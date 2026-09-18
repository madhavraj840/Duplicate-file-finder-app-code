package com.bunkwise.duplicatefilefinder.presentation.delete

import androidx.lifecycle.ViewModel
import com.bunkwise.duplicatefilefinder.service.DeleteController

class DeleteViewModel(
    private val deleteController: DeleteController
) : ViewModel() {
    val progress = deleteController.progress
    val status = deleteController.status
    fun consume() = deleteController.reset()
}
