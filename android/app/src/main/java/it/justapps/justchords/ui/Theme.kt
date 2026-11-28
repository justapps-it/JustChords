package it.justapps.justchords.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.justapps.justchords.R

// The justapps.it design system in Material 3: neutral greys, one orange
// accent, Inter + IBM Plex Mono, big rounded cards (src/styles.css).

val JustOrange = Color(0xFFFF5A1F)

@Immutable
data class JustColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val soft: Color,
    val orangeSoft: Color,
    val danger: Color,
    val ok: Color,
    val level1: Color,
    val level2: Color,
    val level3: Color,
)

private val LightJust = JustColors(
    bg = Color(0xFFF5F5F7), card = Color.White, text = Color(0xFF1D1D1F), muted = Color(0xFF6E6E73),
    line = Color(0x14000000), soft = Color(0x0A000000), orangeSoft = Color(0x1AFF5A1F), danger = Color(0xFFD70015), ok = Color(0xFF1F8F4E),
    level1 = Color(0xFF1F8F4E), level2 = Color(0xFFC77700), level3 = Color(0xFFD70015),
)
private val DarkJust = JustColors(
    bg = Color(0xFF0F0F10), card = Color(0xFF1C1C1E), text = Color(0xFFF5F5F7), muted = Color(0xFFA1A1A6),
    line = Color(0x1AFFFFFF), soft = Color(0x0DFFFFFF), orangeSoft = Color(0x29FF5A1F), danger = Color(0xFFFF6961), ok = Color(0xFF30D158),
    level1 = Color(0xFF30D158), level2 = Color(0xFFFFB340), level3 = Color(0xFFFF6961),
)

val LocalJust = staticCompositionLocalOf { LightJust }

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))
/** The ticly wordmark font. */
val DelaGothic = FontFamily(Font(R.font.dela_gothic_one))

/** Text with every standalone "ticly" in Dela Gothic One (not inside www.ticly.it or info@ticly.it). */
fun ticlyText(s: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    var last = 0
    for (m in Regex("(?<![.@\\w])ticly(?![.\\w])").findAll(s)) {
        append(s.substring(last, m.range.first))
        pushStyle(androidx.compose.ui.text.SpanStyle(fontFamily = DelaGothic))
        append(m.value)
        pop()
        last = m.range.last + 1
    }
    append(s.substring(last))
}

val PlexMono = FontFamily(Font(R.font.plex_mono, FontWeight.Normal), Font(R.font.plex_mono_medium, FontWeight.Medium), Font(R.font.plex_mono_medium, FontWeight.SemiBold))

private fun scheme(dark: Boolean, j: JustColors): ColorScheme = if (dark) {
    darkColorScheme(
        primary = JustOrange, onPrimary = Color.White, primaryContainer = Color(0xFF4A1A08), onPrimaryContainer = Color(0xFFFFDBCF),
        secondary = Color(0xFFE7BDB0), onSecondary = Color(0xFF442A21), secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = j.text,
        background = j.bg, onBackground = j.text, surface = j.bg, onSurface = j.text, surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = j.muted,
        surfaceContainerLowest = Color(0xFF0A0A0B), surfaceContainerLow = Color(0xFF161618), surfaceContainer = j.card,
        surfaceContainerHigh = Color(0xFF242426), surfaceContainerHighest = Color(0xFF2C2C2E),
        outline = Color(0x33FFFFFF), outlineVariant = j.line, error = j.danger, onError = Color.Black,
    )
} else {
    lightColorScheme(
        primary = JustOrange, onPrimary = Color.White, primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3A0B00),
        secondary = Color(0xFF77574C), onSecondary = Color.White, secondaryContainer = Color(0xFFEDEDF0), onSecondaryContainer = j.text,
        background = j.bg, onBackground = j.text, surface = j.bg, onSurface = j.text, surfaceVariant = Color(0xFFEDEDF0), onSurfaceVariant = j.muted,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFB), surfaceContainer = j.card,
        surfaceContainerHigh = Color(0xFFEFEFF2), surfaceContainerHighest = Color(0xFFE8E8EC),
        outline = Color(0x29000000), outlineVariant = j.line, error = j.danger, onError = Color.White,
    )
}

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.i(w: FontWeight? = null, ls: Float? = null) = copy(fontFamily = Inter, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.i(FontWeight.ExtraBold, -1.5f), displayMedium = base.displayMedium.i(FontWeight.ExtraBold, -1f),
        displaySmall = base.displaySmall.i(FontWeight.Bold, -0.8f), headlineLarge = base.headlineLarge.i(FontWeight.Bold, -0.8f),
        headlineMedium = base.headlineMedium.i(FontWeight.Bold, -0.6f), headlineSmall = base.headlineSmall.i(FontWeight.Bold, -0.4f),
        titleLarge = base.titleLarge.i(FontWeight.Bold, -0.3f), titleMedium = base.titleMedium.i(FontWeight.SemiBold, -0.1f),
        titleSmall = base.titleSmall.i(FontWeight.SemiBold), bodyLarge = base.bodyLarge.i(), bodyMedium = base.bodyMedium.i(), bodySmall = base.bodySmall.i(),
        labelLarge = base.labelLarge.i(FontWeight.SemiBold), labelMedium = base.labelMedium.i(FontWeight.SemiBold), labelSmall = base.labelSmall.i(FontWeight.Medium),
    )
}

private val JustShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun JustTheme(theme: String = "auto", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val j = if (dark) DarkJust else LightJust
    CompositionLocalProvider(LocalJust provides j) {
        MaterialTheme(colorScheme = scheme(dark, j), typography = typography(), shapes = JustShapes, content = content)
    }
}

val just: JustColors @Composable get() = LocalJust.current
