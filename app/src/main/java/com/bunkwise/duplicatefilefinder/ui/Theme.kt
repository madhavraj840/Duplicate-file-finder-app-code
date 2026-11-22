package com.bunkwise.duplicatefilefinder.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Fixed, theme-independent status colours: HowToUseScreen's mini-mocks draw someone else's UI, and
// Warning has no slot in [DfColors]. Everything else reads [Df.c].
val Danger = Color(0xFFE5635B)

/** Rating stars - always yellow, whatever the accent. */
val StarYellow = Color(0xFFFFC107)
val Warning = Color(0xFFC98A2E)
val Success = Color(0xFF5DBB85)

/**
 * The colours Material3's scheme has no slot for. Ink-black surfaces with one blue accent,
 * and the accent only ever means "action" or "Pro" - never decoration.
 *
 * [acInk] is the accent as *text*: the fill colour itself is unreadable as text on a white
 * surface, so light mode darkens it to a brass that still reads as the same colour.
 * [lit]/[shade] are the two halves of the window-blind control: the lit panel and the dark layer
 * pulled down over it - near-black whether dimming is on or off.
 */
class DfColors(
    val bg: Color,
    val s1: Color,
    val s2: Color,
    val line: Color,
    val line2: Color,
    val tx: Color,
    val mu: Color,
    val ac: Color,
    val acTx: Color,
    val acInk: Color,
    val acSoft: Color,
    val lit: Color,
    val shade: Color,
    val ok: Color,
    val okSoft: Color,
    val danger: Color,
    // The live first-month offer (countdown pill, offer banner) - starts here, runs to [danger].
    val offer: Color,
    // Informational, not an action or Pro: e.g. Home's "turn on notifications" line.
    val info: Color,
    // The floating nav capsule stays dark in both themes - a lamp-lit object over the page.
    val navBg: Color,
    val navLine: Color,
    val navMu: Color = Color(0xFF7D8394),
    // Glass: cards are a see-through fill over the page's two soft glows, lit along the top edge.
    val glass: Color,
    val glass2: Color,     // a glass panel inside a glass card
    val glassEdge: Color,  // the top highlight of a card's border, fading to [line]
    val glowA: Color,      // page glow, top right
    val glowB: Color,      // page glow, bottom left
    val sheetGlass: Color, // a bottom sheet over the blurred app (Android 12+)
)

private val DarkDf = DfColors(
    bg = Color(0xFF0D1117),     // Deep slate background instead of pure black
    s1 = Color(0xFF161B22),     // Elevated slate surface
    s2 = Color(0xFF21262D),     // Secondary elevated surface
    line = Color(0xFF30363D),   // Crisp borders
    line2 = Color(0xFF484F58),  // Stronger borders
    tx = Color(0xFFE6EDF3),     // Crisp off-white text
    mu = Color(0xFF8B949E),     // Cool slate-grey for muted text
    ac = Color(0xFF4493F8),     // Bright, legible digital blue (clean, non-AI)
    acTx = Color(0xFF0D1117),   // Dark slate text on blue background
    acInk = Color(0xFF58A6FF),  // Lighter blue for accent text on dark backgrounds
    acSoft = Color(0x264493F8), // Soft blue for translucent backgrounds
    lit = Color(0xFFC9D1D9),    // Soft grey-white for lit elements
    shade = Color(0xFF010409),  // Deepest slate for shading
    ok = Color(0xFF3FB950),     // Clean, legible green
    okSoft = Color(0x263FB950),
    danger = Color(0xFFF85149), // Legible red for dark mode
    offer = Color(0xFFFFA657),  // Warm, legible orange for offers
    info = Color(0xFF58A6FF),   // Matches accent ink
    navBg = Color(0xFF010409),  // Deepest slate for navigation
    navLine = Color(0xFF30363D),
    glass = Color(0x14FFFFFF),
    glass2 = Color(0x0FFFFFFF),
    glassEdge = Color(0x40FFFFFF),
    glowA = Color(0x474493F8),
    glowB = Color(0x338E7CFF),
    sheetGlass = Color(0xB3161B22),
)

private val LightDf = DfColors(
    bg = Color(0xFFF6F7F9),     // Crisp airy grey-white
    s1 = Color(0xFFFFFFFF),     // Pure white surfaces
    s2 = Color(0xFFE8EBF0),     // Very soft cool-grey for secondary cards
    line = Color(0xFFD6DBE3),   // Clean borders
    line2 = Color(0xFFA5ADB8),  // Strong borders
    tx = Color(0xFF1A1E24),     // Sharp deep slate text
    mu = Color(0xFF626B75),     // Professional slate-grey for muted text
    ac = Color(0xFF2A85FF),     // Crisp digital blue accent (clean, non-AI)
    acTx = Color(0xFFFFFFFF),   // White text on blue accent
    acInk = Color(0xFF1553A6),  // Deep blue for accent text on light bg
    acSoft = Color(0x1F2A85FF), // Pale blue background for soft accents
    lit = Color(0xFFFFFFFF),
    shade = Color(0xFF10141A),  // Deep slate shade
    ok = Color(0xFF1F9E54),     // Vibrant natural green
    okSoft = Color(0x1F1F9E54),
    danger = Color(0xFFE0383E), // Classic red
    offer = Color(0xFFFF6B00),  // Vibrant orange for offers
    info = Color(0xFF0073E6),   // Classic blue for info
    navBg = Color(0xFF0D1117),  // Nearly black slate for the dark bottom nav
    navLine = Color(0x14FFFFFF),
    glass = Color(0x99FFFFFF),
    glass2 = Color(0x80E8EBF0),
    glassEdge = Color(0xFFFFFFFF),
    glowA = Color(0x402A85FF),
    glowB = Color(0x338E7CFF),
    sheetGlass = Color(0xB3FFFFFF),
)

