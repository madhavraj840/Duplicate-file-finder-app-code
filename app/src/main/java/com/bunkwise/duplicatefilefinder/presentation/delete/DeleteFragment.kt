package com.bunkwise.duplicatefilefinder.presentation.delete

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.engage.Engagement
import com.bunkwise.duplicatefilefinder.presentation.common.TreeUris
import com.bunkwise.duplicatefilefinder.service.DeleteProgress
import com.bunkwise.duplicatefilefinder.service.DeleteStatus
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import kotlinx.coroutines.launch

/**
 * Progress and outcome of a cleanup. The work runs in a foreground service, so the
 * user may leave at any time; the result is also posted as a notification.
 */
class DeleteFragment : Fragment() {

    private val viewModel: DeleteViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private val archiving get() = requireContext().appContainer.pendingArchive

    private var archiveFolder by mutableStateOf("")
    private var busy by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        composeScreen {
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            val status by viewModel.status.collectAsStateWithLifecycle()
            BackHandler(onBack = ::done)
            LaunchedEffect(status) {
                if ((status as? DeleteStatus.Completed)?.archived == true) {
                    archiveFolder = viewModel.archiveFolderUri()?.let(TreeUris::displayName) ?: ""
                }
            }
            DeleteScreen(build(status, progress), onUndo = ::undo, onDone = ::done)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Restored after the process died: nothing is running any more.
        if (viewModel.status.value is DeleteStatus.Idle) findNavController().popBackStack()
    }

    private fun build(status: DeleteStatus, p: DeleteProgress): DeleteUi = when (status) {
        is DeleteStatus.Completed -> completed(status)
        is DeleteStatus.Failed -> DeleteUi.Done(
            title = getString(R.string.failed_title),
            body = getString(
                when (status.error) {
                    AppError.Delete.PERMISSION_DENIED -> R.string.error_permission
                    AppError.Delete.BIN_FULL -> R.string.error_space
                    AppError.Delete.ARCHIVE_FOLDER_MISSING -> R.string.error_archive_folder
                    else -> R.string.error_unknown
                }
            ),
            undoLabel = null, skipped = null, doneLabel = getString(R.string.done), busy = busy,
        )
        else -> DeleteUi.Running(
            title = getString(if (archiving) R.string.removing_archive else R.string.removing_bin),
            percent = if (p.total == 0) 0 else p.deleted * 100 / p.total,
            count = getString(R.string.progress_count, Formatters.count(p.deleted), Formatters.count(p.total)),
            current = p.currentName,
            note = getString(R.string.removing_note),
        )
    }

    private fun completed(status: DeleteStatus.Completed): DeleteUi.Done {
        val outcome = status.outcome
        val freed = Formatters.bytes(outcome.freedBytes)
        val skipped = outcome.skipped
        return DeleteUi.Done(
            title = resources.getQuantityString(
                if (status.archived) R.plurals.done_title_archive else R.plurals.done_title_bin, outcome.deleted, outcome.deleted,
            ),
            body = if (status.archived) getString(R.string.done_body_archive, freed, archiveFolder)
            else getString(R.string.done_body_bin, freed),
            undoLabel = if (!status.archived && outcome.deleted > 0) getString(R.string.undo) else null,
            skipped = if (skipped.isEmpty()) null else buildString {
                append(resources.getQuantityString(R.plurals.skipped_head, skipped.size, skipped.size))
                skipped.take(MAX_SKIPPED_LINES).forEach {
                    append('\n').append(getString(R.string.skipped_line, it.displayName, reason(it.reason)))
                }
                val more = skipped.size - MAX_SKIPPED_LINES
                if (more > 0) append('\n').append(getString(R.string.skipped_more, more))
            },
            doneLabel = getString(R.string.done),
            busy = busy,
        )
    }

    private fun undo() {
        val status = viewModel.status.value as? DeleteStatus.Completed ?: return
        busy = true
        val appContext = requireContext().applicationContext
        // The restore runs on the app scope, so leaving the screen can't cut it short.
        val work = viewModel.undo(status.outcome)
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = work.await()
            Toast.makeText(
                appContext, appContext.resources.getQuantityString(R.plurals.undo_result, restored, restored), Toast.LENGTH_SHORT
            ).show()
            leave()
        }
    }

    private fun reason(e: AppError.Delete): String = getString(
        when (e) {
            AppError.Delete.FILE_CHANGED -> R.string.reason_changed
            AppError.Delete.TARGET_MISSING -> R.string.reason_missing
            AppError.Delete.PERMISSION_DENIED, AppError.Delete.ARCHIVE_FOLDER_MISSING -> R.string.reason_permission
            AppError.Delete.BIN_FULL -> R.string.reason_space
            AppError.Delete.UNKNOWN -> R.string.reason_unknown
        }
    )

    /** Done after a clean-up that removed something: the moment for Play's review sheet. */
    private fun done() {
        val ok = (viewModel.status.value as? DeleteStatus.Completed)?.outcome?.deleted ?: 0
        if (ok > 0) activity?.let(Engagement::maybeAskReview)
        leave()
    }

    /** Back to Duplicates. A running cleanup keeps going in the background. */
    private fun leave() {
        if (viewModel.status.value !is DeleteStatus.Running) viewModel.consume()
        findNavController().popBackStack()
    }

    private companion object {
        const val MAX_SKIPPED_LINES = 5
    }
}
