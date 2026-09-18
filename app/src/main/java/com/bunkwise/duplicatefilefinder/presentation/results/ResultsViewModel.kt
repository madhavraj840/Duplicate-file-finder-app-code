package com.bunkwise.duplicatefilefinder.presentation.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ResultsViewModel(
    scanRepo: ScanResultRepository,
    settings: SettingsRepository
) : ViewModel() {

    data class ResultsState(
        val result: ScanResult? = null,
        val isPremium: Boolean = false
    )

    val state = combine(scanRepo.observe(), settings.settings) { result, set ->
        ResultsState(result = result, isPremium = set.isPremium)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultsState())
}
