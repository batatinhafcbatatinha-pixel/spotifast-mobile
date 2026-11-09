package io.github.batatinha.spotifast.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

/** The colours the screens use beyond Material's own roles. */
data class Palette(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val track: Color,
    val muted: Color,
    val text: Color,
    val dark: Boolean,
    /** A typeface the phone already has, for themes that want one. */
    val font: FontFamily? = null,
) {
    val scrim: Color get() = background.copy(alpha = 0.85f)
}

private val DarkBase = Palette(
    accent = Color(0xFF1ED760), background = Color(0xFF0F1114), surface = Color(0xFF181B20),
    raised = Color(0xFF2A2F36), track = Color(0xFF4A5059), muted = Color(0xFFA5ADBA),
    text = Color(0xFFE8EAED), dark = true,
)
private val LightBase = Palette(
    accent = Color(0xFF128A3E), background = Color(0xFFF7F8FA), surface = Color(0xFFECEEF2),
    raised = Color(0xFFE1E4EA), track = Color(0xFFC5CAD3), muted = Color(0xFF5F6670),
    text = Color(0xFF15181C), dark = false,
)
private val BlackBase = Palette(
    accent = Color(0xFF1ED760), background = Color.Black, surface = Color(0xFF0E0E0E),
    raised = Color(0xFF1A1A1A), track = Color(0xFF3A3A3A), muted = Color(0xFF9AA0A8),
    text = Color(0xFFE8EAED), dark = true,
)

/**
 * Themes. The first four take an [Accent]; the rest bring their own
 * colours, after well-known Spicetify themes and palettes.
 */
enum class Appearance(val label: String, val palette: Palette?) {
    System("Same as the phone", null),
    Dark("Dark", DarkBase),
    Light("Light", LightBase),
    Black("Black (AMOLED)", BlackBase),

    /** Colours taken from the cover of the song playing (Dribbblish Dynamic, Lucid). */
    Dynamic("Dynamic (from the cover)", null),

    Mocha(
        "Catppuccin Mocha",
        Palette(Color(0xFFCBA6F7), Color(0xFF1E1E2E), Color(0xFF181825), Color(0xFF313244),
            Color(0xFF45475A), Color(0xFFA6ADC8), Color(0xFFCDD6F4), dark = true),
    ),
    Latte(
        "Catppuccin Latte",
        Palette(Color(0xFF8839EF), Color(0xFFEFF1F5), Color(0xFFE6E9EF), Color(0xFFCCD0DA),
            Color(0xFFBCC0CC), Color(0xFF6C6F85), Color(0xFF4C4F69), dark = false),
    ),
    Dracula(
        "Dracula",
        Palette(Color(0xFFBD93F9), Color(0xFF282A36), Color(0xFF21222C), Color(0xFF44475A),
            Color(0xFF6272A4), Color(0xFF9EA8C7), Color(0xFFF8F8F2), dark = true),
    ),
    Nord(
        "Nord",
        Palette(Color(0xFF88C0D0), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E),
            Color(0xFF4C566A), Color(0xFFA3B1C6), Color(0xFFECEFF4), dark = true),
    ),
    Gruvbox(
        "Gruvbox",
        Palette(Color(0xFFFE8019), Color(0xFF282828), Color(0xFF32302F), Color(0xFF3C3836),
            Color(0xFF504945), Color(0xFFA89984), Color(0xFFEBDBB2), dark = true),
    ),
    Fluent(
        "Fluent",
        Palette(Color(0xFF60CDFF), Color(0xFF202020), Color(0xFF2B2B2B), Color(0xFF353535),
            Color(0xFF4A4A4A), Color(0xFFC5C5C5), Color(0xFFFFFFFF), dark = true),
    ),
    Comfy(
        "Comfy",
        Palette(Color(0xFF8FA4F5), Color(0xFF16191E), Color(0xFF1E2229), Color(0xFF282D36),
            Color(0xFF3A4150), Color(0xFF8D96A6), Color(0xFFDCE0E8), dark = true),
    ),
    Throwback(
        "Throwback",
        Palette(Color(0xFF84BD00), Color(0xFF121314), Color(0xFF222326), Color(0xFF2E2F33),
            Color(0xFF45464B), Color(0xFFA0A0A0), Color(0xFFFFFFFF), dark = true),
    ),
    Eighties(
        "80s synthwave",
        Palette(Color(0xFFFF4FD8), Color(0xFF1A0B2E), Color(0xFF261447), Color(0xFF341D5C),
            Color(0xFF00E5FF), Color(0xFF9D8FD0), Color(0xFFF6E8FF), dark = true),
    ),
    Nineties(
        "90s vaporwave",
        Palette(Color(0xFFFF71CE), Color(0xFF2D1B4E), Color(0xFF3B2766), Color(0xFF4A3380),
            Color(0xFF01CDFE), Color(0xFFB9F2E4), Color(0xFFFFFBF0), dark = true, font = FontFamily.Monospace),
    ),
    Terminal(
        "Terminal",
        Palette(Color(0xFF33FF66), Color(0xFF020A04), Color(0xFF07160B), Color(0xFF0D2414),
            Color(0xFF1C5A2E), Color(0xFF29B34F), Color(0xFFB8FFC8), dark = true, font = FontFamily.Monospace),
    ),
    Cyberpunk(
        "Cyberpunk",
        Palette(Color(0xFFFCEE0A), Color(0xFF050608), Color(0xFF101318), Color(0xFF1A1F27),
            Color(0xFF00F0FF), Color(0xFF00C2CC), Color(0xFFEAF6FF), dark = true, font = FontFamily.Monospace),
    ),
    Coffee(
        "Coffee",
        Palette(Color(0xFFD4A373), Color(0xFF1E1410), Color(0xFF2C1E17), Color(0xFF3B2A21),
            Color(0xFF6B4F3F), Color(0xFFC9B29B), Color(0xFFF3E9DC), dark = true, font = FontFamily.Serif),
    ),
    Rose(
        "Rose",
        Palette(Color(0xFFD63384), Color(0xFFFFF0F6), Color(0xFFFCE1EC), Color(0xFFF8CCDD),
            Color(0xFFF1A7C5), Color(0xFF8C5A70), Color(0xFF3A1027), dark = false),
    );

    /** Whether the accent colour can be chosen. */
    val takesAccent: Boolean get() = this in setOf(System, Dark, Light, Black)
}

