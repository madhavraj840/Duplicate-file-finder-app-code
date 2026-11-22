package com.bunkwise.duplicatefilefinder.presentation.reviewdelete

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.presentation.common.TreeUris
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.service.DeleteForegroundService

/** The last stop before files are removed: exactly which files, where they go, and what stays. */
class ReviewDeleteFragment : Fragment() {

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    /** Bumped on resume: returning from the All-files settings screen re-evaluates the blocker. */
    private var resumes by mutableIntStateOf(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        composeScreen {
            val state by selection.state.collectAsStateWithLifecycle()
            resumes // read, so a resume rebuilds the screen
            val ui = build(state)
            if (ui == null) {
                // Nothing left to remove (e.g. the selection was cleared elsewhere).
                androidx.compose.runtime.LaunchedEffect(Unit) { findNavController().popBackStack() }
                return@composeScreen
            }
            ReviewScreen(ui, onBack = { findNavController().popBackStack() }, onFixBlocker = ::fixBlocker, onConfirm = ::proceed)
        }

    private fun build(state: SharedSelectionViewModel.CleanupState): ReviewUi? {
        val files = selection.selectedFiles()
        if (files.isEmpty()) return null
        val total = Formatters.bytes(files.sumOf { it.sizeBytes })
        val folder = state.archiveTreeUri?.let(TreeUris::displayName)
        // Blockers: things that would make every file fail. Fix them here, not after.
        val blocker = blocker(state)
        return ReviewUi(
            files = files,
            archive = state.archiveMode,
            headline = resources.getQuantityString(
                if (state.archiveMode) R.plurals.review_head_archive else R.plurals.review_head_bin, files.size, files.size,
            ),
            note = if (state.archiveMode) getString(R.string.review_note_archive, total, folder ?: "")
            else getString(R.string.review_note_bin, total),
            kept = getString(R.string.review_kept, Formatters.count(selection.keptFiles().size)),
            confirm = getString(if (state.archiveMode) R.string.review_confirm_archive else R.string.review_confirm_bin),
            blocker = blocker?.let(::getString),
            blockerFixable = blocker != null && blocker != R.string.review_scan_running,
        )
    }

    private fun blocker(state: SharedSelectionViewModel.CleanupState): Int? = when {
        !Permissions.canRemoveFiles(requireContext()) -> R.string.review_need_access
        state.archiveMode && state.archiveTreeUri == null -> R.string.review_need_folder
        // A scan finishing mid-cleanup would bring the removed files back into the list.
        requireContext().appContainer.scanController.isRunning -> R.string.review_scan_running
        else -> null
    }

    private fun fixBlocker() {
        val blocker = blocker(selection.state.value) ?: return
        when {
            blocker != R.string.review_need_access -> (activity as? MainActivity)?.selectTab(R.id.settingsFragment)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Permissions.openAllFilesSettings(requireContext())
            // Android 10 and below: the WRITE grant lives on the app's settings page.
            else -> runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    private fun proceed() {
        val files = selection.selectedFiles()
        if (files.isEmpty()) return
        if (!DeleteForegroundService.start(requireContext(), files, selection.state.value.archiveMode)) return
        findNavController().navigate(
            R.id.deleteFragment, null,
            navOptions { popUpTo(R.id.reviewDeleteFragment) { inclusive = true } }
        )
    }
}
