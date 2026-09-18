package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.data.RecycleBinRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val bin: RecycleBinRepositoryImpl
) : ViewModel() {

    data class BinUiState(
        val entries: List<BinEntry> = emptyList(),
        val selected: Set<Long> = emptySet()
    ) {
        val selectionMode: Boolean get() = selected.isNotEmpty()
        val allSelected: Boolean get() = entries.isNotEmpty() && selected.size == entries.size
    }

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<BinUiState> = combine(bin.observe(), selectedIds) { entries, sel ->
        // Drop selections whose entries are gone (restored/deleted).
        val valid = sel.intersect(entries.map { it.id }.toSet())
        BinUiState(entries, valid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BinUiState())

    fun binFilePath(entry: BinEntry): String = bin.binFilePath(entry)

    fun toggleSelect(id: Long) {
        selectedIds.value = selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun selectAll() {
        val entries = state.value.entries
        selectedIds.value = if (state.value.allSelected) emptySet() else entries.map { it.id }.toSet()
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun restoreSelected() = viewModelScope.launch {
        val ids = selectedIds.value
        if (ids.isEmpty()) return@launch
        bin.restoreMany(ids)
        selectedIds.value = emptySet()
    }

    fun deleteSelectedForever() = viewModelScope.launch {
        val ids = selectedIds.value
        ids.forEach { bin.deleteForever(it) }
        selectedIds.value = emptySet()
    }

    fun restore(id: Long) = viewModelScope.launch { bin.restore(id) }
    fun deleteForever(id: Long) = viewModelScope.launch { bin.deleteForever(id) }
    fun emptyBin() = viewModelScope.launch { bin.emptyBin() }
}
