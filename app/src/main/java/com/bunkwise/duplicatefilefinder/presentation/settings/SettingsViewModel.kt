package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.repository.AppSettings
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository
) : ViewModel() {

    val state = settings.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    fun setArchiveMode(enabled: Boolean) = viewModelScope.launch { settings.setArchiveMode(enabled) }
    fun setAutoCleanup(enabled: Boolean) = viewModelScope.launch { settings.setAutoCleanup(enabled) }
    fun setScanFolders(paths: Set<String>) = viewModelScope.launch { settings.setScanFolders(paths) }
    fun setExcludeFolders(paths: Set<String>) = viewModelScope.launch { settings.setExcludeFolders(paths) }
    fun setScanSchedule(schedule: ScanSchedule) = viewModelScope.launch { settings.setScanSchedule(schedule) }
    fun setArchiveTreeUri(uri: String?) = viewModelScope.launch { settings.setArchiveTreeUri(uri) }
    fun setLanguage(tag: String) = viewModelScope.launch { settings.setLanguage(tag) }
}
