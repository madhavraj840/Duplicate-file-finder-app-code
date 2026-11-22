package com.bunkwise.duplicatefilefinder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    @Test fun lowEnd() {
        assertTrue(Performance.isLowEnd(lowRam = true, totalMem = 8L shl 30))
        assertTrue(Performance.isLowEnd(false, (3L shl 30) - 1))
        assertFalse(Performance.isLowEnd(false, 3L shl 30))
    }

    @Test fun slow() {
        assertTrue(Performance.isSlow(fps = 50f, refresh = 120f))   // < min(55, 72)
        assertFalse(Performance.isSlow(fps = 37f, refresh = 60f))   // threshold 36
        assertTrue(Performance.isSlow(fps = 35f, refresh = 60f))
    }
}
