package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.repository.AppSettings
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.RecycleBinRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    bin: RecycleBinRepository,
    private val enumerator: FileEnumerator
) : ViewModel() {

    data class State(val settings: AppSettings = AppSettings(), val bin: List<BinEntry> = emptyList())

    val state = combine(settings.settings, bin.observe()) { s, b -> State(s, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun setScanFolders(paths: Set<String>) = viewModelScope.launch {
        settings.setScanFolders(paths)
        enumerator.invalidateSizes() // sizes on the Scan tab follow the scan scope
    }

    fun setExcludeFolders(paths: Set<String>) = viewModelScope.launch {
        settings.setExcludeFolders(paths)
        enumerator.invalidateSizes()
    }

    fun setSkipHidden(skip: Boolean) = viewModelScope.launch {
        settings.setSkipHidden(skip)
        enumerator.invalidateSizes()
    }

    fun setScanSchedule(schedule: ScanSchedule) = viewModelScope.launch { settings.setScanSchedule(schedule) }

    /** Archiving is only ever ON together with a destination folder. */
    fun setArchive(treeUri: String?) = viewModelScope.launch {
        settings.setArchiveTreeUri(treeUri)
        settings.setArchiveMode(treeUri != null)
    }

    fun setArchiveMode(enabled: Boolean) = viewModelScope.launch { settings.setArchiveMode(enabled) }
    fun setLanguage(tag: String) = viewModelScope.launch { settings.setLanguage(tag) }
}
