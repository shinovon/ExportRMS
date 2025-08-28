/*
Copyright (C) 2025 Arman Jussupgaliyev
*/
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
	
	private static boolean started;
	private Form f;
	private TextField path;
	
	private String midletName, midletVendor;

	protected void destroyApp(boolean unconditional) {}

	protected void pauseApp() {}

	protected void startApp() {
		if (started) return;
		started = true;
		
		f = new Form("ExportRMS");
		f.addCommand(new Command("Exit", Command.EXIT, 1));
		f.addCommand(new Command("Export", Command.OK, 1));
		f.setCommandListener(this);
		
		path = new TextField("Save path", "file:///E:/", 200, TextField.ANY);
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
		if (c.getCommandType() == Command.OK) {
			new Thread(this).start();
		}
	}
	
	public void run() {
		try {
			String[] records = RecordStore.listRecordStores();
			if (records == null) {
				log("No record stores!");
				return;
			}
			log("Stores count: " + records.length);
			String root = path.getString();
			if (!root.endsWith("/")) {
				root += '/';
			}
			mkdir(root = root + encode(midletVendor + '_' + midletName));
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
			log("Done");
		} catch (Exception e) {
			e.printStackTrace();
			log(e.toString());
		}
	}

	private void log(String s) {
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
		} catch (UnsupportedEncodingException e) {
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

}
