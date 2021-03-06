package com.immortalplayer.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import com.immortalplayer.MainActivity;
import com.immortalplayer.proxy.HttpParser.ProxyRequest;
import com.immortalplayer.proxy.HttpParser.ProxyResponse;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

public class HttpGetProxy
{
	public final static String pref = "-ml";
	private final static String LOCAL_IP_ADDRESS = "127.0.0.1";
	private final static int HTTP_PORT = 80, FTP_PORT = 21;
	// config dc++
	private final static String CHARENCODING = "windows-1251";
	private final static String Hub = "dc.filimania.com";
	private final static int dcPort = 411;
	public String dcErrorTxt = "";
	public File file1;
	public boolean seek = false, close;
	private String nick = "MediaLibrary";
	// Other
	private int remotePort = -1, postFix = 0;
	private long urlsize = 0, lastStart, now, maxFile;
	private int localPort, portUser;
	private ServerSocket localServer = null;
	private String mUrl, ftplogin, ftppass, remoteHost, remotePath;
	private String mMediaFilePath, newPath, newPath1, file2, cachefolder, TTH,
			ipUser = "", nickUser = "", xmlDir1 = "", delayDC;
	private File file;
	private Proxy proxy = null;
	private ArrayList<range> ranges = new ArrayList<range>();
	private boolean startProxy, error = false, ftpenable, useDC, errorDC,
			writeFile, firstCheck;
	private Context ctx;
	private FTPClient mFTPClient;
	private InputStream ftp = null;
	private Socket p2pServer = null, sckUser = null;
	private Uri originalURI;
	private XmlPullParser xpp;
	private XmlPullParserFactory factory;
	private TextView textProgress;
	private Handler handler = new Handler();

