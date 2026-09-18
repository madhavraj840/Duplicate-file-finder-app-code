package com.bunkwise.duplicatefilefinder.core.engine.algo;

/**
 * 64-bit difference hash (ARCHITECTURE §5.4). Operates on a pre-computed 9x8
 * luminance grid (row-major, values 0-255) produced by decoding an image to a
 * tiny bitmap once (decode-once rule). For each of 8 rows, compares 8 adjacent
 * column pairs -> 64 bits. Rotation/scale tolerant enough for "similar" images.
 */
public final class DHasher {

    public static final int WIDTH = 9;  // one extra column for adjacent diffs
    public static final int HEIGHT = 8;

    private DHasher() {}

    /**
     * @param gray row-major luminance, length must be WIDTH*HEIGHT (9*8 = 72).
     * @return 64-bit difference hash.
     */
    public static long dHash(int[] gray) {
        long hash = 0L;
        int bit = 0;
        for (int row = 0; row < HEIGHT; row++) {
            int base = row * WIDTH;
            for (int col = 0; col < WIDTH - 1; col++) {
                if (gray[base + col] > gray[base + col + 1]) {
                    hash |= (1L << bit);
                }
                bit++;
            }
        }
        return hash;
    }
}
