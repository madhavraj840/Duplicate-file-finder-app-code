package com.bunkwise.duplicatefilefinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.produceState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.core.content.getSystemService
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import com.bunkwise.duplicatefilefinder.R
import androidx.compose.ui.draw.drawWithCache
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/**
 * The app's shared building blocks. Every screen composes from these instead of hand-rolling its
 * own card, row or switch. Anything only one screen will ever use still belongs to that screen.
 *
 * Surfaces are glass: a see-through fill over the page's two soft glows ([glassBackdrop]), lit
 * along the top edge by a 1dp border fading from glassEdge to line. No shadows.
 */

/** Icons match the line-height of the text they sit beside, so they never tower over a label. */
val IconSize = 22.dp

/** The same rule one step down: for an icon sitting beside `bodySmall` rather than `bodyLarge`. */
val IconSizeSmall = 18.dp

/** Nothing tappable is smaller than this - fingers are not cursors. */
val MinTouchTarget = 48.dp

// ─────────────────────────────────────────────────────────────────────────────
// Surfaces
// ─────────────────────────────────────────────────────────────────────────────

/** How deep in nested cards we are, so a card inside a card steps its surface instead of its border. */
private val LocalCardDepth = compositionLocalOf { 0 }

@Composable
private fun Modifier.cardSurface(shape: Shape): Modifier {
    val nested = LocalCardDepth.current > 0
    val n = Df.c
    return this
        .clip(shape)
        .background(if (nested) n.glass2 else n.glass)
        .then(if (nested) Modifier else Modifier.border(1.dp, Brush.verticalGradient(listOf(n.glassEdge, n.line)), shape))
}

/** Every page's background: the theme colour plus two fixed soft glows that stay put while the glass
 *  cards scroll over them - the glass needs something behind it to show. */
@Composable
fun Modifier.glassBackdrop(): Modifier = background(Df.c.bg)

/** The one card: flat surface, 1px line. A card inside a card drops the line and steps to `s2`. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.cardShape,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.lg,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val depth = LocalCardDepth.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        CompositionLocalProvider(LocalCardDepth provides depth + 1) { content() }
    }
}

/** A card that is nothing but a stack of [ListRow]s - no padding, so rows run edge to edge. */
@Composable
fun RowCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val depth = LocalCardDepth.current
    Column(modifier = modifier.fillMaxWidth().cardSurface(Radius.cardShape)) {
        CompositionLocalProvider(LocalCardDepth provides depth + 1) { content() }
    }
}

/** Between rows *inside* a card. [start] lines it up with the row text when the rows carry icons. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, start: Dp = Spacing.lg) {
    HorizontalDivider(modifier = modifier.padding(start = start), thickness = 1.dp, color = Df.c.line)
}

/** The inset that lines a [RowDivider] up with the text of a [ListRow] that has an icon. */
val IconRowInset = 52.dp

/** The small uppercase header above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, top: Dp = Spacing.xxl - Spacing.xs) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Df.c.mu,
        modifier = modifier.padding(start = Spacing.xs, top = top, bottom = Spacing.md - Spacing.xxs),
    )
}

/** A tinted note under a control: a Doze caveat, a privacy promise, "applying needs Pro". */
@Composable
fun InfoNote(icon: ImageVector, text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val n = Df.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (accent) n.acSoft else n.glass2)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = if (accent) n.acInk else n.mu, modifier = Modifier.padding(top = Spacing.xxs).size(IconSizeSmall))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (accent) n.tx else n.mu)
    }
}

/** The rounded-square icon holder on feature cards. Lit up (accent) while the thing it stands for is on. */
@Composable
fun IconBox(icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = false, size: Dp = 44.dp) {
    val n = Df.c
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(if (active) n.acSoft else n.glass2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) n.acInk else n.mu, modifier = Modifier.size(size * 0.54f))
    }
}

/** A coloured glyph on a soft square of the same colour - the settings-list icon. */
@Composable
fun TintedIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The one row. With an [onClick] and no [trailing], it draws a chevron: something that navigates
 * should look like it navigates. [icon] is the muted leading glyph most settings rows carry.
 */
