package com.bunkwise.duplicatefilefinder.core.engine

import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem

/**
 * Chooses the "best copy" to keep within a group (ARCHITECTURE §5.8). The first
 * rule that differentiates two files decides the ordering; the top file is the
 * recommended keeper and powers Smart Cleanup + the "Best copy" badge.
 *
 * Quality first (only differs for similar-image groups), then provenance: for
 * byte-identical copies the original is the one in a camera/original folder,
 * without a "(1)"/"copy" suffix, and — when nothing else tells them apart — the
 * OLDEST one, because copies are made after the original.
 */
object KeeperRanker {

    private val ORIGINAL_HINTS = listOf("dcim", "camera")
    private val DERIVED_HINTS = listOf(".thumbnails", "thumbnail", "whatsapp", "cache", "telegram", "download")

    fun keeperId(files: List<FileItem>): Long {
        if (files.isEmpty()) return -1
        return files.maxWith(::compare).id
    }

    /** Positive => a ranks above b (better keeper). */
    internal fun compare(a: FileItem, b: FileItem): Int {
        // 1. Highest resolution (images/videos with dimensions).
        val resA = a.width.toLong() * a.height.toLong()
        val resB = b.width.toLong() * b.height.toLong()
        if (resA != resB) return resA.compareTo(resB)

        // 2. Largest file size (a recompressed copy is smaller).
        if (a.sizeBytes != b.sizeBytes) return a.sizeBytes.compareTo(b.sizeBytes)

        // 3. Original-looking folder beats derived/cache/download folders.
        val folderScore = folderScore(a.path) - folderScore(b.path)
        if (folderScore != 0) return folderScore

        // 4. "photo.jpg" beats "photo (1).jpg" / "photo - copy.jpg".
        val copyA = isCopyName(a.displayName)
        val copyB = isCopyName(b.displayName)
        if (copyA != copyB) return if (copyA) -1 else 1

        // 5. Oldest wins: a copy can't predate its original.
        if (a.modifiedMs != b.modifiedMs) return b.modifiedMs.compareTo(a.modifiedMs)

        // 6. Tie-break: shorter filename (the original is rarely the longer one).
        return b.displayName.length.compareTo(a.displayName.length)
    }

    private val COPY_SUFFIX = Regex("""\(\d+\)|[-_ ]cop(y|ie)""", RegexOption.IGNORE_CASE)

    /** True when the name looks like a duplicated copy, e.g. "img (1).jpg" or "img - copy.jpg". */
    private fun isCopyName(name: String): Boolean = COPY_SUFFIX.containsMatchIn(name)

    private fun folderScore(path: String): Int {
        val lower = path.lowercase()
        var score = 0
        if (ORIGINAL_HINTS.any { lower.contains("/$it/") }) score += 2
        if (DERIVED_HINTS.any { lower.contains(it) }) score -= 2
        return score
    }
}
