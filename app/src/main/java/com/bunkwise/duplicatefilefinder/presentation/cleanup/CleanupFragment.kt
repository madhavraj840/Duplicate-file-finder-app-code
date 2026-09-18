package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.databinding.FragmentCleanupBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemFilterChipBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.SortMode
import kotlinx.coroutines.launch

class CleanupFragment : Fragment() {

    private var _binding: FragmentCleanupBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private lateinit var adapter: GroupAdapter
    private val filterChips = mutableMapOf<FileCategory, ItemFilterChipBinding>()

    private val filterCategories = listOf(
        FileCategory.ALL, FileCategory.IMAGES, FileCategory.VIDEOS,
        FileCategory.DOCUMENTS, FileCategory.AUDIO, FileCategory.OTHER
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCleanupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = GroupAdapter(
            onToggle = selection::toggleGroup,
            onPreview = ::openPreview,
            onOpenFile = ::openFile
        )
        binding.groupList.layoutManager = LinearLayoutManager(requireContext())
        binding.groupList.adapter = adapter

        buildFilterChips()

        binding.sortSize.setOnClickListener { selection.setSort(SortMode.SIZE) }
        binding.sortName.setOnClickListener { selection.setSort(SortMode.NAME) }
        binding.sortDup.setOnClickListener { selection.setSort(SortMode.DUPLICATES) }
        binding.sortDirection.setOnClickListener { selection.toggleDirection() }

        binding.reviewDeleteButton.setOnClickListener {
            findNavController().navigate(
                R.id.reviewDeleteFragment, null,
                androidx.navigation.navOptions { launchSingleTop = true }
            )
        }

        binding.searchButton.setOnClickListener { toggleSearch() }
        binding.searchField.doAfterTextChanged { selection.setSearchQuery(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect(::render)
            }
        }
    }

    private fun buildFilterChips() {
        binding.categoryChips.removeAllViews()
        filterChips.clear()
        for (cat in filterCategories) {
            val chip = ItemFilterChipBinding.inflate(layoutInflater, binding.categoryChips, false)
            chip.chipIcon.setImageResource(CategoryUi.icon(cat))
            chip.chipLabel.setText(CategoryUi.label(cat))
            chip.root.setOnClickListener { selection.setCategory(cat) }
            binding.categoryChips.addView(chip.root)
            filterChips[cat] = chip
        }
    }

    private fun render(state: SharedSelectionViewModel.CleanupState) {
        binding.statTotalDup.text = Formatters.count(state.totalDuplicateFiles)
        binding.statDupSize.text = Formatters.bytes(state.duplicateBytes)
        binding.statDupPercent.text = getString(R.string.of_total_space, "${state.percentOfTotal}%")
        binding.statGroups.text = state.groupCount.toString()
        binding.statMarked.text = Formatters.bytes(state.markedBytes)
        binding.statMarkedFiles.text = getString(R.string.n_files_selected_stat, Formatters.count(state.markedFiles))

        binding.selectedCount.text = getString(R.string.n_files_selected, Formatters.count(state.markedFiles))
        binding.selectedSize.text = Formatters.bytes(state.markedBytes)
        binding.reviewDeleteButton.isEnabled = state.markedFiles > 0

        // Filter chip highlight
        for ((cat, chip) in filterChips) {
            val active = cat == state.category
            chip.root.setBackgroundResource(if (active) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            val tint = ContextCompat.getColor(requireContext(), if (active) R.color.primary else R.color.text_secondary)
            chip.chipLabel.setTextColor(tint)
            chip.chipIcon.setColorFilter(tint)
        }

        highlightSort(state.sort)

        binding.groupList.isVisible = state.groups.isNotEmpty()
        binding.emptyText.isVisible = state.hasResult && state.groups.isEmpty()
        binding.emptyText.setText(
            if (state.query.isNotBlank()) R.string.search_no_results else R.string.empty_results_title
        )
        adapter.submitList(state.groups)
    }

    private fun highlightSort(sort: SortMode) {
        val map = mapOf(
            SortMode.SIZE to binding.sortSize,
            SortMode.NAME to binding.sortName,
            SortMode.DUPLICATES to binding.sortDup
        )
        for ((mode, tv) in map) {
            val active = mode == sort
            (tv as TextView).setBackgroundResource(if (active) R.drawable.bg_chip_selected else 0)
            tv.setTextColor(
                ContextCompat.getColor(requireContext(), if (active) R.color.primary else R.color.text_secondary)
            )
        }
    }

    private fun openPreview(groupId: Long) {
        selection.previewGroupId = groupId
        findNavController().navigate(
            R.id.previewFragment, null,
            androidx.navigation.navOptions { launchSingleTop = true }
        )
    }

    /** Let users verify a file with their own eyes before deleting (DESIGN §3.5). */
    private fun openFile(file: FileItem) = FileOpener.open(requireContext(), file.path, file.mime)

    private fun toggleSearch() {
        val show = !binding.searchField.isVisible
        binding.searchField.isVisible = show
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        if (show) {
            binding.searchField.requestFocus()
            imm?.showSoftInput(binding.searchField, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.searchField.setText("")
            imm?.hideSoftInputFromWindow(binding.searchField.windowToken, 0)
        }
    }

    override fun onDestroyView() {
        _binding = null
        filterChips.clear()
        super.onDestroyView()
    }
}
