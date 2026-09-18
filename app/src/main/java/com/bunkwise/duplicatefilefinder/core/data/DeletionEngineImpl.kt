package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import android.net.Uri
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
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
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
        for ((i, file) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            // flush = false: defer the index write + MediaScanner to a single batched
            // commit below, so deleting N files no longer triggers N full-index writes.
            when (val r = bin.moveToBin(file, flush = false)) {
                is Result.Success -> { deleted++; freed += r.data; movedPaths.add(file.path); deletedIds.add(file.id) }
                is Result.Error -> skipped.add(SkippedFile(file.displayName, r.error))
            }
            onProgress(deleted, files.size, freed, file.displayName)
        }
        // One index write + one MediaScanner request for the whole batch.
        bin.commitBatch(movedPaths)
        return Result.Success(DeletionOutcome(deleted, freed, skipped, deletedIds))
    }

    private suspend fun archiveAll(
        files: List<FileItem>,
        archiveTreeUri: String?,
        onProgress: (Int, Int, Long, String) -> Unit
    ): Result<DeletionOutcome, AppError.Delete> = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipName = "duplicates_$stamp.zip"

        // Open the destination zip stream: user-chosen SAF folder, else app folder.
        val output: OutputStream = if (archiveTreeUri != null) {
            openInTree(Uri.parse(archiveTreeUri), zipName)
                ?: return@withContext Result.Error(AppError.Delete.PERMISSION_DENIED)
        } else {
            val archiveDir = File(context.getExternalFilesDir(null), "archives").apply { mkdirs() }
            val needed = (files.sumOf { it.sizeBytes } * 1.1).toLong()
            if (archiveDir.usableSpace < needed) return@withContext Result.Error(AppError.Delete.BIN_FULL)
            File(archiveDir, zipName).outputStream()
        }

        var deleted = 0
        var freed = 0L
        val skipped = ArrayList<SkippedFile>()
        val deletedIds = LinkedHashSet<Long>(files.size)
        ZipOutputStream(output.buffered()).use { zip ->
            for (file in files) {
                currentCoroutineContext().ensureActive()
                val src = File(file.path)
                if (!src.exists() || src.length() != file.sizeBytes) {
                    skipped.add(SkippedFile(file.displayName, AppError.Delete.FILE_CHANGED)); continue
                }
                try {
                    zip.putNextEntry(ZipEntry(file.displayName))
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    if (src.delete()) { deleted++; freed += file.sizeBytes; deletedIds.add(file.id) }
                    else skipped.add(SkippedFile(file.displayName, AppError.Delete.PERMISSION_DENIED))
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    skipped.add(SkippedFile(file.displayName, AppError.Delete.UNKNOWN))
                }
                onProgress(deleted, files.size, freed, file.displayName)
            }
        }
        Result.Success(DeletionOutcome(deleted, freed, skipped, deletedIds))
    }

    /** Creates [name] inside a SAF tree and returns a writable stream, or null. */
    private fun openInTree(treeUri: Uri, name: String): OutputStream? = runCatching {
        val resolver = context.contentResolver
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val fileUri = DocumentsContract.createDocument(resolver, dirUri, "application/zip", name)
            ?: return null
        resolver.openOutputStream(fileUri)
    }.getOrNull()
}
