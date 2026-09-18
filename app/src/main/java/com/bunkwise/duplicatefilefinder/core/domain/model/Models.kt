package com.bunkwise.duplicatefilefinder.core.domain.model

/**
 * File categories used for enumeration selection, filtering and result grouping.
 * ALL is mutually exclusive with the others in the selection UI.
 */
enum class FileCategory {
    ALL, IMAGES, VIDEOS, DOCUMENTS, AUDIO, OTHER
}

/**
 * The four scan modes (ARCHITECTURE §1, DESIGN §3.2). Descriptions are framed by
 * WHAT they detect, not the algorithm. Titles/descriptions here are keys resolved
 * to localized strings in the UI; the raw copy lives in strings.xml.
 */
enum class ScanMode {
    EXACT, SIMILAR, VERY_SIMILAR, DEEP
}

/**
 * Cadence for the premium background scheduled scan (Settings › Schedule Scan).
 * Feeds a WorkManager PeriodicWorkRequest; [intervalHours] is null when disabled.
 */
enum class ScanSchedule {
    OFF, DAILY, WEEKLY, MONTHLY;

    val intervalHours: Long?
        get() = when (this) {
            OFF -> null
            DAILY -> 24L
            WEEKLY -> 24L * 7
            MONTHLY -> 24L * 30
        }
}

/** Real file type resolved from magic bytes (ARCHITECTURE §5.2). */
enum class DetectedType {
    JPEG, PNG, GIF, WEBP, HEIF, BMP,
    MP4, VIDEO_OTHER,
    PDF, DOCX, TXT,
    AUDIO,
    ZIP, BINARY, UNKNOWN;

    fun toCategory(): FileCategory = when (this) {
        JPEG, PNG, GIF, WEBP, HEIF, BMP -> FileCategory.IMAGES
        MP4, VIDEO_OTHER -> FileCategory.VIDEOS
        PDF, DOCX, TXT -> FileCategory.DOCUMENTS
        AUDIO -> FileCategory.AUDIO
        else -> FileCategory.OTHER
    }

    /** Only image/text-like types support similarity matching in v1. */
    val supportsSimilarity: Boolean
        get() = this == JPEG || this == PNG || this == WEBP || this == HEIF || this == BMP
}

/** A single file discovered during enumeration. UI-free, DB-free. */
data class FileItem(
    val id: Long,
    val path: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val mime: String?,
    val category: FileCategory,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L
)

/** Confidence band for a duplicate group (ARCHITECTURE §5.6). */
enum class Confidence { EXACT, VERY_HIGH, HIGH, MEDIUM }

/** One piece of evidence backing a non-exact match. */
data class Signal(val name: String, val value: Float, val passed: Boolean)

data class MatchEvidence(
    val fusedScore: Float,
    val confidence: Confidence,
    val signals: List<Signal>
)

/**
 * A set of files determined to be duplicates of one another. A group always has
 * >= 2 files and exactly one recommended keeper (ARCHITECTURE §5.7 / §5.8).
 */
data class DuplicateGroup(
    val id: Long,
    val category: FileCategory,
    val files: List<FileItem>,
    val keeperId: Long,
    val evidence: MatchEvidence?
) {
    val fileCount: Int get() = files.size
    /** Number of removable copies (everything but the keeper). */
    val duplicateCount: Int get() = files.size - 1
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    /** Space reclaimable if all non-keepers are removed. */
    val wastedBytes: Long get() = totalBytes - (files.firstOrNull { it.id == keeperId }?.sizeBytes ?: 0L)
    val confidence: Confidence get() = evidence?.confidence ?: Confidence.EXACT
}

/** Phase of an active scan, used for weighted progress + labels. */
enum class ScanPhase { ENUMERATING, HASHING, COMPARING, GROUPING, DONE }

/** Live scan progress snapshot published by the engine collector. */
data class ScanProgress(
    val phase: ScanPhase = ScanPhase.ENUMERATING,
    val percent: Int = 0,
    val currentPath: String = "",
    val filesScanned: Int = 0,
    val duplicatesFound: Int = 0,
    val elapsedMs: Long = 0L,
    val slowedForBattery: Boolean = false
)

/** The persisted outcome of the most recent completed scan. */
data class ScanResult(
    val mode: ScanMode,
    val categories: Set<FileCategory>,
    val groups: List<DuplicateGroup>,
    val finishedAtMs: Long
) {
    val totalDuplicateFiles: Int get() = groups.sumOf { it.duplicateCount }
    val groupCount: Int get() = groups.size
    val wastedBytes: Long get() = groups.sumOf { it.wastedBytes }

    /** Wasted bytes broken down by category (drives the donut chart). */
    fun wastedByCategory(): Map<FileCategory, Long> {
        val map = linkedMapOf<FileCategory, Long>()
        for (g in groups) map[g.category] = (map[g.category] ?: 0L) + g.wastedBytes
        return map
    }
}

/** Device storage snapshot for the Home overview card. */
data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = if (totalBytes <= 0) 0 else ((usedBytes * 100.0) / totalBytes).toInt().coerceIn(0, 100)
    val freePercent: Int get() = (100 - usedPercent).coerceIn(0, 100)
}

/** Free/premium daily quota snapshot (ARCHITECTURE §10). */
data class QuotaInfo(
    val limit: Int,
    val used: Int,
    val bonus: Int,
    val isPremium: Boolean
) {
    val remaining: Int get() = if (isPremium) Int.MAX_VALUE else (limit + bonus - used).coerceAtLeast(0)
}

/** An entry in the recycle bin (ARCHITECTURE §7.1). */
data class BinEntry(
    val id: Long,
    val binFileName: String,
    val originalPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val deletedAtMs: Long,
    val expiresAtMs: Long
) {
    fun daysLeft(nowMs: Long): Int =
        ((expiresAtMs - nowMs) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
}