enum class Accent(val label: String, val dark: Color, val light: Color) {
    Green("Green", Color(0xFF1ED760), Color(0xFF128A3E)),
    Blue("Blue", Color(0xFF4EA8FF), Color(0xFF1765C1)),
    Purple("Purple", Color(0xFFB98CFF), Color(0xFF6B3FC4)),
    Pink("Pink", Color(0xFFFF7AB6), Color(0xFFC2185B)),
    Orange("Orange", Color(0xFFFFA24C), Color(0xFFC25E00)),
    Red("Red", Color(0xFFFF6B6B), Color(0xFFC62828)),
}

/** Colours picked from a cover: its most vivid colour and its dominant one. */
data class CoverColors(val vivid: Color, val dominant: Color)

private fun dynamic(cover: CoverColors?): Palette {
    cover ?: return DarkBase
    // A deep shade of the cover's main colour as the background, its
    // vivid colour as the accent, lightened until it reads on that shade.
    val background = cover.dominant.copy(alpha = 0.22f).compositeOver(Color(0xFF0B0C0E))
    var accent = cover.vivid
    while (accent.luminance() < 0.3f) accent = Color.White.copy(alpha = 0.25f).compositeOver(accent)
    return DarkBase.copy(
        accent = accent,
        background = background,
        surface = Color.White.copy(alpha = 0.05f).compositeOver(background),
        raised = Color.White.copy(alpha = 0.10f).compositeOver(background),
        track = Color.White.copy(alpha = 0.22f).compositeOver(background),
    )
}

private fun palette(appearance: Appearance, accent: Accent, systemDark: Boolean, cover: CoverColors?): Palette =
    when (appearance) {
        Appearance.System -> if (systemDark) DarkBase.copy(accent = accent.dark) else LightBase.copy(accent = accent.light)
        Appearance.Dark, Appearance.Black -> appearance.palette!!.copy(accent = accent.dark)
        Appearance.Light -> LightBase.copy(accent = accent.light)
        Appearance.Dynamic -> dynamic(cover)
        else -> appearance.palette!!
    }

val LocalPalette = staticCompositionLocalOf { DarkBase }

val Green: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
val Raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
val Track: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.track
val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
val Scrim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.scrim

@Composable
fun SpotifastTheme(
    appearance: Appearance = Appearance.System,
    accent: Accent = Accent.Green,
    cover: CoverColors? = null,
    content: @Composable () -> Unit,
) {
    val p = palette(appearance, accent, isSystemInDarkTheme(), cover)
    val onAccent = if (p.accent.luminance() > 0.4f) Color(0xFF0F1114) else Color.White
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = onAccent, secondary = p.accent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.muted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.raised,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = onAccent, secondary = p.accent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.muted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.raised,
        )
    }
    // Status and navigation bar icons dark on a light theme, light on a dark one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !p.dark
                isAppearanceLightNavigationBars = !p.dark
            }
        }
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography(p.font), content = content)
    }
}

private fun typography(font: FontFamily?): Typography {
    val base = Typography()
    if (font == null) return base
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}
