/*
Copyright (C) 2025 Arman Jussupgaliyev
*/
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

public class R extends MIDlet implements CommandListener, Runnable {

	private static final byte[] BASE64_ENCODE_ALPHABET = {
		(byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F', (byte)'G',
		(byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
		(byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U',
		(byte)'V', (byte)'W', (byte)'X', (byte)'Y', (byte)'Z',
		(byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f', (byte)'g',
		(byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l', (byte)'m', (byte)'n',
		(byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t', (byte)'u',
		(byte)'v', (byte)'w', (byte)'x', (byte)'y', (byte)'z',
		(byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5',
		(byte)'6', (byte)'7', (byte)'8', (byte)'9', (byte)45, (byte)95
	};

	private final static byte[] BASE64_DECODE_ALPHABET = {
			-9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 0 - 8
			-5, -5, // Whitespace: Tab and Linefeed
			-9, -9, // Decimal 11 - 12
			-5, // Whitespace: Carriage Return
			-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 14 - 26
			-9, -9, -9, -9, -9, // Decimal 27 - 31
			-5, // Whitespace: Space
			-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, // Decimal 33 - 42
			62, // Plus sign at decimal 43
			-9, -9, -9, // Decimal 44 - 46
			63, // Slash at decimal 47
			52, 53, 54, 55, 56, 57, 58, 59, 60, 61, // Numbers zero through nine
			-9, -9, -9, // Decimal 58 - 60
			-1, // Equals sign at decimal 61
			-9, -9, -9, // Decimal 62 - 64
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, // Letters 'A' through 'N'
			14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, // Letters 'O' through 'Z'
			-9, -9, -9, -9, -9, -9, // Decimal 91 - 96
			26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, // Letters 'a' through 'm'
			39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, // Letters 'n' through 'z'
			-9, -9, -9, -9 // Decimal 123 - 126
	};
	
	private static boolean started;
	private Form f;
	private TextField path;
	
	private String midletName, midletVendor;
	int running;
	protected void destroyApp(boolean unconditional) {}

	protected void pauseApp() {}

	protected void startApp() {
		if (started) return;
		started = true;
		
		f = new Form("PortRMS");
		f.addCommand(new Command("Exit", Command.EXIT, 1));
		f.addCommand(new Command("Export", Command.SCREEN, 2));
		f.addCommand(new Command("Import", Command.SCREEN, 3));
		f.setCommandListener(this);
		
		String s = System.getProperty("fileconn.dir.memorycard");
		if (s == null) {
			s = System.getProperty("fileconn.dir.photos");
		}
		
		try {
			// kemulator
			Class.forName("emulator.custom.CustomMethod");
			s = "file:///root/";
		} catch (Exception ignored) {}
				
		if (s == null) {
			s = "file:///E:/";
		}
		path = new TextField("Save path", s, 200, TextField.ANY);
		f.append(path);
		
		f.append("Identified as:\n" + (midletName = getAppProperty("MIDlet-Name"))
				+ "\n" + (midletVendor = getAppProperty("MIDlet-Vendor")) + "\n\n");
		
		Display.getDisplay(this).setCurrent(f);
	}

	public void commandAction(Command c, Displayable d) {
		if (c.getCommandType() == Command.EXIT) {
			notifyDestroyed();
			return;
		}
		if (c.getCommandType() == Command.SCREEN) {
			if (running != 0) return;
			running = c.getPriority();
			new Thread(this).start();
		}
	}
	
	public void run() {
		try {
			String root = path.getString();
			if (!root.endsWith("/")) {
				root += '/';
			}
			root = root + encode(midletVendor + '_' + midletName);
			log("Root directory: " + root);
			
			switch (running) {
			case 2: { // Export
				String[] records = RecordStore.listRecordStores();
				if (records == null) {
					log("No record stores!");
					return;
				}
				log("Stores count: " + records.length);
				mkdir(root);
				for (int i = 0; i < records.length; i++) {
					String r = records[i];
					log("Reading store: " + r);
					mkdir(root + '/' + encode(r));
					RecordStore rs = RecordStore.openRecordStore(r, false);
					try {
						int count = rs.getNextRecordID();
						// write index
						FileConnection fc = (FileConnection) Connector.open(root + '/' + encode(r) + "/idx");
						try {
							if (!fc.exists()) fc.create();
							DataOutputStream out = fc.openDataOutputStream();
							try {
								out.writeInt(count);
								int num = rs.getNumRecords();
								Vector ids = new Vector();
								int c = 0;
								for (int j = 1; j <= count; ++j) {
									try {
										rs.getRecordSize(j);
										ids.addElement(new Integer(j));
										c++;
									} catch (Exception e) {
										continue;
									}
								}
								if (c != num) {
									log("WARNING: getNumRecords() does not match count of found records");
									log(c + " != " + num);
								}
								out.writeInt(ids.size());
								for (int j = 0; j < ids.size(); ++j) {
									out.writeInt(((Integer) ids.elementAt(j)).intValue());
								}
								out.writeLong(rs.getLastModified());
								out.writeInt(rs.getVersion());
								out.writeInt(RecordStore.AUTHMODE_PRIVATE);
								out.writeBoolean(true);
								out.flush();
							} finally {
								out.close();
							}
						} finally {
							try {
								fc.close();
							} catch (Exception ignored) {}
						}
						
						// write records
						for (int j = 1; j <= count; ++j) {
							byte[] b;
							try {
								b = rs.getRecord(j);
							} catch (Exception e) {
								continue;
							}
	
							fc = (FileConnection) Connector.open(root + '/' + encode(r) + '/' + j + ".rms");
							try {
								if (!fc.exists()) fc.create();
								DataOutputStream out = fc.openDataOutputStream();
								try {
									out.write(b);
									out.flush();
								} finally {
									out.close();
								}
								log("Wrote record: " + j + ", size: " + b.length);
							} finally {
								try {
									fc.close();
								} catch (Exception ignored) {}
							}
						}
					} finally {
						try {
							rs.closeRecordStore();
						} catch (Exception ignored) {}
					}
				}
				break;
			}
			case 3: { // import
				Vector stores = new Vector();
				FileConnection fc = (FileConnection) Connector.open(root + '/');
				try {
					Enumeration en = fc.list();
					while (en.hasMoreElements()) {
						String s = (String) en.nextElement();
						if (s.endsWith("/")) {
							try {
								String r = decode(s = s.substring(0, s.length() - 1));
								if (r == null || r.length() == 0) {
									continue;
								}
								stores.addElement(s);
							} catch (Exception ignored) {}
						}
					}
				} finally {
					try {
						fc.close();
					} catch (Exception ignored) {}
				}
				
				int l = stores.size();
				for (int i = 0; i < l; ++i) {
					String dir = (String) stores.elementAt(i);
					String r = decode(dir);
					log("Reading store: " + r);
					try {
						RecordStore.deleteRecordStore(r);
					} catch (Exception ignored) {}
					
					RecordStore rs = RecordStore.openRecordStore(r, true);
					Vector ids = new Vector();
					int count = 0, size = 0;
					try {
						// read index
						fc = (FileConnection) Connector.open(root + '/' + dir + "/idx");
						try {
							DataInputStream in = fc.openDataInputStream();
							try {
								count = in.readInt();
								size = in.readInt();
								for (int j = 0; j < size; ++j) {
									ids.addElement(new Integer(in.readInt()));
								}
								in.readLong(); // lastModified
								in.readInt(); // version
								int mode = in.readInt();
								boolean writable = in.readBoolean();
								try {
									rs.setMode(mode, writable);
								} catch (Exception ignored) {}
							} finally {
								in.close();
							}
						} finally {
							try {
								fc.close();
							} catch (Exception ignored) {}
						}
						
						// read records
						for (int id = 1; id < count; ++id) {
							if (!ids.contains(new Integer(id))) { // deleted record
								log("Reading record: " + id + " (deleted)");
								rs.addRecord(new byte[1], 0, 1);
								rs.deleteRecord(id);
								continue;
							}
							
							fc = (FileConnection) Connector.open(root + '/' + dir + '/' + id + ".rms");
							try {
								int recordSize = (int) fc.fileSize();
								byte[] data = new byte[recordSize];
								log("Reading record: " + id + ", size: " + recordSize);
								DataInputStream in = fc.openDataInputStream();
								try {
									in.readFully(data);
								} finally {
									in.close();
								}
								rs.addRecord(data, 0, recordSize);
							} finally {
								fc.close();
							}
						}
					} finally {
						rs.closeRecordStore();
					}
				}
				break;
			}
			}
			log("Done");
		} catch (Exception e) {
			e.printStackTrace();
			log(e.toString());
		} finally {
			running = 0;
		}
	}

	private void log(String s) {
		System.out.println(s);
		f.append(s + "\n");
	}
	
	private static void mkdir(String s) throws IOException {
		FileConnection fc = (FileConnection) Connector.open(s + '/');
		try {
			if (!fc.isDirectory()) fc.mkdir();
		} finally {
			fc.close();
		}
	}
	
	private static String encode(String s) {
		byte[] src;
		try {
			src = s.getBytes("UTF-8");
		} catch (Exception e) {
			src = s.getBytes();
		}
		int len = src.length;
		byte[] out = new byte[(len * 4 / 3) + ((len % 3) > 0 ? 4 : 0)];
		int i = 0, j = 0, t = len - 2;
		while (i < t) {
			encode3to4(src, i, 3, out, j);
			i += 3; j += 4;
		}
		if (i < len) {
			encode3to4(src, i, len - i, out, j);
			j += 4;
		}
		return new String(out, 0, j);
	}

	private static String decode(String s) {
		byte[] src;
		try {
			src = s.getBytes("UTF-8");
		} catch (Exception e) {
			src = s.getBytes();
		}
		int len34 = src.length * 3 / 4;
		byte[] out = new byte[len34];
		int outlen = 0;

		byte[] b4 = new byte[4];
		int b4Posn = 0;
		int i = 0;
		byte sbiCrop = 0;
		byte sbiDecode = 0;
		for (i = 0; i < src.length; i++) {
			sbiCrop = (byte) (src[i] & 0x7f);
			sbiDecode = BASE64_DECODE_ALPHABET[sbiCrop];

			if (sbiDecode >= -5) {
				if (sbiDecode >= -1) {
					b4[b4Posn++] = sbiCrop;
					if (b4Posn > 3) {
						outlen += decode4to3(b4, 0, out, outlen);
						b4Posn = 0;

						if (sbiCrop == '=')
							break;
					}
				}
			} else {
				return "";
			}
		}
		try {
			return new String(out, 0, outlen, "UTF-8");
		} catch (Exception e) {
			return new String(out, 0, outlen);
		}
	}

	private static byte[] encode3to4(byte[] src, int srcPos, int n, byte[] dst, int dstPos) {
		int inBuff = (n > 0 ? ((src[srcPos] << 24) >>> 8) : 0)
				| (n > 1 ? ((src[srcPos + 1] << 24) >>> 16) : 0)
				| (n > 2 ? ((src[srcPos + 2] << 24) >>> 24) : 0);
		switch (n) {
		case 3:
			dst[dstPos] = BASE64_ENCODE_ALPHABET[(inBuff >>> 18)];
			dst[dstPos + 1] = BASE64_ENCODE_ALPHABET[(inBuff >>> 12) & 0x3f];
			dst[dstPos + 2] = BASE64_ENCODE_ALPHABET[(inBuff >>> 6) & 0x3f];
			dst[dstPos + 3] = BASE64_ENCODE_ALPHABET[(inBuff) & 0x3f];
			return dst;
		case 2:
			dst[dstPos] = BASE64_ENCODE_ALPHABET[(inBuff >>> 18)];
			dst[dstPos + 1] = BASE64_ENCODE_ALPHABET[(inBuff >>> 12) & 0x3f];
			dst[dstPos + 2] = BASE64_ENCODE_ALPHABET[(inBuff >>> 6) & 0x3f];
			dst[dstPos + 3] = '=';
			return dst;
		case 1:
			dst[dstPos] = BASE64_ENCODE_ALPHABET[(inBuff >>> 18)];
			dst[dstPos + 1] = BASE64_ENCODE_ALPHABET[(inBuff >>> 12) & 0x3f];
			dst[dstPos + 2] = '=';
			dst[dstPos + 3] = '=';
			return dst;
		default:
			return dst;
		}
	}

	private static int decode4to3(byte[] source, int srcOffset, byte[] destination, int destOffset) {
		if (source[srcOffset + 2] == '=') {
			int outBuff = ((BASE64_DECODE_ALPHABET[source[srcOffset]] & 0xFF) << 18)
					| ((BASE64_DECODE_ALPHABET[source[srcOffset + 1]] & 0xFF) << 12);
			destination[destOffset] = (byte) (outBuff >>> 16);
			return 1;
		} else if (source[srcOffset + 3] == '=') {
			int outBuff = ((BASE64_DECODE_ALPHABET[source[srcOffset]] & 0xFF) << 18)
					| ((BASE64_DECODE_ALPHABET[source[srcOffset + 1]] & 0xFF) << 12)
					| ((BASE64_DECODE_ALPHABET[source[srcOffset + 2]] & 0xFF) << 6);
			destination[destOffset] = (byte) (outBuff >>> 16);
			destination[destOffset + 1] = (byte) (outBuff >>> 8);
			return 2;
		} else {
			try {
				int outBuff = ((BASE64_DECODE_ALPHABET[source[srcOffset]] & 0xFF) << 18)
						| ((BASE64_DECODE_ALPHABET[source[srcOffset + 1]] & 0xFF) << 12)
						| ((BASE64_DECODE_ALPHABET[source[srcOffset + 2]] & 0xFF) << 6)
						| ((BASE64_DECODE_ALPHABET[source[srcOffset + 3]] & 0xFF));
				destination[destOffset] = (byte) (outBuff >> 16);
				destination[destOffset + 1] = (byte) (outBuff >> 8);
				destination[destOffset + 2] = (byte) (outBuff);
				return 3;
			} catch (Exception e) {
				return -1;
			}
		}
	}

}