@Composable
fun ListRow(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    value: String? = null,
    pro: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    labelColor: Color = Df.c.tx,
    valueColor: Color = Df.c.mu,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val n = Df.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            icon != null && iconTint != null -> TintedIcon(icon, iconTint)
            icon != null -> Icon(icon, null, tint = n.mu, modifier = Modifier.size(IconSize))
        }
        leading?.invoke()
        // weight(1f): an unweighted label is handed every pixel it asks for, which squeezed the
        // trailing control to nothing on a narrow screen or at a raised font scale.
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = labelColor)
                if (pro) ProBadge(isPremium = false, modifier = Modifier.padding(start = Spacing.sm))
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = n.mu,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(AppIcons.ChevronRight, null, tint = n.mu, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A [ListRow] carrying the app's switch.
 *
 * [locked] swaps the switch for a Premium lock chip and routes every tap to [onLockedTap] - a
 * greyed-out switch tells the user nothing about why it won't move.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    caption: String? = null,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onLockedTap: () -> Unit = {},
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    ListRow(
        label = label,
        caption = caption,
        icon = icon,
        modifier = modifier,
        leading = leading,
        onClick = if (locked) onLockedTap else null,
        trailing = {
            if (locked) {
                LockChip(onClick = onLockedTap)
            } else {
                DfSwitch(
                    checked = checked,
                    onCheckedChange = {
                        onChange(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                )
            }
        },
    )
}

/**
 * One line of a "is this working?" checklist.
 *
 * [optional] means nice-to-have, not broken: a muted ring and an outlined "Turn on" instead of a
 * red alarm. A checklist that opens with red marks against things the user never asked for reads
 * as "this app is broken out of the box".
 */
@Composable
fun StatusRow(
    label: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null,
    optional: Boolean = false,
    okLabel: String = stringResource(R.string.status_granted),
    fixLabel: String = stringResource(if (optional) R.string.action_turn_on else R.string.action_fix),
    onFix: () -> Unit,
) {
    val n = Df.c
    ListRow(
        label = label,
        caption = caption,
        modifier = modifier,
        leading = {
            Icon(
                when {
                    ok -> AppIcons.StatusOk
                    optional -> AppIcons.Unselected
                    else -> AppIcons.StatusProblem
                },
                contentDescription = null,
                tint = when {
                    ok -> n.ok
                    optional -> n.mu
                    else -> n.danger
                },
                modifier = Modifier.size(IconSize),
            )
        },
        value = if (ok) okLabel.ifEmpty { null } else null,
        trailing = if (ok) null else ({
            if (optional) TonalButton(fixLabel, onClick = onFix) else SmallAccentButton(fixLabel, onClick = onFix)
        }),
    )
}

/** A row that offers one of several mutually exclusive choices. The filled radio is the signifier. */
@Composable
fun SelectableRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val n = Df.c
    ListRow(
        label = label,
        caption = caption,
        modifier = modifier,
        leading = leading,
        onClick = onSelect,
        trailing = {
            Icon(
                if (selected) AppIcons.Selected else AppIcons.Unselected,
                contentDescription = null,
                tint = if (selected) n.acInk else n.line2,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The app's switch. Off is a hollow ring with a small grey knob; on is a solid accent track with a
 * big dark knob - the knob growing is what makes "on" readable without relying on colour alone.
 */
@Composable
fun DfSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val n = Df.c
    val track by animateColorAsState(if (checked) n.ac else n.s2, label = "switchTrack")
    val knob by animateColorAsState(if (checked) n.acTx else n.mu, label = "switchKnob")
    val knobSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "switchKnobSize")
    val knobStart by animateDpAsState(if (checked) 24.dp else 8.dp, label = "switchKnobStart")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(Radius.pill)
            .background(track)
            .then(if (checked) Modifier else Modifier.border(2.dp, n.line2, Radius.pill))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = knobStart)
                .size(knobSize)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** The main action: a full accent pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    containerColor: Color = Df.c.ac,
    contentColor: Color = Df.c.acTx,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.pill,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        modifier = modifier.height(height),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The quieter action: an outlined pill. Also the inline "Turn on" / "Set up" on a status row. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 36.dp,
    contentColor: Color = Df.c.tx,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.pill,
        border = BorderStroke(1.5.dp, Df.c.line2),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        modifier = modifier.height(height),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(IconSizeSmall))
            Spacer(modifier = Modifier.size(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** A small filled accent pill - "Fix", "Unlock". */
@Composable
fun SmallAccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = Df.c
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(Radius.pill)
            .background(n.ac)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = n.acTx)
    }
}

/** Destructive, and it should look it: outlined, red text. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TonalButton(text, onClick, modifier = modifier.fillMaxWidth(), height = 52.dp, contentColor = Df.c.danger)
}

/**
 * A labelled section that starts collapsed. Step-by-step instructions matter enormously once and
 * are noise every time after.
 */
@Composable
fun Disclosure(
    label: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(initiallyExpanded) }
    val n = Df.c
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.chipShape)
                .clickable { expanded = !expanded }
                .defaultMinSize(minHeight = MinTouchTarget)
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = n.acInk,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) AppIcons.Collapse else AppIcons.Expand, null, tint = n.acInk, modifier = Modifier.size(IconSize))
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = Spacing.sm), content = content)
        }
    }
}

