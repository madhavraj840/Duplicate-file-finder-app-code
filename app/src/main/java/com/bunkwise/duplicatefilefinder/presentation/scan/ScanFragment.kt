package com.bunkwise.duplicatefilefinder.presentation.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.databinding.FragmentScanBinding
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

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private var handledTerminal = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Restored after the process died: the scan died with it. Don't show a
        // spinner that will never finish.
        if (!viewModel.isRunning && viewModel.status.value is ScanStatus.Idle) {
            findNavController().popBackStack()
            return
        }

        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.backgroundButton.setOnClickListener { findNavController().popBackStack() }
        binding.stopButton.setOnClickListener { confirmStop() }
        showScanScope()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.progress.collect(::renderProgress) }
                launch { viewModel.status.collect(::handleStatus) }
            }
        }
    }

    private fun renderProgress(p: ScanProgress) {
        val listing = p.phase == ScanPhase.ENUMERATING && p.percent <= 5
        if (binding.progress.isIndeterminate != listing) {
            // Switching mode requires the indicator to be hidden first.
            binding.progress.visibility = View.INVISIBLE
            binding.progress.isIndeterminate = listing
            binding.progress.visibility = View.VISIBLE
        }
        if (!listing) binding.progress.setProgressCompat(p.percent, true)
        binding.percentText.text = if (listing) "" else "${p.percent}%"
        binding.phaseText.setText(
            when (p.phase) {
                ScanPhase.ENUMERATING -> R.string.phase_listing
                ScanPhase.HASHING -> R.string.phase_hashing
                ScanPhase.COMPARING -> R.string.phase_comparing
                ScanPhase.GROUPING, ScanPhase.DONE -> R.string.phase_grouping
            }
        )
        binding.filesScanned.text = Formatters.count(p.filesScanned)
        binding.duplicatesFound.text = Formatters.count(p.duplicatesFound)
        binding.timeElapsed.text = Formatters.duration(p.elapsedMs)
        binding.currentPath.text = p.currentPath
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
            val binding = _binding ?: return@launch
            binding.scanScope.text = if (folder == null) getString(R.string.scan_scope_device)
            else getString(R.string.scan_scope_folder, File(folder).name)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