	/**
	 * Initialize the proxy server, and start the proxy server
	 */
	public HttpGetProxy()
	{
		try
		{
			// Initialize proxy server
			localServer = new ServerSocket(0, 1,
					InetAddress.getByName(LOCAL_IP_ADDRESS));
			localPort = localServer.getLocalPort();// There ServerSocket
			// automatically assigned
			// port
			startProxy();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Get playing link
	 */
	public String getLocalURL()
	{
		File file = new File(Environment.getExternalStorageDirectory()
				.getAbsolutePath() + cachefolder + "/" + file2);
		if (file.exists())
		{
			return file.getAbsolutePath();
		} else
		{
			originalURI = Uri.parse(mUrl);
			remoteHost = originalURI.getHost();
			remotePort = originalURI.getPort();
			remotePath = originalURI.getPath();
			String localUrl = mUrl.replace(remoteHost
							+ (remotePort == -1 ? "" : ":" + remotePort),
					LOCAL_IP_ADDRESS + ":" + localPort);
			if (localUrl.toLowerCase().contains("ftp://"))
			{
				if (localUrl.contains("ftp://"))
				{
					localUrl = localUrl.replaceFirst("ftp://", "http://");
				} else if (localUrl.contains("FTP://"))
				{
					localUrl = localUrl.replaceFirst("FTP://", "http://");
				}
				ftpenable = true;
			} else
			{
				ftpenable = false;
			}
			return localUrl;
		}
	}

	public void stopProxy()
	{
		startProxy = false;
		try
		{
			if (localServer != null)
			{
				localServer.close();
				localServer = null;
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void setPaths(String dirPath, String url, int maxSize, int maxnum,
						 int maxFile1, Context ctxx, boolean deltemp, String loginftp,
						 String pasftp, boolean ftpclose, boolean useDC1, String xmlDir,
						 TextView textProgress1, String delayDC1)
	{
		close = ftpclose;
		cachefolder = dirPath;
		ctx = ctxx;
		ftplogin = loginftp;
		ftppass = pasftp;
		xmlDir1 = xmlDir;
		useDC = useDC1;
		delayDC = delayDC1;
		maxFile = maxFile1 * 1048576;
		textProgress = textProgress1;
		dirPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ dirPath;
		new File(dirPath).mkdirs();
		long maxsize1 = maxSize * 1024L * 1024L;
		mUrl = url;
		file2 = Uri.decode(mUrl.substring(mUrl.lastIndexOf("/") + 1));
		mMediaFilePath = dirPath + "/" + file2;
		firstCheck=false;
		file = new File(mMediaFilePath);
		file1 = new File(dirPath + "/" + file2 + pref);
		Utils.RemoveBufferFile(dirPath, maxnum, maxsize1, deltemp, pref,
				file1.getPath());
		error = false;
	}

	public void startProxy()
	{
		startProxy = true;
		Thread prox = new Thread()
		{
			public void run()
			{
				while (startProxy)
				{
					// --------------------------------------
					// MediaPlayer's request listening, MediaPlayer-> proxy
					// server
					// --------------------------------------
					try
					{
						if (proxy != null)
						{
							proxy.closeSockets();
						}
						Socket s = localServer.accept();
						if (!startProxy) break;
						proxy = new Proxy(s);
					} catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		};
		prox.start();
	}

	public void scan(Uri url)
	{
		Intent scanFileIntent = new Intent(
				Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, url);// scan new files for other
		// players
		ctx.sendBroadcast(scanFileIntent);
	}

	public class range
	{
		long start = 0;
		long end = 0;

		public void setstart(long star)
		{
			start = star;
		}

		public void setend(long en)
		{
			end = en;
		}
	}

	private class Proxy
	{
		/**
		 * Socket receive requests Media Player
		 */
		private Socket sckPlayer = null;
		/**
		 * Socket transceiver Media Server requests
		 */
		private Socket sckServer = null;
		private HttpParser httpParser = null;
		private HttpGetProxyUtils utils;
		private int bytes_read, retr;
		private byte[] file_buffer = new byte[1448];
		private File file2, file3;
		private byte[] local_request = new byte[1024];
		private byte[] p2preq = new byte[1024 * 10];
		private byte[] remote_reply = new byte[1448 * 50];
		private ProxyRequest request = null;
		private ProxyResponse proxyResponse = null;
		private boolean isExists = false;
		private String header = "", str = "";
		private RandomAccessFile os = null, fInputStream = null;
		private long sendByte = 0;
		private FTPFile[] files = null;

		public Proxy(Socket sckPlayer)
		{
			this.sckPlayer = sckPlayer;
			run();
		}

		/**
		 * Shut down the existing links
		 */
		public void closeSockets()
		{
			try
			{// Before starting a new request to close the past Sockets
				if (sckPlayer != null)
				{
					sckPlayer.close();
					sckPlayer = null;
				}
				if (!useDC)
				{
					if (p2pServer != null)
					{
						p2pServer.close();
						p2pServer = null;
					}
				}
				if (sckUser != null)
				{
					sckUser.close();
					sckUser = null;
				}
				if ((ftp != null) && (close))
				{
					try
					{
						mFTPClient.abort();
					} catch (IOException e)
					{
						e.printStackTrace();
						mFTPClient.disconnect();
					}
					if (mFTPClient.getReplyCode() == 426
							|| mFTPClient.getReplyCode() == 226)
					{
						mFTPClient.completePendingCommand();
					} else
					{
						ftp.close();
					}
					ftp = null;
				}
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		private boolean connect()
		{
			boolean rezult = false;
			try
			{
				if ((mFTPClient != null) && (mFTPClient.isConnected()))
				{
					mFTPClient.disconnect();
				}
				mFTPClient = new FTPClient();
				// mFTPClient.addProtocolCommandListener(new PrintCommandListener(
				// new PrintWriter(System.out)));
				mFTPClient.setControlEncoding("UTF-8");
				mFTPClient.setAutodetectUTF8(true);
				mFTPClient.setBufferSize(1048576);
				mFTPClient.connect(remoteHost, (remotePort == -1 ? FTP_PORT : remotePort));
				if (FTPReply.isPositiveCompletion(mFTPClient.getReplyCode()))
				{
					mFTPClient.setFileType(FTP.BINARY_FILE_TYPE); // for support
					mFTPClient.enterLocalPassiveMode();
					rezult = mFTPClient.login(ftplogin, ftppass);
					mFTPClient.setFileType(FTP.BINARY_FILE_TYPE); // for user
				}
				mFTPClient.setSoTimeout(1500);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
			return rezult;
		}

		private boolean connectDC()
		{
			SocketAddress p2padrr = new InetSocketAddress(Hub, dcPort);
			p2pServer = new Socket();
			p2preq = new byte[1024 * 10];
			try
			{
				p2pServer.connect(p2padrr);
				p2pServer.getInputStream().read(p2preq);
				str = new String(p2preq, CHARENCODING);
				str = str.substring(str.indexOf("$Lock ") + 6,
						str.indexOf(" Pk="));
				p2pServer
						.getOutputStream()
						.write(("$Supports UserCommand NoGetINFO NoHello UserIP2 TTHSearch|"
								+ lockToKey(str)
								+ "$ValidateNick "
								+ nick
								+ "|" + "$Version 1,0091|$MyINFO $ALL " + nick + " <ImmortalPlayer V:3,M:P,H:1/0/0,S:15>$ $100 $$600000000000$|")
								.getBytes());
				p2pServer.getOutputStream().flush();
				p2preq = new byte[1024 * 10];
				while ((bytes_read = p2pServer.getInputStream().read(p2preq)) != -1)
				{
					str = new String(p2preq, CHARENCODING);
					if (str.contains("$ValidateDenide"))
					{
						nick = nick + Integer.toString(postFix);
						postFix = postFix + 1;
						if (postFix > 100)
						{
							errorDC = true;
							return false;
						} else
						{
							connectDC();
						}
					}
					if (str.contains("$Search"))
					{
						errorDC = false;
						return true;
					}
				}
			} catch (Exception e)
			{
			}
			errorDC = true;
			return false;
		}

		private void sendToFile()
		{
			if (file1.exists())
			{// Send pre-loaded file to MediaPlayer
				try
				{
					fInputStream = new RandomAccessFile(file1, "r");
					if ((request._rangePosition > 0)
							|| (request._overRange))
					{
						header = "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: "
								+ Long.toString(file1.length()
								- request._rangePosition)
								+ "\r\nContent-Range: bytes "
								+ Long.toString(request._rangePosition)
								+ "-"
								+ Long.toString(file1.length() - 1)
								+ "/"
								+ file1.length()
								+ "\r\nContent-Type: application/octet-stream\r\n\r\n";
						fInputStream.seek(request._rangePosition);
					} else
					{
						header = "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: "
								+ Long.toString(file1.length())
								+ "\r\nContent-Disposition: attachment\r\nContent-Type: application/octet-stream\r\n\r\n";
					}
					sckPlayer.setSoTimeout(1500); // need experiment
					sckPlayer.getOutputStream().write(header.getBytes(), 0,
							header.length());
					while (((bytes_read = fInputStream.read(file_buffer)) != -1)
							&& (!sckPlayer.isClosed())
							&& (mMediaFilePath.equals(newPath)))
					{
						sckPlayer.getOutputStream().write(file_buffer, 0,
								bytes_read);
					}
					sckPlayer.getOutputStream().flush();
				} catch (Exception ex)
				{
					ex.printStackTrace();
				}
				if (fInputStream != null)
				{
					try
					{
						fInputStream.close();
					} catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		private String lockToKey(String lockstr)
				throws UnsupportedEncodingException
		{
			byte[] lock = lockstr.getBytes(CHARENCODING);
			byte[] key = new byte[lock.length];
			for (int i = 1; i < lock.length; i++)
			{
				key[i] = (byte) ((lock[i] ^ lock[i - 1]) & 0xFF);
			}
			key[0] = (byte) ((((lock[0] ^ lock[lock.length - 1]) ^ lock[lock.length - 2]) ^ 5) & 0xFF);
			for (int i = 0; i < key.length; i++)
			{
				key[i] = (byte) ((((key[i] << 4) & 0xF0) | ((key[i] >> 4) & 0x0F)) & 0xFF);
			}
			return dcnEncode(new String(key, CHARENCODING));
		}

		private String dcnEncode(String lock)
		{
			for (int i : new int[]
					{0, 5, 36, 96, 124, 126})
			{
				String paddedDecimal = String.format("%03d", i);
				String paddedHex = String.format("%02x", i);
				lock = lock.replaceAll("\\x" + paddedHex, "/%DCN"
						+ paddedDecimal + "%/");
			}
			return "$Key " + lock + "|";
		}

		public void run()
		{
			if (!mMediaFilePath.equals(newPath))
			{
				error = false;
				errorDC = false;
				firstCheck = false;
				nickUser = "";
				ipUser = "";
				TTH = "";
				portUser = 0;
				urlsize = 0;
				ranges.clear();
				ranges.trimToSize();
				newPath = mMediaFilePath;
				newPath1 = file1.getAbsolutePath();
			}
			// Read player request
			try
			{
				httpParser = new HttpParser(remoteHost, remotePort,
						LOCAL_IP_ADDRESS, localPort);
				while (((bytes_read = sckPlayer.getInputStream().read(
						local_request)) != -1)
						&& (mMediaFilePath.equals(newPath)))
				{
					byte[] buffer = httpParser.getRequestBody(local_request,
							bytes_read);
					if (buffer != null)
					{
						request = httpParser.getProxyRequest(buffer, urlsize);
						break;
					}
				}
				isExists = file.exists();
				// Read from file///////////////////////////////
				if (((isExists) || ((file1.exists()) && (error)))
						&& (mMediaFilePath.equals(newPath)))
				{
					try
					{
						if ((file1.exists()) && (error)
								&& (!isExists))
						{
							fInputStream = new RandomAccessFile(file1, "r");
							if ((request._rangePosition > 0)
									|| (request._overRange))
							{
								header = "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: "
										+ Long.toString(file1.length()
										- request._rangePosition)
										+ "\r\nContent-Range: bytes "
										+ Long.toString(request._rangePosition)
										+ "-"
										+ Long.toString(file1.length() - 1)
										+ "/"
										+ file1.length()
										+ "\r\nContent-Type: application/octet-stream\r\n\r\n";
								fInputStream.seek(request._rangePosition);
							} else
							{
								header = "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: "
										+ Long.toString(file1.length())
										+ "\r\nContent-Disposition: attachment\r\nContent-Type: application/octet-stream\r\n\r\n";
							}
						} else
						{
							fInputStream = new RandomAccessFile(file, "r");
							if ((request._rangePosition > 0)
									|| (request._overRange))
							{
								header = "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: "
										+ Long.toString(file.length()
										- request._rangePosition)
										+ "\r\nContent-Range: bytes "
										+ Long.toString(request._rangePosition)
										+ "-"
										+ Long.toString(file.length() - 1)
										+ "/"
										+ file.length()
										+ "\r\nContent-Type: application/octet-stream\r\n\r\n";
								fInputStream.seek(request._rangePosition);
							} else
							{
								header = "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: "
										+ Long.toString(file.length())
										+ "\r\nContent-Disposition: attachment\r\nContent-Type: application/octet-stream\r\n\r\n";
							}
						}
						error = false;
						sckPlayer.getOutputStream().write(header.getBytes(), 0,
								header.length());
						while (((bytes_read = fInputStream.read(file_buffer)) != -1)
								&& (!sckPlayer.isClosed())
								&& (mMediaFilePath.equals(newPath)))
						{
							sckPlayer.getOutputStream().write(file_buffer, 0,
									bytes_read);
						}
						sckPlayer.getOutputStream().flush();
					} catch (Exception ex)
					{
						ex.printStackTrace();
					} finally
					{
						if (fInputStream != null) fInputStream.close();
					}
					return;
				}
			} catch (Exception e1)
			{
				e1.printStackTrace();
			}
			// Read from DC++ peering network!
			if ((useDC) && (!errorDC))
			{
				try
				{
					if (p2pServer == null)
					{
						if (!connectDC())
						{
							errorDC = true;
							dcErrorTxt = "No connect";
							throw new NullPointerException("No connect");
						}
					}
					if (TTH.equals(""))
					{// Get TTH from XML File. Utility for create xml
						// file:https://github.com/master255/SimplyServer
						File xmlFile = new File(xmlDir1, originalURI.getHost()
								+ ".xml");
						if ((xmlFile != null) && (xmlFile.exists()))
						{
							int pathInd = 0, depth = 0;
							List<String> pathSl = originalURI.getPathSegments();
							boolean exitC = false;
							if (factory == null)
							{
								factory = XmlPullParserFactory.newInstance();
								xpp = factory.newPullParser();
							}
							FileInputStream xmlFile1 = new FileInputStream(xmlFile);
							xpp.setInput(xmlFile1, null);
							int eventType = xpp.getEventType();
							while ((eventType != XmlPullParser.END_DOCUMENT)
									&& (!exitC)
									&& (mMediaFilePath.equals(newPath)))
							{
								if ((depth > 0) && (xpp.getDepth() > depth + 1))
								{
									eventType = xpp.next();
									continue;
								}
								if (eventType == XmlPullParser.START_TAG)
								{
									if ((xpp.getName().equals("Directory"))
											&& (xpp.getAttributeCount() > 0)
											&& (xpp.getAttributeValue(null,
											"Name").equals(pathSl
											.get(pathInd))))
									{
										depth = xpp.getDepth();
										pathInd = pathInd + 1;
									} else if ((xpp.getName().equals("File"))
											&& (pathInd == pathSl.size() - 1)
											&& (xpp.getAttributeCount() > 0)
											&& (xpp.getAttributeValue(null,
											"Name").equals(pathSl
											.get(pathInd))))
									{
										TTH = xpp
												.getAttributeValue(null, "TTH");
										exitC = true;
									}
								} else if ((eventType == XmlPullParser.END_TAG)
										&& (xpp.getName().equals("Directory"))
										&& (xpp.getDepth() == depth))
								{
									errorDC = true;
									exitC = true;
								}
								eventType = xpp.next();
							}
							xmlFile1.close();
						}
					}
					if (!errorDC)
					{
						if (nickUser.length() < 1)
						{// then get nick user
							now = System.currentTimeMillis();
							if (lastStart > 0)
							{
								handler.post(new Runnable()
								{
									public void run()
									{
										textProgress
												.setVisibility(View.VISIBLE);
									}
								});
								int i = 0;
								while (((now - lastStart) < 30000)
										&& (mMediaFilePath.equals(newPath)))
								{
									now = System.currentTimeMillis();
									if (i > 100)
									{
										handler.post(new Runnable()
										{
											public void run()
											{
												textProgress.setText(delayDC
														+ Float.toString((301 - (float) Math
														.round((now - lastStart) / 100)) / 10));
											}
										});
										i = 0;
									}
									i++;
								}
								handler.post(new Runnable()
								{
									public void run()
									{
										textProgress.setVisibility(View.GONE);
									}
								});
							}
							if (mMediaFilePath.equals(newPath))
							{
								lastStart = now;
								try
								{
									p2pServer.getInputStream().skip(
											p2pServer.getInputStream()
													.available());
									p2pServer
											.getOutputStream()
											.write(("$Search Hub:" + nick
													+ " F?T?0?9?TTH:" + TTH + "|")
													.getBytes());
									p2pServer.getOutputStream().flush();
								} catch (Exception e)
								{
									connectDC();
									p2pServer.getInputStream().skip(
											p2pServer.getInputStream()
													.available());
									p2pServer
											.getOutputStream()
											.write(("$Search Hub:" + nick
													+ " F?T?0?9?TTH:" + TTH + "|")
													.getBytes());
									p2pServer.getOutputStream().flush();
									e.printStackTrace();
								}
							}
							p2preq = new byte[1024 * 10];
							retr = 20;// number of answers to searches
							while (((bytes_read = p2pServer.getInputStream()
									.read(p2preq)) != -1)
									&& (mMediaFilePath.equals(newPath)))
							{
								str = new String(p2preq, CHARENCODING);
								str = str.substring(0, bytes_read);
								// Log.d("999", Integer.toString(bytes_read));
								// Log.d("888", str);
								if (str.contains("$SR"))
								{
									nickUser = str.substring(
											str.indexOf("$SR") + 4,
											str.indexOf(" ",
													str.indexOf("$SR") + 4));
									urlsize = Integer
											.parseInt(str.substring(
													str.indexOf("",
															str.indexOf("$SR")) + 1,
													str.indexOf(
															" ",
															str.indexOf(
																	"",
																	str.indexOf("$SR")) + 1)));
									break;
								}
								retr = retr - 1;
								if (retr == 0)
								{
									errorDC = true;
									dcErrorTxt = "No search result";
									throw new NullPointerException(
											"No search result");
								}
								p2preq = new byte[1024 * 10];
							}
						}
						try
						{
							p2pServer.getOutputStream()
									.write(("$RevConnectToMe " + nick + " "
											+ nickUser + "|").getBytes());
							p2pServer.getOutputStream().flush();
						} catch (Exception e)
						{
							connectDC();
							p2pServer.getInputStream().skip(
									p2pServer.getInputStream().available());
							p2pServer.getOutputStream()
									.write(("$RevConnectToMe " + nick + " "
											+ nickUser + "|").getBytes());
							p2pServer.getOutputStream().flush();
						}
						seek = false;
						retr = 20;
						do
						{
							if ((ipUser.equals("")) || (portUser == 0))
							{
								p2preq = new byte[1024 * 10];
								retr = 30;// number of answers (user) to searches
								while (((bytes_read = p2pServer
										.getInputStream().read(p2preq)) != -1)
										&& (mMediaFilePath.equals(newPath)))
								{
									str = new String(p2preq, CHARENCODING);
									// Log.d("999", str.substring(0, bytes_read));
									str = str.substring(0, bytes_read);
									if (str.contains("$ConnectToMe " + nick))
									{
										ipUser = str
												.substring(
														str.indexOf("$ConnectToMe "
																+ nick)
																+ ("$ConnectToMe " + nick)
																.length()
																+ 1,
														str.indexOf(
																":",
																str.indexOf("$ConnectToMe "
																		+ nick)
																		+ ("$ConnectToMe " + nick)
																		.length()
																		+ 1));
										portUser = Integer
												.parseInt(str.substring(
														str.indexOf(
																":",
																str.indexOf("$ConnectToMe "
																		+ nick)
																		+ ("$ConnectToMe " + nick)
																		.length()
																		+ 1) + 1,
														str.indexOf(
																"|",
																str.indexOf(
																		":",
																		str.indexOf("$ConnectToMe "
																				+ nick)
																				+ ("$ConnectToMe " + nick)
																				.length()
																				+ 1))));
										break;
									}
									p2preq = new byte[1024 * 10];
									retr = retr - 1;
									if (retr == 0)
									{
										errorDC = true;
										dcErrorTxt = "No answer from User";
										throw new NullPointerException(
												"No answer from User");
									}
								}
							}
							sckUser = new Socket();
							sckUser.setSoTimeout(1500);
							InetSocketAddress p2padrr1 = new InetSocketAddress(
									ipUser, portUser);
							sckUser.connect(p2padrr1);
							sckUser.getOutputStream()
									.write(("$MyNick "
											+ nick
											+ "|$Lock EXTENDEDPROTOCOLABCABCABCABCABCABC Pk=DCPLUSPLUS0.785ABCABCRef=dchub://"
											+ Hub
											+ "|$Supports MiniSlots ADCGet TTHL TTHF|$Direction Download 7777|$Key СА° A С±±АА0Р0 0 0 0 0 0|"
											+ "$ADCGET file TTH/" + TTH + " "
											+ request._rangePosition + " -1|")
											.getBytes());
							sckUser.getOutputStream().flush();
							p2preq = new byte[1024 * 10];
							bytes_read = sckUser.getInputStream().read(p2preq);
							retr = retr - 1;
							if (bytes_read == -1)
							{
								sckUser.close();
								sckUser = null;
								Long cur = System.currentTimeMillis();
								while ((System.currentTimeMillis() - cur) < 200) ;
							}
						} while ((retr > 0) && (bytes_read == -1)
								&& (!seek)
								&& (mMediaFilePath.equals(newPath)));
						// header
						// Log.d("999", Integer.toString(bytes_read));
						if ((request._rangePosition > 0)
								|| (request._overRange))
						{
							header = "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: "
									+ Long.toString(urlsize
									- request._rangePosition)
									+ "\r\nContent-Range: bytes "
									+ Long.toString(request._rangePosition)
									+ "-"
									+ Long.toString(urlsize - 1)
									+ "/"
									+ urlsize
									+ "\r\nContent-Type: application/octet-stream\r\n\r\n";
						} else
						{
							header = "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: "
									+ Long.toString(urlsize)
									+ "\r\nContent-Disposition: attachment\r\nContent-Type: application/octet-stream\r\n\r\n";
						}
						sckPlayer.getOutputStream().write(header.getBytes(), 0,
								header.length());
						str = new String(p2preq, CHARENCODING);
						// removes $ADCSND
						if (str.contains("$ADCSND"))
						{
							str = str
									.substring(
											str.indexOf("$MyNick"),
											str.indexOf("|",
													str.indexOf("$ADCSND")) + 1);
							bytes_read = bytes_read - str.length();
						} else
						{
							str = "";
						}
						writeFile = ((urlsize < maxFile) && (urlsize > 0));
						try
						{
							if (writeFile)
							{
								os = new RandomAccessFile(file1, "rwd");
								os.seek(request._rangePosition);
								os.write(p2preq, str.length(), bytes_read);
							}
							sendByte += bytes_read;
						} catch (FileNotFoundException e2)
						{
							e2.printStackTrace();
						}
						sckPlayer.getOutputStream().write(p2preq, str.length(),
								bytes_read);
						p2preq = new byte[1448 * 50];
						seek = false;
						boolean sendFile = false;
						while (true)
						{
							if ((!seek) && (mMediaFilePath.equals(newPath)))
							{
								if ((MainActivity.loads.size() > 0)
										&& (MainActivity.loads.contains(file1
										.getAbsolutePath()))
										&& (MainActivity.loadsByte
										.get(MainActivity.loads.indexOf(file1
												.getAbsolutePath())) > (request._rangePosition
										+ sendByte + 1448)))
								{
									if (os == null)
									{
										try
										{
											os = new RandomAccessFile(file1,
													"r");
										} catch (FileNotFoundException e2)
										{
										}
									}
									if ((!sendFile)
											&& ((sendByte + request._rangePosition) > 0))
									{
										os.seek(request._rangePosition
												+ sendByte);
									}
									bytes_read = os.read(file_buffer);
									sendByte += bytes_read;
									sendFile = true;
									sckPlayer.getOutputStream().write(
											file_buffer, 0, bytes_read);
								} else
								{
									if ((sendFile) && (sendByte > 0))
									{
										sendFile = false;
										sckUser.close();
										sckUser = new Socket();
										sckUser.setSoTimeout(1500);
										InetSocketAddress p2padrr1 = new InetSocketAddress(
												ipUser, portUser);
										sckUser.connect(p2padrr1);
										sckUser.getOutputStream()
												.write(("$MyNick "
														+ nick
														+ "|$Lock EXTENDEDPROTOCOLABCABCABCABCABCABC Pk=DCPLUSPLUS0.785ABCABCRef=dchub://"
														+ Hub
														+ "|$Supports MiniSlots ADCGet TTHL TTHF|$Direction Download 7777|$Key ����A ѱ���0�0 0 0 0 0 0|"
														+ "$ADCGET file TTH/"
														+ TTH
														+ " "
														+ (request._rangePosition + sendByte) + " -1|")
														.getBytes());
										sckUser.getOutputStream().flush();
										p2preq = new byte[1024 * 10];
										bytes_read = sckUser.getInputStream()
												.read(p2preq);
										str = new String(p2preq, CHARENCODING);
										// removes $ADCSND
										if (str.contains("$ADCSND"))
										{
											str = str
													.substring(
															str.indexOf("$MyNick"),
															str.indexOf(
																	"|",
																	str.indexOf("$ADCSND")) + 1);
											bytes_read = bytes_read
													- str.length();
										} else
										{
											str = "";
										}
										sckPlayer.getOutputStream().write(
												p2preq, str.length(),
												bytes_read);
										if (writeFile)
										{
											os.seek(request._rangePosition
													+ sendByte);
											os.write(p2preq, str.length(),
													bytes_read);
										}
										sendByte += bytes_read;
										p2preq = new byte[1448 * 50];
									}
									if ((bytes_read = sckUser.getInputStream()
											.read(p2preq)) != -1)
									{
										if (writeFile)
										{
											os.write(p2preq, 0, bytes_read);
										}
										sendByte += bytes_read;
										sckPlayer.getOutputStream().write(p2preq, 0, bytes_read);
									} else
									{
										break;
									}
								}
							} else
							{
								break;
							}
						}
						sckPlayer.getOutputStream().flush();
					}
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			// Read from FTP internet///////////////////////
			if ((ftpenable)
					&& (((useDC) && (errorDC)) || (!useDC)))
			{
				if ((mFTPClient == null) || (!mFTPClient.isConnected()))
				{
					if (!connect())
					{
						error = true;
						sendToFile();
						return;
					}
				}
				try
				{
					if (urlsize == 0)
					{
						files = mFTPClient.listFiles(remotePath);
						if (files.length == 1 && files[0].isFile())
						{
							urlsize = files[0].getSize();
						}
						if (urlsize == 0)
						{
							mFTPClient.sendCommand("SIZE " + remotePath);
							if (mFTPClient.getReplyString().startsWith("213 "))
							{
								urlsize = Long.parseLong(mFTPClient
										.getReplyString()
										.replaceFirst("213 ", "")
										.replaceAll("\r\n", ""));
							}
						}
					}
					writeFile = ((urlsize < maxFile) && (urlsize > 0));
					if (writeFile)
					{
						try
						{
							os = new RandomAccessFile(file1, "rwd");
						} catch (FileNotFoundException e2)
						{
							writeFile = false;
							e2.printStackTrace();
						}
					}
					if (request._rangePosition > 0)
					{
						mFTPClient.setRestartOffset(request._rangePosition);
						if (writeFile)
						{
							os.seek(request._rangePosition);
						}
					}
					ftp = mFTPClient.retrieveFileStream(remotePath);
				} catch (Exception e)
				{
					e.printStackTrace();
					ftp = null;
				}
				try
				{
					if ((ftp == null) || (urlsize == 0))
					{
						if (!connect())
						{
							error = true;
							sendToFile();
							return;
						} else
						{
							if (urlsize == 0)
							{
								files = mFTPClient.listFiles(remotePath);
								if (files.length == 1 && files[0].isFile())
								{
									urlsize = files[0].getSize();
								}
								if (urlsize == 0)
								{
									mFTPClient
											.sendCommand("SIZE " + remotePath);
									if (mFTPClient.getReplyString().startsWith(
											"213 "))
									{
										urlsize = Long.parseLong(mFTPClient
												.getReplyString()
												.replaceFirst("213 ", "")
												.replaceAll("\r\n", ""));
									}
								}
							}
							writeFile = ((urlsize < maxFile) && (urlsize > 0));
							if (writeFile)
							{
								try
								{
									os = new RandomAccessFile(file1, "rwd");
								} catch (FileNotFoundException e2)
								{
									writeFile = false;
									e2.printStackTrace();
								}
							}
							if (request._rangePosition > 0)
							{
								mFTPClient
										.setRestartOffset(request._rangePosition);
								if (writeFile)
								{
									os.seek(request._rangePosition);
								}
							}
							ftp = mFTPClient.retrieveFileStream(remotePath);
							if ((ftp == null) || (urlsize == 0))
							{
								error = true;
								sendToFile();
								return;
							}
						}
					}
				} catch (Exception e)
				{
					e.printStackTrace();
					error = true;
					sendToFile();
					return;
				}
				error = false;
				try
				{
					if ((request._rangePosition > 0)
							|| (request._overRange))
					{
						header = "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: "
								+ Long.toString(urlsize
								- request._rangePosition)
								+ "\r\nContent-Range: bytes "
								+ Long.toString(request._rangePosition)
								+ "-"
								+ Long.toString(urlsize - 1)
								+ "/"
								+ urlsize
								+ "\r\nContent-Type: application/octet-stream\r\n\r\n";
					} else
					{
						header = "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: "
								+ Long.toString(urlsize)
								+ "\r\nContent-Disposition: attachment\r\nContent-Type: application/octet-stream\r\n\r\n";
					}
					sckPlayer.getOutputStream().write(header.getBytes(), 0,
							header.length());
					seek = false;
					boolean sendFile = false;
					while (true)
					{
						if ((!seek) && (mMediaFilePath.equals(newPath)))
						{
							if ((MainActivity.loads.size() > 0)
									&& (MainActivity.loads.contains(file1
									.getAbsolutePath()))
									&& (MainActivity.loadsByte
									.get(MainActivity.loads
											.indexOf(file1
													.getAbsolutePath())) > (request._rangePosition
									+ sendByte + 1448)))
							{
								if (os == null)
								{
									try
									{
										os = new RandomAccessFile(file1, "r");
									} catch (FileNotFoundException e2)
									{
									}
								}
								if ((!sendFile)
										&& ((sendByte + request._rangePosition) > 0))
								{
									os.seek(request._rangePosition + sendByte);
								}
								bytes_read = os.read(file_buffer);
								sendByte += bytes_read;
								sendFile = true;
								sckPlayer.getOutputStream().write(file_buffer,
										0, bytes_read);
							} else
							{
								if ((sendFile) && (sendByte > 0))
								{
									sendFile = false;
									mFTPClient
											.setRestartOffset(request._rangePosition
													+ sendByte);
									if (writeFile)
									{
										os.seek(request._rangePosition
												+ sendByte);
									}
									ftp = mFTPClient
											.retrieveFileStream(remotePath);
								}
								if ((bytes_read = ftp.read(remote_reply)) != -1)
								{
									if (writeFile)
									{
										os.write(remote_reply, 0, bytes_read);
									}
									sendByte += bytes_read;
									sckPlayer.getOutputStream().write(
											remote_reply, 0, bytes_read);
								} else
								{
									break;
								}
							}
						} else
						{
							break;
						}
					}
					sckPlayer.getOutputStream().flush();
				} catch (IOException e)
				{
					// e.printStackTrace();
				}
			}
			// Read from HTTP Internet///////////////////////
			else if (((useDC) && (errorDC))
					|| (!useDC))
			{
				if ((request != null) && (!isExists)
						&& (mMediaFilePath.equals(newPath)))
				{
					try
					{
						SocketAddress serverAddress = new InetSocketAddress(remoteHost,
								(remotePort == -1 ? HTTP_PORT : remotePort));
						utils = new HttpGetProxyUtils(sckPlayer, serverAddress);
						sckServer = utils.sentToServer(request._body);
						// Send MediaPlayer's request to server
						sckPlayer.setSoTimeout(1500);
						sckServer.setSoTimeout(1500); // without this flac not work.
					} catch (Exception e)
					{
						e.printStackTrace();
						error = true;
						sendToFile();
						return;
					}
					error = false;
				} else
				{
					// MediaPlayer's request is invalid
					closeSockets();
					return;
				}
				if (sckServer != null)
				{
					try
					{
						bytes_read = sckServer.getInputStream().read(
								remote_reply);
						proxyResponse = httpParser.getProxyResponse(
								remote_reply, bytes_read);
						if (proxyResponse._duration > 0)
						{
							urlsize = proxyResponse._duration;
						}
						writeFile = ((urlsize < maxFile) && (urlsize > 0));
						if (writeFile)
						{
							try
							{
								os = new RandomAccessFile(file1, "rwd");
							} catch (FileNotFoundException e2)
							{
							}
						}
						if (proxyResponse._other != null)
						{
							if (!firstCheck)//check for html
							{
								firstCheck = true;
								String str = new String(proxyResponse._other);
								if (str.toLowerCase().contains("<html>"))
								{
									throw new Exception("");
								}
							}
						}
						// send http header to mediaplayer
						sckPlayer.getOutputStream().write(proxyResponse._body);
						// Send the binary data
						if (proxyResponse._other != null)
						{
							sckPlayer.getOutputStream().write(proxyResponse._other);
							if (writeFile)
							{
								os.seek(proxyResponse._currentPosition);
								os.write(proxyResponse._other, 0,
										proxyResponse._other.length);
							}
							sendByte += proxyResponse._other.length;
						}
						seek = false;
						boolean sendFile = false;
						while (true)
						{
							if ((!seek) && (mMediaFilePath.equals(newPath)))
							{
								if ((MainActivity.loads.size() > 0)
										&& (MainActivity.loads.contains(file1
										.getAbsolutePath()))
										&& (MainActivity.loadsByte
										.get(MainActivity.loads.indexOf(file1
												.getAbsolutePath())) > (request._rangePosition
										+ sendByte + 1448)))
								{//Read from downloads. Very cool feature :-)
									if (os == null)
									{
										try
										{
											os = new RandomAccessFile(file1,
													"r");
										} catch (FileNotFoundException e2)
										{}
									}
									if ((!sendFile)
											&& ((sendByte + request._rangePosition) > 0))
									{
										os.seek(request._rangePosition
												+ sendByte);
									}
									bytes_read = os.read(file_buffer);
									sendByte += bytes_read;
									sendFile = true;
									sckPlayer.getOutputStream().write(
											file_buffer, 0, bytes_read);
								} else
								{
									if ((sendFile) && (sendByte > 0))
									{
										if (request._body  //Range adjustment after sending file
												.contains("Range: bytes="))
										{
											request._body = request._body
													.substring(
															0,
															request._body
																	.indexOf("Range: bytes="))
													+ "Range: bytes="
													+ (request._rangePosition + sendByte)
													+ "-\r\n\r\n";
										} else
										{ //add Range
											request._body = request._body
													.substring(
															0,
															request._body
																	.lastIndexOf("\r\n"))
													+ "Range: bytes="
													+ (request._rangePosition + sendByte)
													+ "-\r\n\r\n";
										}
										sckServer.close(); //reconnect. It is transparent for player
										sckServer = utils
												.sentToServer(request._body);
										sckServer.setSoTimeout(1500);
										bytes_read = sckServer.getInputStream()
												.read(remote_reply);
										proxyResponse = httpParser
												.getProxyResponse(remote_reply,
														bytes_read);
										if (writeFile)
										{
											os.seek(proxyResponse._currentPosition);
										}
										sendFile = false;
										if (proxyResponse._other != null)
										{
											sckPlayer.getOutputStream().write(
													proxyResponse._other);
											if (writeFile)
											{
												os.write(
														proxyResponse._other,
														0,
														proxyResponse._other.length);
											}
											sendByte += proxyResponse._other.length;
										}
									}
									if ((bytes_read = sckServer
											.getInputStream()
											.read(remote_reply)) != -1)
									{
										if (writeFile)
										{
											os.write(remote_reply, 0,
													bytes_read);
										}
										sendByte += bytes_read;
										sckPlayer.getOutputStream().write(
												remote_reply, 0, bytes_read);
									} else
									{
										break;
									}
								}
							} else
							{
								break;
							}
						}
						sckPlayer.getOutputStream().flush();
					} catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
			closeSockets();
			try
			{//calculate and save file
				if (os != null)
				{
					os.close();
					file2 = new File(newPath);
					file3 = new File(newPath1);
					if ((!file2.exists()) && (request != null)
							&& (sendByte > 0))
					{
						range r = new range();
						r.setstart(request._rangePosition);
						r.setend(request._rangePosition + sendByte);
						ranges.add(r);
						if (urlsize > 0)
						{
							long h = 0;
							for (int i = 0; i < ranges.size(); i++)
							{
								for (int i1 = 0; i1 < ranges.size(); i1++)
								{
									if (ranges.get(i1).start <= h)
									{
										if (ranges.get(i1).end > h)
											h = ranges.get(i1).end;
									}
								}
							}
							if (((useDC) && (urlsize == h))
									|| ((ftpenable) && (urlsize == h))
									|| ((!ftpenable) && (urlsize == h - 1)))
							{

								file3.renameTo(file2);
								scan(Uri.fromFile(file2));
							}
						}
					}
				}
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}