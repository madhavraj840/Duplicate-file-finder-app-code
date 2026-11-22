package com.bunkwise.duplicatefilefinder

import com.bunkwise.duplicatefilefinder.engage.Engagement
import com.bunkwise.duplicatefilefinder.engage.Engagement.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngagementTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1000 * day
    private val mb = 1024L * 1024

    private fun facts(
        sinceScan: Long = 40 * day,
        leftover: Long = 0,
        newMedia: Int = 0,
        free: Int = 50,
        sincePost: Long = 100 * day,
        ignored: Int = 0,
        lastByKind: Map<Kind, Long> = emptyMap(),
        cleanedAfterScan: Boolean = false,
        busy: Boolean = false,
    ) = Engagement.Facts(
        now = now,
        lastScanAt = if (sinceScan < 0) 0 else now - sinceScan,
        lastCleanupAt = if (cleanedAfterScan) now - sinceScan + 1 else 0,
        leftoverBytes = leftover,
        newMedia = newMedia,
        freePercent = free,
        lastPostAt = now - sincePost,
        ignored = ignored,
        lastByKind = lastByKind,
        leftoverPostedFor = 0,
        busy = busy,
    )

    @Test
    fun picksByPriority() {
        assertEquals(Kind.STORAGE_LOW, Engagement.pick(facts(free = 5, leftover = 200 * mb)))
        assertEquals(Kind.LEFTOVER, Engagement.pick(facts(leftover = 200 * mb, newMedia = 500)))
        assertEquals(Kind.NEW_MEDIA, Engagement.pick(facts(sinceScan = 10 * day, newMedia = 200)))
        assertEquals(Kind.MONTHLY, Engagement.pick(facts(sinceScan = 31 * day)))
        assertNull(Engagement.pick(facts(sinceScan = 10 * day)))
    }

    @Test
    fun neverSpams() {
        assertNull("never scanned", Engagement.pick(facts(sinceScan = -1)))
        assertNull("global gap", Engagement.pick(facts(sincePost = 2 * day)))
        assertNull("ignored too often", Engagement.pick(facts(ignored = Engagement.MAX_IGNORED)))
        assertNull("busy", Engagement.pick(facts(busy = true)))
        assertNull("already cleaned", Engagement.pick(facts(sinceScan = 10 * day, leftover = 200 * mb, cleanedAfterScan = true)))
        assertNull("monthly cooldown", Engagement.pick(facts(lastByKind = mapOf(Kind.MONTHLY to now - 5 * day))))
    }

    @Test
    fun reviewOnlyAfterRealSuccess() {
        assertFalse(Engagement.reviewDue(cleanups = 1, freedNow = 5 * mb, asks = 0, lastAskAt = 0, now = now))
        assertTrue(Engagement.reviewDue(cleanups = 1, freedNow = 150 * mb, asks = 0, lastAskAt = 0, now = now))
        assertTrue(Engagement.reviewDue(cleanups = 2, freedNow = 1 * mb, asks = 0, lastAskAt = 0, now = now))
        assertFalse("asked recently", Engagement.reviewDue(cleanups = 5, freedNow = 1 * mb, asks = 1, lastAskAt = now - 10 * day, now = now))
        assertFalse("asked enough", Engagement.reviewDue(cleanups = 9, freedNow = 1 * mb, asks = Engagement.MAX_REVIEW_ASKS, lastAskAt = 0, now = now))
    }
}
