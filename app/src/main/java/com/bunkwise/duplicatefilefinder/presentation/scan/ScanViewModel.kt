package com.bunkwise.duplicatefilefinder.presentation.scan

import androidx.lifecycle.ViewModel
import com.bunkwise.duplicatefilefinder.service.ScanController
import com.bunkwise.duplicatefilefinder.service.ScanStatus
import kotlinx.coroutines.flow.StateFlow

/** The Scan overlay only observes the service-owned controller (single writer). */
class ScanViewModel(
    private val scanController: ScanController
) : ViewModel() {

    val progress = scanController.progress
    val status: StateFlow<ScanStatus> = scanController.status

    fun consumeTerminalStatus() = scanController.reset()
}
