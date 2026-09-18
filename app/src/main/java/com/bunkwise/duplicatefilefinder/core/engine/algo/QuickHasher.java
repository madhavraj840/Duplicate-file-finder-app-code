package com.bunkwise.duplicatefilefinder.core.engine.algo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Quick hash (ARCHITECTURE §5.3b): SHA-256 over the first 16 KB + middle 16 KB
 * + last 16 KB of a file (a ~48 KB read budget). Two files that share a size
 * but differ anywhere in these windows cannot be exact duplicates, eliminating
 * most collisions without a full read. Pure and synchronous.
 */
public final class QuickHasher {

    private static final int WINDOW = 16 * 1024;

    private QuickHasher() {}

    /** Returns a lowercase hex SHA-256, or null if the file could not be read. */
    public static String hash(String path, long size) {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[WINDOW];

            // Include the exact size as a salt so different-size files never collide.
            md.update((byte) (size));
            md.update((byte) (size >>> 8));
            md.update((byte) (size >>> 16));
            md.update((byte) (size >>> 24));

            readWindow(raf, 0, buf, md);
            if (size > WINDOW * 2L) {
                readWindow(raf, Math.max(0, (size / 2) - WINDOW / 2), buf, md);
            }
            if (size > WINDOW) {
                readWindow(raf, Math.max(0, size - WINDOW), buf, md);
            }
            return Hex.encode(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static void readWindow(RandomAccessFile raf, long offset, byte[] buf, MessageDigest md)
            throws IOException {
        raf.seek(offset);
        int total = 0;
        while (total < buf.length) {
            int r = raf.read(buf, total, buf.length - total);
            if (r == -1) break;
            total += r;
        }
        if (total > 0) md.update(buf, 0, total);
    }
}
