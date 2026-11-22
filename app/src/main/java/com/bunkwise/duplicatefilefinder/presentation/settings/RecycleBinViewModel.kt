package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.data.RecycleBinRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val bin: RecycleBinRepositoryImpl,
    private val enumerator: FileEnumerator
) : ViewModel() {

    data class BinUiState(
        val entries: List<BinEntry> = emptyList(),
        val selected: Set<Long> = emptySet()
    ) {
        val selectionMode: Boolean get() = selected.isNotEmpty()
        val allSelected: Boolean get() = entries.isNotEmpty() && selected.size == entries.size
    }

    /** One-off results to confirm to the user. */
    sealed interface BinMessage {
        data class Restored(val restored: Int, val failed: Int) : BinMessage
        data class Deleted(val count: Int) : BinMessage
        data object Emptied : BinMessage
    }

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _messages = Channel<BinMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val state: StateFlow<BinUiState> = combine(bin.observe(), selectedIds) { entries, sel ->
        // Drop selections whose entries are gone (restored/deleted/expired).
        BinUiState(entries.sortedByDescending { it.deletedAtMs }, sel.intersect(entries.map { it.id }.toSet()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BinUiState())

    fun binFilePath(entry: BinEntry): String = bin.binFilePath(entry)

    fun toggleSelect(id: Long) {
        selectedIds.value = selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun toggleSelectAll() {
        val s = state.value
        selectedIds.value = if (s.allSelected) emptySet() else s.entries.map { it.id }.toSet()
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun restoreSelected() = viewModelScope.launch {
        val ids = state.value.selected
        if (ids.isEmpty()) return@launch
        val restored = bin.restoreMany(ids)
        selectedIds.value = emptySet()
        enumerator.invalidateSizes()
        _messages.send(BinMessage.Restored(restored, ids.size - restored))
    }

    fun deleteSelectedForever() = viewModelScope.launch {
        val ids = state.value.selected
        if (ids.isEmpty()) return@launch
        bin.deleteManyForever(ids)
        selectedIds.value = emptySet()
        _messages.send(BinMessage.Deleted(ids.size))
    }

    fun emptyBin() = viewModelScope.launch {
        bin.emptyBin()
        selectedIds.value = emptySet()
        _messages.send(BinMessage.Emptied)
    }
}
