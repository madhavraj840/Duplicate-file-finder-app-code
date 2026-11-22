package com.bunkwise.duplicatefilefinder.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.Performance

/** A Fragment's whole UI as one Compose screen inside [DfTheme]. Call from onCreateView. */
fun Fragment.composeScreen(content: @Composable () -> Unit): View =
    ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { DfTheme(content) }
    }

/**
 * A tab root: [content] plus the nav capsule in the same Compose tree, so the page is recorded
 * ([glassSource]) and drawn blurred inside the capsule ([glassEffect]) - frosted glass for real.
 */
fun Fragment.tabScreen(tab: Tab, content: @Composable () -> Unit): View = composeScreen {
    val glass = rememberGlass()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (Performance.lite) Modifier else Modifier.glassSource(glass))) { content() }
        BottomNav(
            selected = tab,
            onSelect = { (activity as? MainActivity)?.selectTab(it) },
            glass = glass,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