/**
 * The app's bottom sheet: a big title and the body. Every sheet in the app goes through here so
 * they all open, pad and title the same way. On Android 12+ (when the phone allows cross-window
 * blur - Battery Saver can turn it off) the app behind is blurred and the sheet is see-through
 * glass; otherwise it stays solid `s1`, since a see-through sheet over sharp text is unreadable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val n = Df.c
    val glass = canBlurBehind()
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = Radius.sheetShape,
        containerColor = if (glass) n.sheetGlass else n.s1,
        // No border modifier: material3 1.3 applies it before the sheet's slide offset, so it was drawn
        // round the whole screen's top corners instead of the sheet's.
    ) {
        // Fades out the moment the sheet starts closing, not after its window is gone.
        if (glass) BlurBehindWindow(visible = state.targetValue != SheetValue.Hidden)
        Column(modifier = Modifier.padding(horizontal = Spacing.gutter).navBarPadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = n.tx, modifier = Modifier.padding(horizontal = Spacing.xs))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = n.mu,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            content()
            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

/** Whether this phone will blur behind a window right now (Android 12+; Battery Saver can turn it off). */
@Composable
fun canBlurBehind(): Boolean {
    val context = LocalContext.current
    return remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.getSystemService<WindowManager>()?.isCrossWindowBlurEnabled == true
    }
}

/** Blurs the app behind the dialog window (sheet, full-screen dialog) this is composed in, easing
 *  in on open and out as soon as [visible] goes false. Only call it when [canBlurBehind]. */
@Composable
fun BlurBehindWindow(visible: Boolean = true) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val maxPx = 40 * view.resources.displayMetrics.density
    val amount = remember { Animatable(0f) }
    LaunchedEffect(visible) { amount.animateTo(if (visible) 1f else 0f, tween(if (visible) 250 else 120)) }
    LaunchedEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        snapshotFlow { amount.value }.collect { a ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.attributes = window.attributes.apply { blurBehindRadius = (maxPx * a).toInt() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Badges
// ─────────────────────────────────────────────────────────────────────────────

/** A small status pill - a word or two, never a sentence. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Df.c.mu,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(Radius.pill)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.size(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = tint)
    }
}

/** A tiny tracked-out tag: "PRO", "BETA". */
@Composable
fun Tag(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(20.dp).clip(Radius.pill).background(background).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** The "PRO" marker beside a gated feature's name. Renders nothing once Premium is active. */
@Composable
fun ProBadge(isPremium: Boolean, modifier: Modifier = Modifier) {
    if (isPremium) return
    Tag(stringResource(R.string.pro_tag), Df.c.acSoft, Df.c.acInk, modifier)
}

/** Dark text on [StarYellow] - the lock chip colours. */
val AdInk = Color(0xFF1C1604)

/** Stands in for a control the user can't use yet: "Watch ad" - the tap plays the ad straight away (12-hour unlock). */
@Composable
fun LockChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(Radius.pill)
            .background(StarYellow)
            .clickable(onClickLabel = stringResource(R.string.chip_watch_ad), onClick = onClick)
            .padding(start = 12.dp, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Play, null, tint = AdInk, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(stringResource(R.string.chip_watch_ad), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AdInk, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty states
// ─────────────────────────────────────────────────────────────────────────────

/**
 * What a list looks like before it has anything in it: what would go here, and the one action
 * that fills it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val n = Df.c
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBox(icon, size = 64.dp)
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, color = n.tx, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = n.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.xl))
            PrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

/**
 * Room for the system nav bar. Not navigationBarsPadding(): that inset reads 0 whenever Android
 * counts the bar as hidden or still sliding back in (after a full-screen ad or another app's
 * immersive mode), and an interrupted slide can leave it at 0 - the bottom nav then sat behind
 * the system buttons. This one is the bar's real size either way; the app never hides the bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.navBarPadding(): Modifier = windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)

/** The app's checkbox: an accent box when on, a hollow line2 box when off. 48dp touch target. */
@Composable
fun DfCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** Some but not all - drawn as a dash, still "unchecked" for the toggle. */
    partial: Boolean = false,
) {
    val n = Df.c
    Box(
        modifier = modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                checked -> AppIcons.Checked
                partial -> AppIcons.Partial
                else -> AppIcons.Unchecked
            },
            contentDescription = null,
            tint = if (checked || partial) n.ac else n.line2,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The top of every screen. Tab roots pass no [onBack] and get a big headline; pushed screens get
 * a back arrow and a titleLarge title. [trailing] holds header actions (the Pro badge, search).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val n = Df.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = if (onBack != null) Spacing.xs else Spacing.gutter, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.Back, stringResource(R.string.cd_back), tint = n.tx)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                color = n.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 2)
        }
        trailing()
    }
}
