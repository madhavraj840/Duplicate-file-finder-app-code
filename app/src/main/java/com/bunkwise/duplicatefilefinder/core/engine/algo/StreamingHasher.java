package com.bunkwise.duplicatefilefinder.core.engine.algo;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Full-file SHA-256 streamed in a pooled 1 MB buffer (ARCHITECTURE §5.3c) — no
 * per-file byte[] allocation of the whole file, so memory stays flat regardless
 * of file size. A cancellation checkpoint is exposed via [Canceller].
 */
public final class StreamingHasher {

    private static final int BUFFER = 1024 * 1024; // 1 MB

    /** Cooperative cancellation hook — return true to abort mid-file. */
    public interface Canceller {
        boolean isCancelled();
    }

    private final byte[] buffer = new byte[BUFFER];

    /** Streams the whole file. Returns hex SHA-256, or null on error/cancel. */
    public String hash(String path, Canceller canceller) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(path), BUFFER)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int r;
            long sinceCheck = 0;
            while ((r = in.read(buffer)) != -1) {
                md.update(buffer, 0, r);
                sinceCheck += r;
                // Checkpoint roughly every 2 MB read (ARCHITECTURE §6.3).
                if (sinceCheck >= 2L * BUFFER) {
                    sinceCheck = 0;
                    if (canceller != null && canceller.isCancelled()) return null;
                }
            }
            return Hex.encode(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
