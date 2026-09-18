package com.bunkwise.duplicatefilefinder.core.domain.repository

import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.model.QuotaInfo
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow

/** Enumerates candidate files from MediaStore + filesystem (ARCHITECTURE §5.1). */
interface FileEnumerator {
    /**
     * Emits files in the selected categories. [budget] caps the number emitted
     * (quota). Emission order favours directory locality.
     */
    fun enumerate(categories: Set<FileCategory>, budget: Int): Flow<FileItem>

    /** Fast COUNT(*) estimate used for the pre-scan quota check. */
    suspend fun estimateCount(categories: Set<FileCategory>): Int

    /** Total on-disk size per category for the Home "Choose Files" cards. */
    suspend fun sizeByCategory(): Map<FileCategory, Long>
}

/** Persists the LAST completed scan; source of truth for Results/Cleanup. */
interface ScanResultRepository {
    fun observe(): Flow<ScanResult?>
    fun current(): ScanResult?
    suspend fun save(result: ScanResult)
    suspend fun clear()
    /** Recompute groups after files were removed (drops emptied/singleton groups). */
    suspend fun removeFiles(fileIds: Set<Long>)
}

/** DataStore-backed device storage snapshot. */
interface StorageStatsProvider {
    suspend fun current(): StorageInfo
}

/** Daily file-count quota (ARCHITECTURE §10). */
interface QuotaRepository {
    fun observe(): Flow<QuotaInfo>
    suspend fun current(): QuotaInfo
    suspend fun addUsage(files: Int)
    /** Rewarded-ad top-up. Written synchronously before UI updates. */
    suspend fun addBonus(files: Int)
}

/** User settings + one-time flags (ARCHITECTURE §8, DataStore). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setPremium(premium: Boolean)
    suspend fun setArchiveMode(enabled: Boolean)
    suspend fun setAutoCleanup(enabled: Boolean)
    suspend fun setLanguage(tag: String)
    suspend fun setScanFolders(paths: Set<String>)
    suspend fun setExcludeFolders(paths: Set<String>)
    suspend fun setScanSchedule(schedule: ScanSchedule)
    suspend fun setArchiveTreeUri(uri: String?)
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val isPremium: Boolean = false,
    val archiveMode: Boolean = false,
    val autoCleanup: Boolean = false,
    val languageTag: String = "en",
    /** Restrict scans to these absolute folder paths; empty = whole device. */
    val scanFolders: Set<String> = emptySet(),
    /** Absolute folder paths skipped during scans (in addition to built-in exclusions). */
    val excludeFolders: Set<String> = emptySet(),
    val scanSchedule: ScanSchedule = ScanSchedule.OFF,
    /** SAF tree URI where archive zips are written (Archive mode). */
    val archiveTreeUri: String? = null
)

/** Recycle bin move/restore/purge (ARCHITECTURE §7). */
interface RecycleBinRepository {
    fun observe(): Flow<List<BinEntry>>
    suspend fun restore(id: Long): Result<Unit, AppError.Delete>
    suspend fun deleteForever(id: Long): Result<Unit, AppError.Delete>
    suspend fun emptyBin()
    suspend fun purgeExpired()
    suspend fun totalBytes(): Long
}

/** Outcome of a deletion batch. */
data class DeletionOutcome(
    val deleted: Int,
    val freedBytes: Long,
    val skipped: List<SkippedFile>,
    /** Ids of the files that were actually removed — skips leave no id here, so
     *  result-sync must use this rather than assuming the first N succeeded. */
    val deletedIds: Set<Long> = emptySet()
)

data class SkippedFile(val displayName: String, val reason: AppError.Delete)

/** Executes deletion (move-to-bin or archive) with per-file safety (ARCHITECTURE §7.4). */
interface DeletionEngine {
    suspend fun delete(
        files: List<FileItem>,
        archive: Boolean,
        /** SAF tree URI to write the zip into (Archive mode); null = app folder fallback. */
        archiveTreeUri: String?,
        onProgress: (deleted: Int, total: Int, freed: Long, current: String) -> Unit
    ): Result<DeletionOutcome, AppError.Delete>
}
