package com.bunkwise.duplicatefilefinder.presentation.delete

import androidx.lifecycle.ViewModel
import com.bunkwise.duplicatefilefinder.core.data.RecycleBinRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionOutcome
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import com.bunkwise.duplicatefilefinder.di.AppContainer
import com.bunkwise.duplicatefilefinder.service.DeleteController
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class DeleteViewModel(
    private val deleteController: DeleteController,
    private val bin: RecycleBinRepositoryImpl,
    private val scanResults: ScanResultRepository,
    private val settings: SettingsRepository,
    private val container: AppContainer
) : ViewModel() {
    val progress = deleteController.progress
    val status = deleteController.status

    fun consume() = deleteController.reset()

    suspend fun archiveFolderUri(): String? = settings.current().archiveTreeUri

    /**
     * Undo a recycle-bin cleanup: restore the batch and put the restored files back
     * into the duplicate list (anything that couldn't be restored stays out).
     * Returns the number of files restored.
     */
    fun undo(outcome: DeletionOutcome): Deferred<Int> = container.appScope.async {
        val restoredPaths = bin.restoreBatch(outcome.batchId)
        // Only rewind the list if it is still the same scan; a newer scan must not be lost.
        container.resultBeforeDelete
            ?.takeIf { it.finishedAtMs == scanResults.current()?.finishedAtMs }
            ?.let { before ->
            scanResults.save(before)
            val notRestored = before.groups.flatMap { it.files }
                .filter { it.id in outcome.deletedIds && it.path !in restoredPaths }
                .mapTo(HashSet()) { it.id }
            if (notRestored.isNotEmpty()) scanResults.removeFiles(notRestored)
        }
        container.resultBeforeDelete = null
        container.fileEnumerator.invalidateSizes()
        restoredPaths.size
    }
}
