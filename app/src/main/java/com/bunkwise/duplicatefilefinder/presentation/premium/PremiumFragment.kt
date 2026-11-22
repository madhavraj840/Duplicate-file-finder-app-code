package com.bunkwise.duplicatefilefinder.presentation.premium

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.GoProBadge
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.ListRow
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/**
 * What Premium adds. There is no billing yet, so release builds only describe it;
 * debug builds get a toggle to test premium features.
 */
class PremiumFragment : Fragment() {

    private val viewModel: PremiumViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    private val isDebugBuild: Boolean
        get() = requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        composeScreen {
            val premium by viewModel.isPremium.collectAsStateWithLifecycle()
            PremiumScreen(
                premium = premium,
                showDebugToggle = isDebugBuild,
                onBack = { findNavController().popBackStack() },
                onDebugToggle = { viewModel.setPremium(!premium) },
            )
        }
}

private data class Feature(val icon: ImageVector, val title: Int, val desc: Int? = null)

private val Features = listOf(
    Feature(AppIcons.Unlimited, R.string.premium_f_unlimited, R.string.premium_f_unlimited_desc),
    Feature(AppIcons.SelectAll, R.string.premium_f_select_all, R.string.premium_f_select_all_desc),
    Feature(AppIcons.Folder, R.string.premium_f_folders),
    Feature(AppIcons.Schedule, R.string.premium_f_schedule),
    Feature(AppIcons.Archive, R.string.premium_f_archive),
)

@Composable
private fun PremiumScreen(premium: Boolean, showDebugToggle: Boolean, onBack: () -> Unit, onDebugToggle: () -> Unit) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.premium), onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                GoProBadge(40.dp)
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.premium_intro), style = MaterialTheme.typography.bodyLarge, color = n.mu)
                Spacer(Modifier.height(Spacing.xl))
                RowCard {
                    Features.forEachIndexed { i, f ->
                        if (i > 0) RowDivider()
                        ListRow(label = stringResource(f.title), caption = f.desc?.let { stringResource(it) }, icon = f.icon)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                InfoNote(
                    if (premium) AppIcons.StatusOk else AppIcons.Info,
                    stringResource(if (premium) R.string.premium_is_active else R.string.premium_unavailable),
                )
                if (showDebugToggle) {
                    Spacer(Modifier.height(Spacing.lg))
                    TonalButton(
                        stringResource(if (premium) R.string.premium_debug_off else R.string.premium_debug_on),
                        onClick = onDebugToggle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
