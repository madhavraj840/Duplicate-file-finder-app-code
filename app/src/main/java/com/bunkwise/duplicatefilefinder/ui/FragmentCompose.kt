package com.bunkwise.duplicatefilefinder.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

/** A Fragment's whole UI as one Compose screen inside [DfTheme]. Call from onCreateView. */
fun Fragment.composeScreen(content: @Composable () -> Unit): View =
    ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { DfTheme(content) }
    }
