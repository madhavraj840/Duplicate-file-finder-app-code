package com.bunkwise.duplicatefilefinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/** "Unlock X": watch an ad (12 hours, yellow, first) or get Premium; the dots count ads toward all of Premium. */
@Composable
fun UnlockSheet(feature: ProFeature, onWatchAd: () -> Unit, onGetPro: () -> Unit, onDismiss: () -> Unit) {
    val n = Df.c
    val done = Unlocks.adsTowardPro()
    DfSheet(
        title = stringResource(R.string.unlock_title, stringResource(feature.label)),
        subtitle = stringResource(R.string.unlock_body),
        onDismiss = onDismiss,
    ) {
        PrimaryButton(
            stringResource(R.string.unlock_watch_ad),
            onClick = onWatchAd,
            icon = AppIcons.Play,
            containerColor = StarYellow,
            contentColor = AdInk,
            modifier = Modifier.fillMaxWidth(),
        )
        TonalButton(
            stringResource(R.string.unlock_get_pro),
            onClick = onGetPro,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
        AdProgress(done, Modifier.padding(top = Spacing.lg))
    }
}

/** Three dots, one filled per ad watched in this 12-hour window. */
@Composable
fun AdProgress(done: Int, modifier: Modifier = Modifier) {
    val n = Df.c
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(Unlocks.ADS_FOR_PRO) { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(8.dp).clip(CircleShape).background(if (i < done) StarYellow else n.line2))
        }
        Text(
            stringResource(R.string.unlock_progress, done, Unlocks.ADS_FOR_PRO),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = n.mu,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}
