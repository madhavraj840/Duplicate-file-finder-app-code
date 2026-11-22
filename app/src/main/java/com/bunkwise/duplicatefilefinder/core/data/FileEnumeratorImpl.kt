package com.bunkwise.duplicatefilefinder.core.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Enumerates candidate files from MediaStore (ARCHITECTURE §5.1). Images/videos/
 * audio come from their typed collections (with dimensions/duration); documents
 * and "other" come from MediaStore.Files classified by mime. Exclusions (our bin,
 * Android/data, sub-1 KB files) are applied at this cheapest point.
 */
class FileEnumeratorImpl(
    private val context: Context,
    private val settings: SettingsRepository
) : FileEnumerator {

    private val resolver: ContentResolver get() = context.contentResolver

    private val minSize = 1024L
    private val excludedFragments = listOf("/recycle_bin/", "/Android/data/", "/.thumbnails/")

    /**
     * User-configured scan scope (Settings, premium). [include] restricts scanning to
     * those folder subtrees (empty = whole device); [exclude] skips subtrees. Paths are
     * normalised to lowercase without a trailing slash for prefix comparison.
     */
    private data class Scope(val include: List<String>, val exclude: List<String>) {
        fun accepts(path: String): Boolean {
            val p = path.lowercase()
            if (exclude.any { p == it || p.startsWith("$it/") }) return false
            if (include.isNotEmpty() && include.none { p == it || p.startsWith("$it/") }) return false
            return true
        }
    }

    private suspend fun currentScope(): Scope {
        val s = settings.current()
        fun norm(p: String) = p.trimEnd('/').lowercase()
        return Scope(s.scanFolders.map(::norm), s.excludeFolders.map(::norm))
    }

    override fun enumerate(categories: Set<FileCategory>, budget: Int): Flow<FileItem> = flow {
        val want = expand(categories)
        val scope = currentScope()
        var emitted = 0

        suspend fun tryEmit(item: FileItem?): Boolean {
            if (item == null) return true
            if (emitted >= budget) return false
            emit(item)
            emitted++
            return emitted < budget
        }

        if (FileCategory.IMAGES in want) {
            queryTyped(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.IMAGES, hasDuration = false, scope) {
                if (!tryEmit(it.copy(id = stableId(it.path)))) return@flow
            }
        }
        if (FileCategory.VIDEOS in want) {
            queryTyped(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEOS, hasDuration = true, scope) {
                if (!tryEmit(it.copy(id = stableId(it.path)))) return@flow
            }
        }
        if (FileCategory.AUDIO in want) {
            queryTyped(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.AUDIO, hasDuration = true, scope) {
                if (!tryEmit(it.copy(id = stableId(it.path)))) return@flow
            }
        }
        if (FileCategory.DOCUMENTS in want || FileCategory.OTHER in want) {
            queryFiles(want, scope) {
                if (!tryEmit(it.copy(id = stableId(it.path)))) return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stable 64-bit id derived from the file path (FNV-1a). Unlike a per-scan
     * counter, the same physical file keeps the same id across scans, which keeps
     * selection consistent and enables future "ignore this file" lists.
     */
    private fun stableId(path: String): Long {
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (ch in path) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L // FNV prime
        }
        return hash
    }

    /** Per-category totals from one MediaStore pass, shared by Home sizes and the quota estimate. */
    private data class Stats(val sizes: Map<FileCategory, Long>, val counts: Map<FileCategory, Int>)

    /** What a cached [Stats] was computed against; any change means it is stale. */
    private data class StatsKey(val scope: Scope, val generation: Long)

    @Volatile private var cached: Pair<StatsKey, Stats>? = null
    @Volatile private var cachedAtMs = 0L

    override fun invalidateSizes() { cached = null }

    override suspend fun estimateCount(categories: Set<FileCategory>): Int {
        val counts = stats().counts
        return expand(categories).sumOf { counts[it] ?: 0 }
    }

    override suspend fun sizeByCategory(): Map<FileCategory, Long> = stats().sizes

    /**
     * Returns cached totals unless the media library changed. On API 30+ MediaStore's
     * generation counter tells us that for free, so returning to Home costs one tiny
     * call instead of walking every row. Older devices fall back to a short TTL; our
     * own scans/deletes invalidate explicitly either way.
     */
    private suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val key = StatsKey(currentScope(), mediaGeneration())
        val hit = cached
        if (hit != null) {
            val fresh = if (key.generation >= 0) hit.first == key
            else hit.first.scope == key.scope && System.currentTimeMillis() - cachedAtMs < LEGACY_TTL_MS
            if (fresh) return@withContext hit.second
        }
        val computed = computeStats(key.scope)
        cached = key to computed
        cachedAtMs = System.currentTimeMillis()
        computed
    }

    private fun mediaGeneration(): Long =
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching { MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL) }.getOrDefault(-1L)
        } else -1L

    /** ONE pass over MediaStore.Files, classified by MEDIA_TYPE (was four queries). */
    private fun computeStats(scope: Scope): Stats {
        val sizes = linkedMapOf(
            FileCategory.IMAGES to 0L, FileCategory.VIDEOS to 0L,
            FileCategory.DOCUMENTS to 0L, FileCategory.AUDIO to 0L, FileCategory.OTHER to 0L
        )
        val counts = HashMap<FileCategory, Int>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        resolver.query(MediaStore.Files.getContentUri("external"), projection, null, null, null)?.use { c ->
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (c.moveToNext()) {
                val path = c.getStringOrNull(dataCol) ?: continue
                if (isExcluded(path) || !scope.accepts(path)) continue
                val size = c.getLong(sizeCol)
                if (size < minSize) continue
                val cat = when (c.getInt(typeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> FileCategory.IMAGES
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> FileCategory.VIDEOS
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> FileCategory.AUDIO
                    // Same rule as enumeration: untyped rows only count as docs/other.
                    else -> categoryOf(c.getStringOrNull(mimeCol), path)
                        .takeIf { it == FileCategory.DOCUMENTS || it == FileCategory.OTHER } ?: continue
                }
                sizes[cat] = sizes.getValue(cat) + size
                counts[cat] = (counts[cat] ?: 0) + 1
            }
        }
        return Stats(sizes, counts)
    }

    // ---- helpers ----

    private inline fun queryTyped(
        uri: Uri,
        category: FileCategory,
        hasDuration: Boolean,
        scope: Scope,
        onItem: (FileItem) -> Unit
    ) {
        val modern = android.os.Build.VERSION.SDK_INT >= 29
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            // WIDTH/HEIGHT for images predate 29; for video they are 29+.
            if (category == FileCategory.IMAGES || (category == FileCategory.VIDEOS && modern)) {
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
            }
            if (hasDuration && modern) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()

        resolver.query(uri, projection, null, null, SCAN_ORDER)?.use { c ->
            val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val durCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            while (c.moveToNext()) {
                val path = c.getStringOrNull(dataCol) ?: continue
                if (isExcluded(path) || !scope.accepts(path)) continue
                val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                if (size < minSize) continue
                onItem(
                    FileItem(
                        id = 0L,
                        path = path,
                        uri = "$uri",
                        displayName = c.getStringOrNull(nameCol) ?: path.substringAfterLast('/'),
                        sizeBytes = size,
                        modifiedMs = (if (dateCol >= 0) c.getLong(dateCol) else 0L) * 1000L,
                        mime = c.getStringOrNull(mimeCol),
                        category = category,
                        width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                        durationMs = if (durCol >= 0) c.getLong(durCol) else 0L
                    )
                )
            }
        }
    }

    private inline fun queryFiles(want: Set<FileCategory>, scope: Scope, onItem: (FileItem) -> Unit) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        resolver.query(uri, projection, null, null, SCAN_ORDER)?.use { c ->
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            while (c.moveToNext()) {
                val path = c.getStringOrNull(dataCol) ?: continue
                if (isExcluded(path) || !scope.accepts(path)) continue
                val size = c.getLong(sizeCol)
                if (size < minSize) continue
                val cat = categoryOf(c.getStringOrNull(mimeCol), path)
                if (cat != FileCategory.DOCUMENTS && cat != FileCategory.OTHER) continue
                if (cat !in want) continue
                onItem(
                    FileItem(
                        id = 0L,
                        path = path,
                        uri = "$uri",
                        displayName = c.getStringOrNull(nameCol) ?: path.substringAfterLast('/'),
                        sizeBytes = size,
                        modifiedMs = c.getLong(dateCol) * 1000L,
                        mime = c.getStringOrNull(mimeCol),
                        category = cat
                    )
                )
            }
        }
    }

    private fun isExcluded(path: String): Boolean =
        excludedFragments.any { path.contains(it, ignoreCase = true) }

    private fun categoryOf(mime: String?, path: String): FileCategory {
        val m = mime?.lowercase() ?: ""
        val ext = path.substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") -> FileCategory.IMAGES
            m.startsWith("video/") -> FileCategory.VIDEOS
            m.startsWith("audio/") -> FileCategory.AUDIO
            m == "application/pdf" || ext in DOC_EXT -> FileCategory.DOCUMENTS
            m.startsWith("text/") -> FileCategory.DOCUMENTS
            m.contains("word") || m.contains("officedocument") || m.contains("ms-excel") || m.contains("powerpoint") -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }

    private fun expand(categories: Set<FileCategory>): Set<FileCategory> =
        if (categories.contains(FileCategory.ALL) || categories.isEmpty()) {
            setOf(FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO, FileCategory.DOCUMENTS, FileCategory.OTHER)
        } else categories

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private companion object {
        val DOC_EXT = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "md")

        /**
         * Deterministic ordering for enumeration. Without an explicit ORDER BY,
         * MediaStore returns rows in an unstable order, so a budget-limited scan
         * (free tier) can visit a different subset of files each run — surfacing
         * duplicates one run and missing them the next. Sorting by _ID (unique,
         * newest-first) makes the budget cutoff land on the SAME files every time,
         * which is what makes repeated scans reproducible.
         */
        val SCAN_ORDER = "${MediaStore.MediaColumns._ID} DESC"

        /** Pre-API-30 devices have no generation counter; recompute at most this often. */
        const val LEGACY_TTL_MS = 5 * 60_000L
    }
}
