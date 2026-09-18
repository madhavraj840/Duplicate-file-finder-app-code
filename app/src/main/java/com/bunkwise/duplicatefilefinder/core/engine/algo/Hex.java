package com.bunkwise.duplicatefilefinder.core.engine.algo;

/** Lowercase hex encoding for digest bytes. Pure utility. */
public final class Hex {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hex() {}

    public static String encode(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
