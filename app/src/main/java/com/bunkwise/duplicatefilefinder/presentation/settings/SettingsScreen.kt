package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.ui.BottomNavClearance
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.DfSwitch
import com.bunkwise.duplicatefilefinder.ui.IconRowInset
import com.bunkwise.duplicatefilefinder.ui.ListRow
import com.bunkwise.duplicatefilefinder.ui.LockChip
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.SectionLabel
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/** One settings row. [switch] non-null shows a switch; [locked] swaps the trailing control for "Watch ad". */
data class SettingRow<K>(
    val key: K,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val tint: Color,
    val locked: Boolean = false,
    val switch: Boolean? = null,
)

@Composable
fun <K> SettingsScreen(
    premium: Boolean,
    sections: List<Pair<String, List<SettingRow<K>>>>,
    onRow: (K) -> Unit,
    onWatchAd: (K) -> Unit,
    onPremium: () -> Unit,
) {
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.settings_title), onBack = null)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                // Stays after buying too - the one way back to the Premium page.
                ProCard(premium, onPremium)
                sections.forEach { (label, rows) ->
                    SectionLabel(label, top = Spacing.xl)
                    RowCard {
                        rows.forEachIndexed { j, row ->
                            if (j > 0) RowDivider(start = IconRowInset + Spacing.lg)
                            ListRow(
                                label = row.title,
                                caption = row.subtitle,
                                icon = row.icon,
                                iconTint = row.tint,
                                onClick = { onRow(row.key) },
                                trailing = when {
                                    row.locked -> ({ LockChip(onClick = { onWatchAd(row.key) }) })
                                    row.switch != null -> ({ DfSwitch(row.switch, { onRow(row.key) }) })
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(BottomNavClearance + Spacing.lg))
        }
    }
}

/** The app icon and what Premium gives: accent-filled until bought, then a quiet thank-you card. */
@Composable
private fun ProCard(premium: Boolean, onClick: () -> Unit) {
    val n = Df.c
    val fg = if (premium) n.tx else n.acTx
    val context = LocalContext.current
    val icon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap(168, 168).asImageBitmap()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .clip(Radius.cardShape)
            .background(if (premium) n.glass else n.ac)
            .border(1.dp, if (premium) n.line else n.ac, Radius.cardShape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(icon, null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Spacing.lg)))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (premium) R.string.settings_pro_active else R.string.settings_pro_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            Text(
                stringResource(if (premium) R.string.settings_pro_thanks else R.string.settings_pro_pitch),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = fg.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        Icon(AppIcons.ChevronRight, null, tint = fg, modifier = Modifier.size(22.dp))
    }
}
