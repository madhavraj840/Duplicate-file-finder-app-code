package com.bunkwise.duplicatefilefinder.core.engine.algo;

/** Hamming distance between two 64-bit fingerprints (ARCHITECTURE §5.5). */
public final class Hamming {
    private Hamming() {}

    /** Number of differing bits — a single XOR + popcount, microseconds each. */
    public static int distance(long a, long b) {
        return Long.bitCount(a ^ b);
    }
}
