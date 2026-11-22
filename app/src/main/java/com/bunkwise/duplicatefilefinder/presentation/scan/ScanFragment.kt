package com.bunkwise.duplicatefilefinder.presentation.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import com.bunkwise.duplicatefilefinder.service.ScanForegroundService
import com.bunkwise.duplicatefilefinder.service.ScanStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

/**
 * Progress of a running scan. The scan lives in a foreground service, so leaving
 * this screen (back, or "Continue in background") never stops it; Stop does.
 */
class ScanFragment : Fragment() {

    private val viewModel: ScanViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private var handledTerminal = false
    private var scope by mutableStateOf("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        composeScreen {
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            ScanScreen(
                progress = progress,
                scope = scope,
                onBack = { findNavController().popBackStack() },
                onStop = ::confirmStop,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Restored after the process died: the scan died with it. Don't show a
        // spinner that will never finish.
        if (!viewModel.isRunning && viewModel.status.value is ScanStatus.Idle) {
            findNavController().popBackStack()
            return
        }

        showScanScope()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.status.collect(::handleStatus)
            }
        }
    }

    private fun handleStatus(status: ScanStatus) {
        when (status) {
            is ScanStatus.Completed -> finish {
                // Straight to the result; the Scan tab stays underneath for "back".
                findNavController().popBackStack()
                (activity as? MainActivity)?.selectTab(R.id.cleanupFragment)
            }
            // Home shows the "scan failed" message once it is visible again.
            is ScanStatus.Failed -> finish { findNavController().popBackStack() }
            is ScanStatus.Stopped -> finish { findNavController().popBackStack() }
            is ScanStatus.Running, ScanStatus.Idle -> Unit
        }
    }

    private inline fun finish(action: () -> Unit) {
        if (handledTerminal) return
        handledTerminal = true
        viewModel.consumeTerminalStatus()
        action()
    }

    private fun confirmStop() {
        if (handledTerminal) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stop_title)
            .setMessage(R.string.stop_message)
            .setNegativeButton(R.string.keep_scanning, null)
            .setPositiveButton(R.string.stop) { _, _ -> ScanForegroundService.stop(requireContext()) }
            .show()
    }

    /** Whole device, or the one folder chosen in Settings. */
    private fun showScanScope() {
        viewLifecycleOwner.lifecycleScope.launch {
            val folder = runCatching {
                requireContext().appContainer.settingsRepository.current().scanFolders.firstOrNull()
            }.getOrNull()
            if (view == null) return@launch
            scope = if (folder == null) getString(R.string.scan_scope_device)
            else getString(R.string.scan_scope_folder, File(folder).name)
        }
    }
}
