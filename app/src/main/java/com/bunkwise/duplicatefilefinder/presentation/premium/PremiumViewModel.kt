package com.bunkwise.duplicatefilefinder.presentation.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** v1: premium is a test toggle (no billing) per product spec (OTHER §3). */
class PremiumViewModel(
    private val settings: SettingsRepository
) : ViewModel() {

    val isPremium = settings.settings
        .map { it.isPremium }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPremium(enabled: Boolean) = viewModelScope.launch { settings.setPremium(enabled) }
}
