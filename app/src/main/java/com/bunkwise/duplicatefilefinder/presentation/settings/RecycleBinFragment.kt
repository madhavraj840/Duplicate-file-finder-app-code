package com.bunkwise.duplicatefilefinder.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RecycleBinFragment : Fragment() {

    private val viewModel: RecycleBinViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        composeScreen {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Back first leaves selection mode, then the screen.
            BackHandler(enabled = state.selectionMode, onBack = viewModel::clearSelection)
            RecycleBinScreen(
                rows = state.entries.map { BinRow(it, viewModel.binFilePath(it), it.id in state.selected) },
                selectionMode = state.selectionMode,
                allSelected = state.allSelected,
                onBack = { findNavController().popBackStack() },
                onToggle = viewModel::toggleSelect,
                onOpen = ::openEntry,
                onHeaderAction = { if (state.selectionMode) viewModel.toggleSelectAll() else confirmEmpty(state) },
                onRestore = viewModel::restoreSelected,
                onDeleteSelected = ::confirmDeleteSelected,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect(::showMessage)
            }
        }
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
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
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
}
