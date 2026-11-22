package com.bunkwise.duplicatefilefinder.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.databinding.FragmentRecyclebinBinding
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RecycleBinFragment : Fragment() {

    private var _binding: FragmentRecyclebinBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecycleBinViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private lateinit var adapter: BinAdapter

    /** Back first leaves selection mode, then the screen. */
    private val clearSelectionOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.clearSelection()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecyclebinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = BinAdapter(
            onToggle = viewModel::toggleSelect,
            onOpen = ::openEntry,
            selectionMode = { viewModel.state.value.selectionMode }
        )
        binding.binList.layoutManager = LinearLayoutManager(requireContext())
        binding.binList.adapter = adapter
        binding.binList.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
                isLastItemDecorated = false
            }
        )

        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.restoreSelectedButton.setOnClickListener { viewModel.restoreSelected() }
        binding.deleteSelectedButton.setOnClickListener { confirmDeleteSelected() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, clearSelectionOnBack)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.messages.collect(::showMessage) }
            }
        }
    }

    private fun render(state: RecycleBinViewModel.BinUiState) {
        adapter.submitList(state.entries.map {
            BinAdapter.Row(it, viewModel.binFilePath(it), it.id in state.selected)
        })
        val empty = state.entries.isEmpty()
        binding.emptyState.isVisible = empty
        binding.binList.isVisible = !empty
        binding.summaryBlock.isVisible = !empty
        binding.listDivider.isVisible = !empty
        binding.summary.text = resources.getQuantityString(
            R.plurals.bin_summary, state.entries.size, state.entries.size, Formatters.bytes(state.entries.sumOf { it.sizeBytes })
        )

        // Header action: "Empty bin" normally, "Select all / Clear" while selecting.
        binding.headerAction.isVisible = !empty
        if (state.selectionMode) {
            binding.headerAction.setText(if (state.allSelected) R.string.clear_selection else R.string.select_all)
            binding.headerAction.setTextColor(requireContext().getColor(R.color.primary))
            binding.headerAction.setOnClickListener { viewModel.toggleSelectAll() }
        } else {
            binding.headerAction.setText(R.string.empty_bin)
            binding.headerAction.setTextColor(requireContext().getColor(R.color.danger))
            binding.headerAction.setOnClickListener { confirmEmpty(state) }
        }

        binding.actionBar.isVisible = state.selectionMode
        binding.actionDivider.isVisible = state.selectionMode
        binding.selectedCount.text = getString(R.string.n_selected, state.selected.size)
        clearSelectionOnBack.isEnabled = state.selectionMode
    }

    private fun showMessage(message: RecycleBinViewModel.BinMessage) {
        val text = when (message) {
            is RecycleBinViewModel.BinMessage.Restored -> buildString {
                append(resources.getQuantityString(R.plurals.restored, message.restored, message.restored))
                if (message.failed > 0) {
                    append(". ").append(resources.getQuantityString(R.plurals.restore_failed, message.failed, message.failed))
                }
            }
            is RecycleBinViewModel.BinMessage.Deleted ->
                resources.getQuantityString(R.plurals.deleted_permanently, message.count, message.count)
            RecycleBinViewModel.BinMessage.Emptied -> getString(R.string.bin_emptied)
        }
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    private fun openEntry(entry: BinEntry) =
        FileOpener.open(requireContext(), viewModel.binFilePath(entry), null)

    private fun confirmDeleteSelected() {
        val count = viewModel.state.value.selected.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.delete_selected_title, count, count))
            .setMessage(R.string.cannot_undo)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_permanently) { _, _ -> viewModel.deleteSelectedForever() }
            .show()
    }

    private fun confirmEmpty(state: RecycleBinViewModel.BinUiState) {
        val count = state.entries.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.empty_bin_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.empty_bin_body, count, count, Formatters.bytes(state.entries.sumOf { it.sizeBytes })
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.empty_bin) { _, _ -> viewModel.emptyBin() }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
