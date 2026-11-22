package com.bunkwise.duplicatefilefinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.bunkwise.duplicatefilefinder.Performance
import com.bunkwise.duplicatefilefinder.R

/** The animated "Go Pro" badge (Lottie). Loops, except in lite mode where it plays once. */
@Composable
fun GoProBadge(height: Dp, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.pro_badge))
    LottieAnimation(
        composition,
        iterations = if (Performance.lite) 1 else LottieConstants.IterateForever,
        modifier = modifier.size(width = height * 50f / 27f, height = height),
    )
}

/** The animated badge as a button - the header entry point to Premium. Hidden once Premium is active. */
@Composable
fun ProHeaderBadge(isPremium: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (isPremium) return
    Box(
        modifier = modifier
            .size(MinTouchTarget * 1.3f, MinTouchTarget)
            .clip(Radius.pill)
            .clickable(onClickLabel = stringResource(R.string.premium), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GoProBadge(height = 28.dp)
    }
}