private val LocalDfColors = staticCompositionLocalOf { DarkDf }

/** The current theme's [DfColors]: `Df.c.acInk`. */
object Df {
    val c: DfColors
        @Composable @ReadOnlyComposable get() = LocalDfColors.current
}

private fun scheme(n: DfColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = n.ac, onPrimary = n.acTx, secondary = n.acInk, onSecondary = n.acTx,
        background = n.bg, onBackground = n.tx, surface = n.s1, onSurface = n.tx,
        surfaceContainer = n.s1, surfaceContainerHigh = n.s2, surfaceContainerLow = n.s1,
        surfaceVariant = n.s2, onSurfaceVariant = n.mu,
        outline = n.line2, outlineVariant = n.line, error = n.danger, onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = n.ac, onPrimary = n.acTx, secondary = n.acInk, onSecondary = Color.White,
        background = n.bg, onBackground = n.tx, surface = n.s1, onSurface = n.tx,
        surfaceContainer = n.s1, surfaceContainerHigh = n.s2, surfaceContainerLow = n.s1,
        surfaceVariant = n.s2, onSurfaceVariant = n.mu,
        outline = n.line2, outlineVariant = n.line, error = n.danger, onError = Color.White,
    )
}

/**
 * System fonts only: sans for words, monospace for every number - tabular digits read like an
 * instrument and don't jitter as a value changes. Large text is tracked tight; small uppercase
 * labels are tracked out, because caps set solid read cramped.
 */
private val Sans = FontFamily.SansSerif
val Mono = FontFamily.Monospace

val DfTypography = Typography(
    // Big mono figures: usage total, the per-app level sheet.
    displayLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 52.sp, letterSpacing = (-2.5).sp, lineHeight = 52.sp),
    // Tab-root screen titles.
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.7).sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.9).sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp, lineHeight = 26.sp),
    // Pushed-screen header.
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    // Row labels.
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp),
    // "PRO", "BETA" - tiny, heavy, tracked out.
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.6.sp, lineHeight = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp, lineHeight = 16.sp),
)

/** Named spacing. [gutter] is THE screen margin - every screen uses it, none picks its own. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    val gutter = 20.dp
}

/** Corner radii. Lists sit in [card]; the bigger feature cards use [feature]. */
object Radius {
    val chip = 12.dp
    val card = 20.dp
    val feature = 24.dp
    val sheet = 28.dp
    val pill = RoundedCornerShape(percent = 50)

    val cardShape = RoundedCornerShape(card)
    val featureShape = RoundedCornerShape(feature)
    val chipShape = RoundedCornerShape(chip)
    val sheetShape = RoundedCornerShape(topStart = sheet, topEnd = sheet)
}

/**
 * Durations, in milliseconds. [standard] for something moving across the screen, [fast] for
 * feedback on a thing the finger is already touching.
 */
object Motion {
    const val fast = 90
    const val standard = 180
}

/** ThemePrefs drives AppCompatDelegate's night mode, which sets this Activity's uiMode - so the
 *  configuration already reflects the in-app Light/Dark/System choice. */
@Composable
fun isAppInDarkTheme(): Boolean = isSystemInDarkTheme()

/** Draw behind the system bars (Android 15+ does it anyway). The bar colours come from the theme
 *  (themes.xml), so no deprecated Window colour/cutout call ever runs - Play flags those. */
fun drawEdgeToEdge(window: android.view.Window) {
    if (android.os.Build.VERSION.SDK_INT < 35) WindowCompat.setDecorFitsSystemWindows(window, false)
}

@Composable
fun DfTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    val nm = if (dark) DarkDf else LightDf
    // Bar icon colours follow the *app* theme, not the system one - forcing Light in the app on a
    // dark-mode phone otherwise left white icons on a white screen.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    // Layouts broke on phones with a big "Display size" or font size: draw at the phone's stock
    // density whatever Display size says, and let font size grow only a little.
    // ponytail: ignores Display size entirely; relax the cap if users ask for bigger text.
    val sys = LocalDensity.current
    val density = androidx.compose.ui.unit.Density(
        density = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f,
        fontScale = sys.fontScale.coerceIn(0.9f, 1.15f),
    )
    CompositionLocalProvider(LocalDfColors provides nm, LocalDensity provides density) {
        MaterialTheme(
            colorScheme = scheme(nm, dark),
            typography = DfTypography,
            content = content,
        )
    }
}
