package com.bunkwise.duplicatefilefinder.core.engine.algo;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Optional paranoia pass (ARCHITECTURE §5.3d): direct byte-by-byte comparison of
 * two files, used to make the "100% identical" claim literally true for large
 * Exact-mode groups. Pure and synchronous.
 */
public final class ByteComparator {

    private static final int BUFFER = 64 * 1024;

    private ByteComparator() {}

    /** True iff both files exist, are the same length, and are byte-identical. */
    public static boolean equalContents(String pathA, String pathB) {
        try (InputStream a = new BufferedInputStream(new FileInputStream(pathA), BUFFER);
             InputStream b = new BufferedInputStream(new FileInputStream(pathB), BUFFER)) {
            byte[] ba = new byte[BUFFER];
            byte[] bb = new byte[BUFFER];
            while (true) {
                int ra = fill(a, ba);
                int rb = fill(b, bb);
                if (ra != rb) return false;
                if (ra == -1) return true;
                for (int i = 0; i < ra; i++) {
                    if (ba[i] != bb[i]) return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static int fill(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = in.read(buf, total, buf.length - total);
            if (r == -1) return total == 0 ? -1 : total;
            total += r;
        }
        return total;
    }
}
