package com.bunkwise.duplicatefilefinder.presentation.reviewdelete

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.databinding.FragmentReviewdeleteBinding
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.service.DeleteForegroundService
import kotlinx.coroutines.launch

class ReviewDeleteFragment : Fragment() {

    private var _binding: FragmentReviewdeleteBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewdeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.reviewAgainButton.setOnClickListener { findNavController().popBackStack() }
        binding.proceedButton.setOnClickListener { proceed() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect(::render)
            }
        }
        renderTotals()
    }

    private fun render(state: SharedSelectionViewModel.CleanupState) {
        renderTotals()
        if (state.archiveMode) {
            binding.bannerText.setText(R.string.archive_note)
            binding.proceedButton.setText(R.string.proceed_to_archive)
        } else {
            binding.bannerText.setText(R.string.bin_recover_note)
            binding.proceedButton.setText(R.string.proceed_to_delete)
        }
    }

    private fun renderTotals() {
        val deleted = selection.selectedFiles()
        val kept = selection.keptFiles()
        binding.deletedSize.text = Formatters.bytes(deleted.sumOf { it.sizeBytes })
        binding.deletedCount.text = Formatters.count(deleted.size)
        binding.keptSize.text = Formatters.bytes(kept.sumOf { it.sizeBytes })
        binding.keptCount.text = Formatters.count(kept.size)
    }

    private fun proceed() {
        val files = selection.selectedFiles()
        if (files.isEmpty()) { findNavController().popBackStack(); return }
        val container = requireContext().appContainer
        container.pendingDeletionFiles = files
        container.pendingArchive = selection.state.value.archiveMode
        DeleteForegroundService.start(requireContext())
        findNavController().navigate(
            R.id.deleteFragment, null,
            androidx.navigation.navOptions { popUpTo(R.id.reviewDeleteFragment) { inclusive = true } }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
