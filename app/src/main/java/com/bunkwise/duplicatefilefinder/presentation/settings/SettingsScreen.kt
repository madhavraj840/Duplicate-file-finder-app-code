package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.ui.BottomNavClearance
import com.bunkwise.duplicatefilefinder.ui.DfSwitch
import com.bunkwise.duplicatefilefinder.ui.ListRow
import com.bunkwise.duplicatefilefinder.ui.LockChip
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.SectionLabel
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop

/** One settings row. [switch] non-null shows a switch; [locked] swaps the trailing control for the Premium chip. */
data class SettingRow<K>(
    val key: K,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val locked: Boolean = false,
    val switch: Boolean? = null,
)

@Composable
fun <K> SettingsScreen(sections: List<Pair<String, List<SettingRow<K>>>>, onRow: (K) -> Unit) {
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.settings_title), onBack = null)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                sections.forEachIndexed { i, (label, rows) ->
                    SectionLabel(label, top = if (i == 0) Spacing.sm else Spacing.xl)
                    RowCard {
                        rows.forEachIndexed { j, row ->
                            if (j > 0) RowDivider()
                            ListRow(
                                label = row.title,
                                caption = row.subtitle,
                                icon = row.icon,
                                onClick = { onRow(row.key) },
                                trailing = when {
                                    row.locked -> ({ LockChip(onClick = { onRow(row.key) }) })
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
