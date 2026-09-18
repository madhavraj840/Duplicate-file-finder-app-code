package com.bunkwise.duplicatefilefinder.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.databinding.FragmentRecyclebinBinding
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RecycleBinFragment : Fragment() {

    private var _binding: FragmentRecyclebinBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecycleBinViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private lateinit var adapter: BinAdapter

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

        binding.backButton.setOnClickListener { onBack() }
        binding.emptyBinButton.setOnClickListener { confirmEmpty() }
        binding.selectAllButton.setOnClickListener { viewModel.selectAll() }
        binding.restoreSelectedButton.setOnClickListener { viewModel.restoreSelected() }
        binding.deleteSelectedButton.setOnClickListener { confirmDeleteSelected() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: RecycleBinViewModel.BinUiState) {
        adapter.submitList(state.entries.map {
            BinAdapter.Row(it, viewModel.binFilePath(it), it.id in state.selected)
        })
        binding.emptyText.isVisible = state.entries.isEmpty()
        binding.emptyBinButton.isVisible = state.entries.isNotEmpty() && !state.selectionMode
        binding.selectAllButton.isVisible = state.entries.isNotEmpty()
        binding.selectAllButton.setText(if (state.allSelected) R.string.select_none else R.string.select_all)

        binding.actionBar.isVisible = state.selectionMode
        binding.selectedCount.text = getString(R.string.n_selected, state.selected.size)
    }

    private fun onBack() {
        if (viewModel.state.value.selectionMode) viewModel.clearSelection()
        else findNavController().popBackStack()
    }

    private fun openEntry(entry: BinEntry) =
        FileOpener.open(requireContext(), viewModel.binFilePath(entry), null)

    private fun confirmDeleteSelected() {
        val count = viewModel.state.value.selected.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_forever)
            .setMessage(getString(R.string.delete_selected_msg, count))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_forever) { _, _ -> viewModel.deleteSelectedForever() }
            .show()
    }

    private fun confirmEmpty() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.empty_bin)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.empty_bin) { _, _ -> viewModel.emptyBin() }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
