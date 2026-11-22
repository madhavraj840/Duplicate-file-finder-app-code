package com.bunkwise.duplicatefilefinder

import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.model.KeepRule
import com.bunkwise.duplicatefilefinder.core.engine.KeeperRanker
import org.junit.Assert.assertEquals
import org.junit.Test

/** The recommended copy must be the original, not the newest copy of it. */
class KeeperRankerTest {

    private fun file(id: Long, path: String, modified: Long, size: Long = 1000, w: Int = 0, h: Int = 0) = FileItem(
        id = id, path = path, uri = "", displayName = path.substringAfterLast('/'),
        sizeBytes = size, modifiedMs = modified, mime = null, category = FileCategory.IMAGES, width = w, height = h
    )

    @Test
    fun cameraOriginalBeatsNewerWhatsAppCopy() {
        val original = file(1, "/storage/emulated/0/DCIM/Camera/IMG_1.jpg", modified = 1_000)
        val copy = file(2, "/storage/emulated/0/WhatsApp/Media/IMG_1.jpg", modified = 9_000)
        assertEquals(1L, KeeperRanker.keeperId(listOf(copy, original)))
    }

    @Test
    fun unsuffixedNameBeatsCopySuffixInSameFolder() {
        val original = file(1, "/storage/emulated/0/Pictures/photo.jpg", modified = 5_000)
        val copy = file(2, "/storage/emulated/0/Pictures/photo (1).jpg", modified = 1_000)
        assertEquals(1L, KeeperRanker.keeperId(listOf(copy, original)))
    }

    @Test
    fun oldestWinsWhenNothingElseDiffers() {
        val older = file(1, "/storage/emulated/0/Pictures/a.jpg", modified = 1_000)
        val newer = file(2, "/storage/emulated/0/Pictures/b.jpg", modified = 2_000)
        assertEquals(1L, KeeperRanker.keeperId(listOf(newer, older)))
    }

    @Test
    fun higherResolutionStillWinsForSimilarPhotos() {
        val small = file(1, "/storage/emulated/0/DCIM/Camera/a.jpg", modified = 1_000, w = 800, h = 600)
        val large = file(2, "/storage/emulated/0/Download/a.jpg", modified = 2_000, w = 4000, h = 3000)
        assertEquals(2L, KeeperRanker.keeperId(listOf(small, large)))
    }

    @Test
    fun keepRulesPickByAgeAndName() {
        val oldLong = file(1, "/storage/emulated/0/Pictures/holiday_photo.jpg", modified = 1_000)
        val newShort = file(2, "/storage/emulated/0/Pictures/h.jpg", modified = 9_000)
        val files = listOf(oldLong, newShort)
        assertEquals(1L, KeeperRanker.keeperId(files, KeepRule.OLDEST))
        assertEquals(2L, KeeperRanker.keeperId(files, KeepRule.NEWEST))
        assertEquals(2L, KeeperRanker.keeperId(files, KeepRule.SHORTEST_NAME))
        assertEquals(1L, KeeperRanker.keeperId(files, KeepRule.LONGEST_NAME))
    }
}
