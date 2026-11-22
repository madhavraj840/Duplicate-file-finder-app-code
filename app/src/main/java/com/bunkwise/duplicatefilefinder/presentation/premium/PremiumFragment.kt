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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import com.bunkwise.duplicatefilefinder.ui.AdInk
import com.bunkwise.duplicatefilefinder.ui.AdProgress
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.SectionLabel
import com.bunkwise.duplicatefilefinder.ui.StarYellow
import com.bunkwise.duplicatefilefinder.ui.TintedIcon
import com.bunkwise.duplicatefilefinder.ui.Tints
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.InfoNote
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
            Unlocks.version.collectAsStateWithLifecycle().value // re-read after an ad
            PremiumScreen(
                premium = premium,
                passActive = Unlocks.passActive(),
                adsDone = Unlocks.adsTowardPro(),
                showDebugToggle = isDebugBuild,
                onBack = { findNavController().popBackStack() },
                onWatchAd = { (activity as? MainActivity)?.watchAd(null) },
                onDebugToggle = { viewModel.setPremium(!premium) },
            )
        }
}

private data class Feature(val icon: ImageVector, val tint: Color, val title: Int, val desc: Int)

private val Features = listOf(
    Feature(AppIcons.Unlimited, Tints.blue, R.string.premium_f_unlimited, R.string.premium_f_unlimited_desc),
    Feature(AppIcons.SelectAll, Tints.green, R.string.premium_f_select_all, R.string.premium_f_select_all_desc),
    Feature(AppIcons.Folder, Tints.orange, R.string.premium_f_folders, R.string.premium_f_folders_desc),
    Feature(AppIcons.Schedule, Tints.purple, R.string.premium_f_schedule, R.string.premium_f_schedule_desc),
    Feature(AppIcons.Archive, Tints.teal, R.string.premium_f_archive, R.string.premium_f_archive_desc),
)

@Composable
private fun PremiumScreen(
    premium: Boolean,
    passActive: Boolean,
    adsDone: Int,
    showDebugToggle: Boolean,
    onBack: () -> Unit,
    onWatchAd: () -> Unit,
    onDebugToggle: () -> Unit,
) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.premium), onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Text(
                    stringResource(R.string.premium_headline),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
                    color = n.tx,
                )
                Text(
                    stringResource(R.string.premium_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = n.mu,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            FeatureCards(Modifier.padding(top = Spacing.xl))
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Spacer(Modifier.height(Spacing.xl))
                InfoNote(
                    if (premium || passActive) AppIcons.StatusOk else AppIcons.Info,
                    stringResource(
                        when {
                            premium -> R.string.premium_is_active
                            passActive -> R.string.unlock_pass_active
                            else -> R.string.premium_unavailable
                        }
                    ),
                    accent = premium || passActive,
                )
                if (!premium && !passActive) {
                    SectionLabel(stringResource(R.string.unlock_free_title))
                    AppCard {
                        Text(
                            stringResource(R.string.unlock_free_body, Unlocks.ADS_FOR_PRO),
                            style = MaterialTheme.typography.bodyMedium,
                            color = n.mu,
                        )
                        PrimaryButton(
                            stringResource(R.string.unlock_watch_ad),
                            onClick = onWatchAd,
                            icon = AppIcons.Play,
                            containerColor = StarYellow,
                            contentColor = AdInk,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        )
                        AdProgress(adsDone, Modifier.padding(top = Spacing.md))
                    }
                }
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

/** What Premium gives, one swipeable card each: the colour and glyph it has everywhere, then what it does for you. */
@Composable
private fun FeatureCards(modifier: Modifier = Modifier) {
    val n = Df.c
    val pager = rememberPagerState { Features.size }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Spacing.gutter + Spacing.lg),
            pageSpacing = Spacing.md,
        ) { i ->
            val f = Features[i]
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .clip(Radius.cardShape)
                    .background(n.glass)
                    .background(f.tint.copy(alpha = 0.14f))
                    .border(1.dp, f.tint.copy(alpha = 0.35f), Radius.cardShape)
                    .padding(Spacing.xl),
            ) {
                TintedIcon(f.icon, f.tint, size = 64.dp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(f.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = n.tx)
                Text(
                    stringResource(f.desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = n.mu,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(Features.size) { i ->
                val on = i == pager.currentPage
                Box(Modifier.height(6.dp).width(if (on) 18.dp else 6.dp).clip(Radius.pill).background(if (on) n.acInk else n.line2))
            }
        }
    }
}
