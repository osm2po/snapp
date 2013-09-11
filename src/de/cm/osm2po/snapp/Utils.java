package de.cm.osm2po.snapp;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class Utils {
	private Utils() {/*static*/}

	public static void writeString(String str, DataOutputStream dos) throws Exception {
		if (null == str) {
			dos.writeInt(0);
			return;
		}
		byte[] bytes = str.getBytes();
		dos.writeInt(bytes.length);
		dos.write(bytes);
	}
	
	public static String readString(DataInputStream dis) throws Exception {
		String s = null;
		int n = dis.readInt();
		if (n > 0) {
			byte[] bytes = new byte[n];
			dis.read(bytes);
			s = new String(bytes);
		}
		return s;
	}

	public static void writeLongs(long[] longs, DataOutputStream dos) throws Exception {
		int n = longs == null ? 0 : longs.length;
		dos.writeInt(n);
		for (int i = 0; i < n; i++) {
			dos.writeLong(longs[i]);
		}
	}
	
	public static long[] readLongs(DataInputStream dis) throws Exception {
		long[] longs = null;
		int n = dis.readInt();
		if (n > 0) {
			longs = new long[n];
			for (int i = 0; i < n; i++) {
				longs[i] = dis.readLong();
			}
		}
		return longs;
	}

}
