package com.bunkwise.duplicatefilefinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunkwise.duplicatefilefinder.Performance
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

enum class Tab { HOME, CLEANUP, SETTINGS }

private val Tabs = listOf(
    Triple(Tab.HOME, R.string.nav_scan, AppIcons.NavScan),
    Triple(Tab.CLEANUP, R.string.nav_duplicates, AppIcons.NavDuplicates),
    Triple(Tab.SETTINGS, R.string.nav_settings, AppIcons.NavSettings),
)

/** Bottom padding a tab-root screen leaves so its last row clears the floating capsule. */
val BottomNavClearance = 96.dp

/**
 * The floating nav capsule (from Night Mode): only the active tab shows its label, and an accent
 * dot slides under it. With [glass] the page behind is blurred into it (Android 12+, not lite);
 * otherwise it is the plain navBg tint.
 */
@Composable
fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, glass: Glass? = null) {
    val n = Df.c
    val capsule = RoundedCornerShape(34.dp)
    Box(modifier = modifier.fillMaxWidth()) {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.7f to n.bg)))
    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = Spacing.gutter)
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .shadow(12.dp, capsule, ambientColor = n.shade, spotColor = n.shade)
                .clip(capsule)
                .then(
                    if (glass != null && !Performance.lite) Modifier.glassEffect(glass, n.bg, 28.dp, fallback = n.bg.copy(alpha = 0.94f))
                    else Modifier.background(n.navBg),
                )
                // Light catching the top edge, fading out down the sides.
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), capsule)
                .padding(horizontal = Spacing.sm),
        ) {
            val index = Tabs.indexOfFirst { it.first == selected }
            val tabWidth = maxWidth / Tabs.size
            val dotX by animateDpAsState(
                targetValue = tabWidth * (index + 0.5f),
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                label = "navDot",
            )
            Row(modifier = Modifier.matchParentSize()) {
                Tabs.forEach { (id, label, icon) ->
                    NavTab(icon, stringResource(label), id == selected, { onSelect(id) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = dotX - 2.5.dp, y = (-9).dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(n.ac),
            )
        }
        Spacer(Modifier.height(Spacing.md))
    }
    }
}

@Composable
private fun NavTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = Df.c
    val fg by animateColorAsState(if (selected) n.ac else n.navMu, tween(200), label = "navFg")
    Row(
        modifier = modifier
            .clip(Radius.pill)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
                role = Role.Tab
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(300)) + fadeIn(tween(300)),
            exit = shrinkHorizontally(tween(300)) + fadeOut(tween(200)),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = (-0.14).sp),
                color = fg,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}
