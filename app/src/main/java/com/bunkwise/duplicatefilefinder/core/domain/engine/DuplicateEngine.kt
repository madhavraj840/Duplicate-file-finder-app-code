package com.bunkwise.duplicatefilefinder.core.domain.engine

import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult

/** Parameters for a single scan run. */
data class ScanRequest(
    val mode: ScanMode,
    val categories: Set<FileCategory>,
    /** Hard cap on files to enumerate (quota); [Int.MAX_VALUE] = unlimited. */
    val fileBudget: Int = Int.MAX_VALUE
)

/**
 * The duplicate-detection engine contract (ARCHITECTURE §5). Implementations are
 * pure of Android UI. The engine owns no threads of its own beyond the caller's
 * coroutine context; cancellation is cooperative via the calling scope.
 */
interface DuplicateEngine {
    /**
     * Runs the full multi-stage pipeline. [onProgress] is invoked from the engine's
     * single collector coroutine (already conflated by the caller if needed).
     * Returns the grouped result, or a typed [AppError.Scan] for expected failures.
     * Throws only [kotlinx.coroutines.CancellationException] on cancellation.
     */
    suspend fun scan(
        request: ScanRequest,
        onProgress: (ScanProgress) -> Unit
    ): Result<ScanResult, AppError.Scan>
}
