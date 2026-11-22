package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionEngine
import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionOutcome
import com.bunkwise.duplicatefilefinder.core.domain.repository.SkippedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Executes a deletion batch with per-file safety (ARCHITECTURE §7.4). Default
 * path moves each file to the recycle bin; archive mode zips them into a dated
 * archive then removes the originals. Failures never abort the batch — each file
 * independently succeeds or is skipped with a reason.
 */
class DeletionEngineImpl(
    private val context: Context,
    private val bin: RecycleBinRepositoryImpl
) : DeletionEngine {

    override suspend fun delete(
        files: List<FileItem>,
        archive: Boolean,
        archiveTreeUri: String?,
        onProgress: (deleted: Int, total: Int, freed: Long, current: String) -> Unit
    ): Result<DeletionOutcome, AppError.Delete> {
        return try {
            if (archive) archiveAll(files, archiveTreeUri, onProgress) else moveAll(files, onProgress)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Error(AppError.Delete.UNKNOWN)
        }
    }

    private suspend fun moveAll(
        files: List<FileItem>,
        onProgress: (Int, Int, Long, String) -> Unit
    ): Result<DeletionOutcome, AppError.Delete> {
        var deleted = 0
        var freed = 0L
        val skipped = ArrayList<SkippedFile>()
        val movedPaths = ArrayList<String>(files.size)
        val deletedIds = LinkedHashSet<Long>(files.size)
        val batchId = System.currentTimeMillis()
        for ((i, file) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            // flush = false: defer the index write + MediaScanner to a single batched
            // commit below, so deleting N files no longer triggers N full-index writes.
            when (val r = bin.moveToBin(file, flush = false, batchId = batchId)) {
                is Result.Success -> { deleted++; freed += r.data; movedPaths.add(file.path); deletedIds.add(file.id) }
                is Result.Error -> skipped.add(SkippedFile(file.displayName, r.error))
            }
            onProgress(deleted, files.size, freed, file.displayName)
        }
        // One index write + one MediaScanner request for the whole batch.
        bin.commitBatch(movedPaths)
        return Result.Success(DeletionOutcome(deleted, freed, skipped, deletedIds, batchId))
    }

    /**
     * Archive = zip then remove, as a two-phase commit so a crash, kill or full disk
     * can never leave the user with originals deleted and an unreadable zip:
     *  1. write every file plus a manifest into the zip and close it;
     *  2. re-read the finished zip (CRC-checked) to prove it is intact;
     *  3. only then delete the originals that made it into the archive.
     * Any failure before step 3 deletes the partial zip and touches no originals.
     */
    private suspend fun archiveAll(
        files: List<FileItem>,
        archiveTreeUri: String?,
        onProgress: (Int, Int, Long, String) -> Unit
    ): Result<DeletionOutcome, AppError.Delete> = withContext(Dispatchers.IO) {
        if (archiveTreeUri == null) return@withContext Result.Error(AppError.Delete.ARCHIVE_FOLDER_MISSING)
        // Without All-files access, originals outside our own media can't be removed,
        // which would leave every file both archived and still on disk.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return@withContext Result.Error(AppError.Delete.PERMISSION_DENIED)
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val target = createInTree(Uri.parse(archiveTreeUri), "duplicates_$stamp.zip")
            ?: return@withContext Result.Error(AppError.Delete.ARCHIVE_FOLDER_MISSING)

        val skipped = ArrayList<SkippedFile>()
        val archived = ArrayList<FileItem>(files.size)
        val entryNames = HashMap<Long, String>(files.size)

        // ---- phase 1: write ----
        var writeError: AppError.Delete = AppError.Delete.UNKNOWN
        val written = try {
            val out = context.contentResolver.openOutputStream(target) ?: throw IOException("no stream")
            var bytes = 0L
            ZipOutputStream(out.buffered()).use { zip ->
                // Photos and videos are already compressed; deflating them again costs
                // CPU and battery for ~0% gain.
                zip.setLevel(Deflater.NO_COMPRESSION)
                for ((i, file) in files.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val src = File(file.path)
                    if (!src.exists() || src.length() != file.sizeBytes) {
                        skipped.add(SkippedFile(file.displayName, AppError.Delete.FILE_CHANGED))
                        continue
                    }
                    // Duplicates usually share a display name, so each entry is named by
                    // the file's own unique path — which also records where it came from.
                    val name = entryName(file.path)
                    // Open BEFORE starting the entry: an unreadable file is skipped on its
                    // own instead of aborting the whole archive. (A failure mid-copy still
                    // aborts — the zip stream can't be rewound — and touches no originals.)
                    val input = try {
                        src.inputStream()
                    } catch (e: IOException) {
                        skipped.add(SkippedFile(file.displayName, AppError.Delete.PERMISSION_DENIED))
                        continue
                    }
                    try {
                        zip.putNextEntry(ZipEntry(name).apply { time = file.modifiedMs })
                        input.use { it.copyTo(zip) }
                        zip.closeEntry()
                        archived.add(file)
                        entryNames[file.id] = name
                        bytes += file.sizeBytes
                    } catch (e: ZipException) {
                        skipped.add(SkippedFile(file.displayName, AppError.Delete.UNKNOWN))
                    }
                    onProgress(i + 1, files.size, bytes, file.displayName)
                }
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest(archived, entryNames).toByteArray())
                zip.closeEntry()
            }
            true
        } catch (c: CancellationException) {
            discard(target)
            throw c
        } catch (t: Throwable) {
            // Only a real "no space left" is reported as a space problem.
            if (t.message?.contains("ENOSPC") == true || t.message?.contains("No space", ignoreCase = true) == true) {
                writeError = AppError.Delete.BIN_FULL
            }
            false
        }

        // ---- phase 2: verify ----
        if (!written) {
            discard(target)
            return@withContext Result.Error(writeError)
        }
        if (archived.isEmpty()) {
            discard(target)
            return@withContext Result.Success(DeletionOutcome(0, 0L, skipped))
        }
        if (!verify(target, archived.size + 1)) {
            discard(target)
            return@withContext Result.Error(AppError.Delete.UNKNOWN)
        }

        // ---- phase 3: the archive is proven good; remove the originals ----
        var deleted = 0
        var freed = 0L
        val deletedIds = LinkedHashSet<Long>(archived.size)
        val removedPaths = ArrayList<String>(archived.size)
        for (file in archived) {
            if (File(file.path).delete()) {
                deleted++; freed += file.sizeBytes; deletedIds.add(file.id); removedPaths.add(file.path)
            } else {
                skipped.add(SkippedFile(file.displayName, AppError.Delete.PERMISSION_DENIED))
            }
        }
        // Tell MediaStore the originals are gone so galleries don't show broken tiles.
        if (removedPaths.isNotEmpty()) {
            runCatching { MediaScannerConnection.scanFile(context, removedPaths.toTypedArray(), null, null) }
        }
        Result.Success(DeletionOutcome(deleted, freed, skipped, deletedIds))
    }

    /** "/storage/emulated/0/DCIM/a.jpg" -> "DCIM/a.jpg"; other volumes keep their full path. */
    private fun entryName(path: String): String {
        val root = Environment.getExternalStorageDirectory().path.trimEnd('/') + "/"
        return if (path.startsWith(root)) path.removePrefix(root) else path.trimStart('/')
    }

    private fun manifest(files: List<FileItem>, names: Map<Long, String>): String =
        JSONArray(files.map { f ->
            JSONObject().apply {
                put("entry", names[f.id]); put("originalPath", f.path)
                put("sizeBytes", f.sizeBytes); put("modifiedMs", f.modifiedMs)
            }
        }).toString(2)

    /** Reads the finished zip end to end; ZipInputStream checks every entry's CRC. */
    private fun verify(uri: Uri, expectedEntries: Int): Boolean = runCatching {
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                val sink = ByteArray(64 * 1024)
                while (zip.nextEntry != null) {
                    while (zip.read(sink) != -1) { /* drain: validates the CRC */ }
                    count++
                }
            }
        }
        count == expectedEntries
    }.getOrDefault(false)

    private fun discard(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    /** Creates [name] inside a SAF tree and returns its document uri, or null. */
    private fun createInTree(treeUri: Uri, name: String): Uri? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        DocumentsContract.createDocument(context.contentResolver, dirUri, "application/zip", name)
    }.getOrNull()

    private companion object {
        const val MANIFEST = "manifest.json"
    }
}
