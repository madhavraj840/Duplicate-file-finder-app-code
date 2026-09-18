package com.bunkwise.duplicatefilefinder.presentation.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.designsystem.DonutChartView
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.databinding.FragmentResultsBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemLegendRowBinding
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultsViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private val selection: SharedSelectionViewModel by activityViewModels { requireContext().appContainer.viewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.reviewButton.setOnClickListener {
            findNavController().navigate(
                R.id.cleanupFragment, null,
                androidx.navigation.navOptions {
                    popUpTo(R.id.homeFragment) { inclusive = false }
                    launchSingleTop = true
                }
            )
        }
        binding.goHomeButton.setOnClickListener {
            findNavController().navigate(
                R.id.homeFragment, null,
                androidx.navigation.navOptions {
                    popUpTo(R.id.homeFragment) { inclusive = true }
                    launchSingleTop = true
                }
            )
        }
        binding.smartCleanupButton.setOnClickListener { onSmartCleanup() }

        val container = requireContext().appContainer
        if (container.pendingScanCelebration) {
            container.pendingScanCelebration = false
            binding.confetti.start()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ResultsViewModel.ResultsState) {
        val result = state.result
        val hasData = result != null && result.groupCount > 0
        binding.scroll.isVisible = hasData
        binding.emptyState.isVisible = !hasData

        if (result != null && result.groupCount == 0) {
            // Zero-duplicates celebratory state.
            binding.emptyIcon.setImageResource(R.drawable.ic_check_circle)
            binding.emptyText.setText(R.string.zero_dupes_title)
        } else {
            binding.emptyIcon.setImageResource(R.drawable.ic_search)
            binding.emptyText.setText(R.string.empty_results_title)
        }
        if (!hasData) return

        binding.totalSize.text = Formatters.bytes(result!!.wastedBytes)
        binding.fileCount.text = getString(R.string.files_count, Formatters.count(result.totalDuplicateFiles))

        val byCat = result.wastedByCategory().entries.sortedByDescending { it.value }
        binding.donut.setSlices(byCat.map {
            DonutChartView.Slice(it.value.toFloat(), ContextCompat.getColor(requireContext(), colorFor(it.key)))
        })

        binding.legend.removeAllViews()
        for (entry in byCat) {
            val row = ItemLegendRowBinding.inflate(layoutInflater, binding.legend, false)
            row.dot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), colorFor(entry.key))
            row.legendName.setText(nameFor(entry.key))
            row.legendSize.text = Formatters.bytes(entry.value)
            binding.legend.addView(row.root)
        }
    }

    private fun onSmartCleanup() {
        if (viewModel.state.value.isPremium) {
            selection.selectAllGroups()
            findNavController().navigate(R.id.reviewDeleteFragment)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.smart_cleanup)
                .setMessage(R.string.premium_feature_smart)
                .setPositiveButton(R.string.premium_title) { _, _ -> findNavController().navigate(R.id.premiumFragment) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun colorFor(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.color.cat_images
        FileCategory.VIDEOS -> R.color.cat_videos
        FileCategory.DOCUMENTS -> R.color.cat_documents
        FileCategory.AUDIO -> R.color.cat_audio
        else -> R.color.cat_others
    }

    private fun nameFor(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.string.cat_images
        FileCategory.VIDEOS -> R.string.cat_videos
        FileCategory.DOCUMENTS -> R.string.cat_documents
        FileCategory.AUDIO -> R.string.cat_audio
        else -> R.string.cat_others
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
