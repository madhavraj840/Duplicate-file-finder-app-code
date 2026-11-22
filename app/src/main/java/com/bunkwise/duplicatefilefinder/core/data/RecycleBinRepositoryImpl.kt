package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import android.media.MediaScannerConnection
import android.util.AtomicFile
import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.repository.RecycleBinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Recycle bin (ARCHITECTURE §7.1). DELETE = MOVE into an app-private dir with a
 * UUID name; RESTORE = move back; PURGE removes entries past their 30-day expiry.
 * The bin is uncapped per product spec (unlimited). Index persisted as JSON.
 */
class RecycleBinRepositoryImpl(
    private val context: Context
) : RecycleBinRepository {

    private val binDir: File = File(context.getExternalFilesDir(null), "recycle_bin").apply { mkdirs() }
    private val indexFile = AtomicFile(File(context.filesDir, "bin_index.json"))
    /**
     * Write-ahead log for batch moves: one JSON line per file, appended BEFORE the file
     * is moved. If the process dies before [commitBatch] writes the index, the next
     * launch replays it, so a moved file can never sit in the bin unindexed (invisible,
     * unrestorable, never purged).
     */
    private val journalFile = File(context.filesDir, "bin_journal.jsonl")
    private val state = MutableStateFlow(loadIndex() + recoverJournal())
    val flow: StateFlow<List<BinEntry>> = state.asStateFlow()

    init {
        if (journalFile.exists()) { persist(); journalFile.delete() }
    }

    // Every read-modify-write of `state.value` (and the persist() that follows it)
    // must go through this lock. Without it, two mutators racing (e.g. the app-launch
    // purgeExpired() in App.kt overlapping a user restore/delete tap) can both read the
    // same starting list and the loser's write is silently lost - the physical file
    // move/delete already happened, but the JSON index falls out of sync with disk.
    private val mutex = Mutex()

    private val retentionMs = 30L * 24 * 60 * 60 * 1000

    override fun observe() = flow

    /** Absolute path of the moved file inside the bin (for thumbnails / opening). */
    fun binFilePath(entry: BinEntry): String = File(binDir, entry.binFileName).path

    /** Undo for one cleanup: restores every entry of [batchId]; returns the restored ORIGINAL paths. */
    suspend fun restoreBatch(batchId: Long): Set<String> {
        val entries = state.value.filter { it.batchId == batchId && batchId != 0L }
        val before = entries.associateBy { it.id }
        restoreMany(before.keys)
        val stillInBin = state.value.mapTo(HashSet()) { it.id }
        return before.values.filter { it.id !in stillInBin }.mapTo(HashSet()) { it.originalPath }
    }

    /** Restores several entries with a single index write; returns how many succeeded. */
    suspend fun restoreMany(ids: Set<Long>): Int = withContext(Dispatchers.IO) {
        val restoredPaths = ArrayList<String>()
        val done = HashSet<Long>()
        for (id in ids) {
            val entry = state.value.firstOrNull { it.id == id } ?: continue
            val binFile = File(binDir, entry.binFileName)
            if (!binFile.exists()) { done.add(id); continue } // stale entry: drop, not restored
            restoreFile(entry, binFile)?.let { restoredPaths.add(it); done.add(id) }
        }
        mutex.withLock {
            state.value = state.value.filterNot { it.id in done }
            persist()
        }
        if (restoredPaths.isNotEmpty()) scan(restoredPaths.toTypedArray())
        restoredPaths.size
    }

    /** Permanently deletes several entries with a single index write. */
    suspend fun deleteManyForever(ids: Set<Long>) = withContext(Dispatchers.IO) {
        state.value.filter { it.id in ids }.forEach { runCatching { File(binDir, it.binFileName).delete() } }
        mutex.withLock {
            state.value = state.value.filterNot { it.id in ids }
            persist()
        }
    }

    /**
     * Moves a file into the bin. Called by the DeletionEngine.
     *
     * [flush] controls the expensive I/O: when true (default) the JSON index is
     * rewritten and the MediaScanner is pinged for this one path. Batch callers
     * pass false and call [commitBatch] once at the end, turning O(N) full-index
     * writes + O(N) scanner requests into a single write + single scan.
     */
    suspend fun moveToBin(file: FileItem, flush: Boolean = true, batchId: Long = 0L): Result<Long, AppError.Delete> = withContext(Dispatchers.IO) {
        val src = File(file.path)
        // Pre-flight re-verification (INVARIANT 2, §7.4).
        if (!src.exists()) return@withContext Result.Error(AppError.Delete.TARGET_MISSING)
        if (src.length() != file.sizeBytes) return@withContext Result.Error(AppError.Delete.FILE_CHANGED)

        val binName = "${UUID.randomUUID()}_${file.displayName}"
        val dest = File(binDir, binName)
        val now = System.currentTimeMillis()
        val entry = BinEntry(
            id = UUID.randomUUID().mostSignificantBits,
            binFileName = binName,
            originalPath = file.path,
            displayName = file.displayName,
            sizeBytes = file.sizeBytes,
            deletedAtMs = now,
            expiresAtMs = now + retentionMs,
            batchId = batchId
        )
        // Log intent first; a line whose bin file never appeared is ignored on replay.
        if (!flush) runCatching { journalFile.appendText(toJson(entry).toString() + "\n") }
        val renamed = runCatching { src.renameTo(dest) }.getOrDefault(false)
        if (!renamed) {
            // rename fails across volumes (e.g. SD card → internal bin); the copy
            // fallback then needs real free space, so report low space truthfully
            // instead of a misleading PERMISSION_DENIED.
            if (binDir.usableSpace < file.sizeBytes) return@withContext Result.Error(AppError.Delete.BIN_FULL)
            if (!copyThenDelete(src, dest)) return@withContext Result.Error(AppError.Delete.PERMISSION_DENIED)
        }

        mutex.withLock {
            state.value = state.value + entry
            if (flush) persist()
        }
        if (flush) scan(arrayOf(file.path))
        Result.Success(file.sizeBytes)
    }

    /** Persists the index once and hands all moved paths to the MediaScanner in one call. */
    suspend fun commitBatch(originalPaths: List<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            persist()
            journalFile.delete()
        }
        if (originalPaths.isNotEmpty()) scan(originalPaths.toTypedArray())
    }

    override suspend fun restore(id: Long): Result<Unit, AppError.Delete> = withContext(Dispatchers.IO) {
        val entry = state.value.firstOrNull { it.id == id }
            ?: return@withContext Result.Error(AppError.Delete.TARGET_MISSING)
        val binFile = File(binDir, entry.binFileName)
        if (!binFile.exists()) {
            mutex.withLock { removeEntry(id) }
            return@withContext Result.Error(AppError.Delete.TARGET_MISSING)
        }
        val restoredPath = restoreFile(entry, binFile)
            ?: return@withContext Result.Error(AppError.Delete.PERMISSION_DENIED)
        mutex.withLock { removeEntry(id) }
        scan(arrayOf(restoredPath))
        Result.Success(Unit)
    }

    /** Moves [binFile] back to its original location (or a free " (restored)" name). */
    private fun restoreFile(entry: BinEntry, binFile: File): String? {
        var target = File(entry.originalPath)
        if (target.exists()) {
            // Find a free name so repeated restores never overwrite each other.
            val dir = target.parentFile
            val dot = entry.displayName.lastIndexOf('.')
            val base = if (dot > 0) entry.displayName.substring(0, dot) else entry.displayName
            val ext = if (dot > 0) entry.displayName.substring(dot) else ""
            var n = 1
            do {
                val suffix = if (n == 1) " (restored)" else " (restored $n)"
                target = File(dir, "$base$suffix$ext")
                n++
            } while (target.exists() && n < 10_000)
        }
        target.parentFile?.mkdirs()
        val ok = runCatching { binFile.renameTo(target) }.getOrDefault(false) || copyThenDelete(binFile, target)
        return if (ok) target.path else null
    }

    override suspend fun deleteForever(id: Long): Result<Unit, AppError.Delete> = withContext(Dispatchers.IO) {
        val entry = state.value.firstOrNull { it.id == id }
            ?: return@withContext Result.Error(AppError.Delete.TARGET_MISSING)
        runCatching { File(binDir, entry.binFileName).delete() }
        mutex.withLock { removeEntry(id) }
        Result.Success(Unit)
    }

    override suspend fun emptyBin() = withContext(Dispatchers.IO) {
        val snapshot = state.value
        snapshot.forEach { runCatching { File(binDir, it.binFileName).delete() } }
        // Subtract exactly the entries whose files we deleted, rather than overwriting
        // with emptyList() - anything added concurrently after the snapshot must survive.
        mutex.withLock {
            state.value = state.value - snapshot.toSet()
            persist()
        }
    }

    override suspend fun purgeExpired() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expired = state.value.filter { it.expiresAtMs <= now }
        if (expired.isEmpty()) return@withContext
        expired.forEach { runCatching { File(binDir, it.binFileName).delete() } }
        mutex.withLock {
            state.value = state.value - expired.toSet()
            persist()
        }
    }

    override suspend fun totalBytes(): Long = state.value.sumOf { it.sizeBytes }

    // ---- helpers ----

    /** Mutates [state] + persists. Caller must already hold [mutex] (not reentrant). */
    private fun removeEntry(id: Long) {
        state.value = state.value.filterNot { it.id == id }
        persist()
    }

    private fun copyThenDelete(src: File, dest: File): Boolean = try {
        src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        // If the source can't be removed (e.g. no All-files access) the copy must go
        // too — otherwise it sits in the bin folder unindexed, invisible and never purged.
        if (dest.length() == src.length() && src.delete()) true else { dest.delete(); false }
    } catch (t: Throwable) {
        false
    }

    private fun scan(paths: Array<String>) {
        runCatching { MediaScannerConnection.scanFile(context, paths, null, null) }
    }

    private fun persist() {
        runCatching {
            val arr = JSONArray(state.value.map(::toJson))
            // AtomicFile: a crash mid-write keeps the previous index instead of truncating it.
            val out = indexFile.startWrite()
            try {
                out.write(arr.toString().toByteArray())
                indexFile.finishWrite(out)
            } catch (t: Throwable) {
                indexFile.failWrite(out)
            }
        }
    }

    private fun loadIndex(): List<BinEntry> = runCatching {
        if (!indexFile.baseFile.exists()) return emptyList()
        val arr = JSONArray(String(indexFile.readFully()))
        (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
    }.getOrElse { emptyList() }

    /** Journal entries from an interrupted batch whose file really did reach the bin. */
    private fun recoverJournal(): List<BinEntry> = runCatching {
        if (!journalFile.exists()) return emptyList()
        val indexed = loadIndex().mapTo(HashSet()) { it.binFileName }
        journalFile.readLines()
            .mapNotNull { line -> runCatching { fromJson(JSONObject(line)) }.getOrNull() }
            .filter { it.binFileName !in indexed && File(binDir, it.binFileName).exists() }
    }.getOrElse { emptyList() }

    private fun toJson(e: BinEntry) = JSONObject().apply {
        put("id", e.id); put("binFileName", e.binFileName)
        put("originalPath", e.originalPath); put("displayName", e.displayName)
        put("sizeBytes", e.sizeBytes); put("deletedAtMs", e.deletedAtMs)
        put("expiresAtMs", e.expiresAtMs); put("batchId", e.batchId)
    }

    private fun fromJson(o: JSONObject) = BinEntry(
        id = o.getLong("id"),
        binFileName = o.getString("binFileName"),
        originalPath = o.getString("originalPath"),
        displayName = o.getString("displayName"),
        sizeBytes = o.getLong("sizeBytes"),
        deletedAtMs = o.getLong("deletedAtMs"),
        expiresAtMs = o.getLong("expiresAtMs"),
        batchId = o.optLong("batchId", 0L)
    )
}
