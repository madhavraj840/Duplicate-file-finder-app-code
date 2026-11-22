package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.databinding.FragmentCleanupBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.SortMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.launch

/** The Duplicates tab: the last scan's groups, selection and the path to Review. */
class CleanupFragment : Fragment() {

    private var _binding: FragmentCleanupBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private lateinit var adapter: GroupAdapter

    /** Back closes the search field before leaving the tab. */
    private val closeSearchOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setSearchOpen(false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCleanupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = GroupAdapter(onToggle = selection::toggleGroup, onOpen = ::openGroup)
        binding.groupList.layoutManager = LinearLayoutManager(requireContext())
        binding.groupList.adapter = adapter
        binding.groupList.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
                isLastItemDecorated = false
            }
        )

        binding.filterButton.setOnClickListener { showFilterMenu() }
        binding.sortButton.setOnClickListener { showSortMenu() }
        binding.selectAllButton.setOnClickListener { onSelectAll() }
        binding.reviewButton.setOnClickListener {
            findNavController().navigate(R.id.reviewDeleteFragment, null, androidx.navigation.navOptions { launchSingleTop = true })
        }
        binding.emptyAction.setOnClickListener { (activity as? MainActivity)?.selectTab(R.id.homeFragment) }
        binding.searchButton.setOnClickListener { setSearchOpen(!binding.searchField.isVisible) }
        binding.searchField.doAfterTextChanged { selection.setSearchQuery(it?.toString().orEmpty()) }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, closeSearchOnBack)
        if (selection.state.value.query.isNotEmpty()) setSearchOpen(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect(::render)
            }
        }
    }

    private fun render(state: SharedSelectionViewModel.CleanupState) {
        val result = state.result
        val hasGroups = result != null && result.groupCount > 0

        binding.summary.isVisible = hasGroups
        binding.controls.isVisible = hasGroups
        binding.listDivider.isVisible = hasGroups
        binding.bottomBar.isVisible = hasGroups
        binding.bottomDivider.isVisible = hasGroups
        binding.searchButton.isVisible = hasGroups

        if (result != null && hasGroups) {
            val ago = DateUtils.getRelativeTimeSpanString(
                result.finishedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
            binding.summary.text = getString(
                R.string.dup_summary, Formatters.bytes(result.wastedBytes), Formatters.count(result.groupCount), ago
            )
        }
        binding.filterButton.text = getString(R.string.filter_type, getString(CategoryUi.label(state.category)))
        binding.sortButton.text = getString(R.string.sort_label, getString(sortLabel(state.sort)))
        binding.selectAllButton.setText(if (state.markedFiles > 0) R.string.clear_selection else R.string.select_all)

        binding.selectedSummary.text = getString(
            R.string.selected_summary, Formatters.count(state.markedFiles), Formatters.bytes(state.markedBytes)
        )
        binding.reviewButton.isEnabled = state.markedFiles > 0

        // Empty states, most specific first.
        val empty = when {
            result == null -> Triple(R.string.empty_no_scan_title, getString(R.string.empty_no_scan_body), true)
            result.groupCount == 0 -> Triple(R.string.empty_clean_title, getString(R.string.empty_clean_body), true)
            state.groups.isEmpty() && state.query.isNotBlank() ->
                Triple(0, getString(R.string.empty_search, state.query.trim()), false)
            state.groups.isEmpty() -> Triple(0, getString(R.string.empty_filter), false)
            else -> null
        }
        binding.emptyState.isVisible = empty != null
        binding.groupList.isVisible = empty == null
        if (empty != null) {
            val (title, body, action) = empty
            binding.emptyTitle.isVisible = title != 0
            if (title != 0) binding.emptyTitle.setText(title)
            binding.emptyBody.text = body
            binding.emptyAction.isVisible = action
        }
        adapter.submitList(state.groups)
    }

    private fun onSelectAll() {
        val state = selection.state.value
        when {
            state.markedFiles > 0 -> selection.clearSelection()
            state.isPremium -> selection.selectAllVisible()
            else -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.premium_f_select_all)
                .setMessage(R.string.premium_select_all_body)
                .setPositiveButton(R.string.see_premium) { _, _ -> findNavController().navigate(R.id.premiumFragment) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showFilterMenu() {
        val counts = selection.state.value.typeCounts
        val options = listOf(FileCategory.ALL) + FILTER_TYPES.filter { (counts[it] ?: 0) > 0 }
        PopupMenu(requireContext(), binding.filterButton).apply {
            options.forEachIndexed { i, cat ->
                val label = getString(CategoryUi.label(cat))
                val title = if (cat == FileCategory.ALL) label else getString(R.string.filter_item, label, counts[cat] ?: 0)
                menu.add(0, i, i, title)
            }
            setOnMenuItemClickListener { selection.setCategory(options[it.itemId]); true }
            show()
        }
    }

    private fun showSortMenu() {
        val modes = SortMode.entries
        PopupMenu(requireContext(), binding.sortButton).apply {
            modes.forEachIndexed { i, mode -> menu.add(0, i, i, sortLabel(mode)) }
            setOnMenuItemClickListener { selection.setSort(modes[it.itemId]); true }
            show()
        }
    }

    private fun sortLabel(mode: SortMode) = when (mode) {
        SortMode.SIZE -> R.string.sort_size
        SortMode.NAME -> R.string.sort_name
        SortMode.COPIES -> R.string.sort_count
    }

    private fun openGroup(groupId: Long) {
        selection.previewGroupId = groupId
        findNavController().navigate(R.id.previewFragment, null, androidx.navigation.navOptions { launchSingleTop = true })
    }

    private fun setSearchOpen(open: Boolean) {
        binding.searchField.isVisible = open
        binding.title.isVisible = !open
        closeSearchOnBack.isEnabled = open
        binding.searchButton.setImageResource(if (open) R.drawable.ic_close else R.drawable.ic_search)
        binding.searchButton.contentDescription = getString(if (open) R.string.cd_close_search else R.string.cd_search)
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        if (open) {
            binding.searchField.requestFocus()
            imm?.showSoftInput(binding.searchField, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.searchField.setText("")
            imm?.hideSoftInputFromWindow(binding.searchField.windowToken, 0)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val FILTER_TYPES = listOf(
            FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO, FileCategory.DOCUMENTS, FileCategory.OTHER
        )
    }
}
