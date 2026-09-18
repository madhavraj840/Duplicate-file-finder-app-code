package com.bunkwise.duplicatefilefinder.presentation.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.databinding.FragmentScanBinding
import com.bunkwise.duplicatefilefinder.service.ScanForegroundService
import com.bunkwise.duplicatefilefinder.service.ScanStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

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
        binding.scanRing.useGradient = true
        binding.backButton.setOnClickListener { confirmStop() }
        binding.stopButton.setOnClickListener { confirmStop() }

        showScanScope()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = confirmStop()
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.progress.collect(::renderProgress) }
                launch { viewModel.status.collect(::handleStatus) }
            }
        }
    }

    private fun renderProgress(p: ScanProgress) {
        // No meaningful percent while enumerating - show a rotating sweep instead
        // of a ring frozen at 0%, then ease between values once real progress lands.
        val spinning = p.phase == ScanPhase.ENUMERATING && p.percent <= 0
        binding.scanRing.setIndeterminate(spinning)
        if (!spinning) binding.scanRing.animateTo(p.percent.toFloat())
        binding.filesScanned.text = Formatters.count(p.filesScanned)
        binding.duplicatesFound.text = Formatters.count(p.duplicatesFound)
        binding.timeElapsed.text = Formatters.duration(p.elapsedMs)
        if (p.currentPath.isNotEmpty()) binding.currentPath.text = p.currentPath
    }

    private fun handleStatus(status: ScanStatus) {
        when (status) {
            is ScanStatus.Running -> binding.scanTitle.text = titleFor(status.mode)
            is ScanStatus.Completed -> finish { navigateToResults() }
            is ScanStatus.Failed -> finish {
                Toast.makeText(requireContext(), R.string.scan_failed, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            is ScanStatus.Stopped -> finish { findNavController().popBackStack() }
            is ScanStatus.Idle -> Unit
        }
    }

    private inline fun finish(action: () -> Unit) {
        if (handledTerminal) return
        handledTerminal = true
        viewModel.consumeTerminalStatus()
        action()
    }

    private fun navigateToResults() {
        // Tell Results to celebrate this fresh completion (consumed once, there).
        requireContext().appContainer.pendingScanCelebration = true
        findNavController().navigate(
            R.id.resultsFragment, null,
            navOptions { popUpTo(R.id.scanFragment) { inclusive = true } }
        )
    }

    private fun confirmStop() {
        if (handledTerminal) return
        // Always confirm before stopping a running scan, regardless of progress or
        // tier. The old percent-based branch (>30% asked, otherwise stopped silently)
        // made the button behave inconsistently — it looked like a free-vs-premium
        // difference but was really just how far the scan had gotten.
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stop_confirm_title)
            .setMessage(R.string.stop_confirm_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.stop_confirm_yes) { _, _ -> stopScan() }
            .show()
    }

    private fun stopScan() = ScanForegroundService.stop(requireContext())

    /** Tells the user WHAT is being scanned: the whole device, or the N picked folders. */
    private fun showScanScope() {
        viewLifecycleOwner.lifecycleScope.launch {
            val folders = runCatching {
                requireContext().appContainer.settingsRepository.current().scanFolders
            }.getOrDefault(emptySet())
            val binding = _binding ?: return@launch
            binding.scanScope.text = if (folders.isEmpty()) {
                getString(R.string.scan_scope_device)
            } else {
                resources.getQuantityString(R.plurals.scan_scope_folders, folders.size, folders.size)
            }
        }
    }

    private fun titleFor(mode: ScanMode): CharSequence = when (mode) {
        ScanMode.EXACT -> getString(R.string.scan_title_exact)
        ScanMode.SIMILAR -> getString(R.string.mode_similar_short)
        ScanMode.VERY_SIMILAR -> getString(R.string.mode_very_similar_short)
        ScanMode.DEEP -> getString(R.string.mode_deep_short)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
