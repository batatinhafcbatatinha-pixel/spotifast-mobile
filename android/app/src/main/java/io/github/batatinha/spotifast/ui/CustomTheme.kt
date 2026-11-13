package io.github.batatinha.spotifast.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject

/**
 * A theme someone wrote as a small JSON file (see THEMES.md):
 *
 * ```json
 * { "name": "Solarized", "dark": true, "accent": "#B58900", "background": "#002B36" }
 * ```
 *
 * Only `name` is required. Colours left out come from Spotifast's own
 * Dark or Light palette, whichever `dark` picks.
 */
data class CustomTheme(val name: String, val palette: Palette) {
    fun toJson(): JSONObject = palette.toJson(name)

    companion object {
        /** The longest theme file accepted; real ones are a few hundred bytes. */
        const val MAX_BYTES = 16 * 1024

        /** Reads a theme, or throws [IllegalArgumentException] saying what is wrong. */
        fun parse(text: String): CustomTheme {
            require(text.length <= MAX_BYTES) { "That theme is too large." }
            val json = runCatching { JSONObject(text.trim()) }
                .getOrElse { throw IllegalArgumentException("That is not a theme file (it is not JSON).") }
            val name = json.optString("name").trim()
            require(name.isNotEmpty()) { "The theme needs a \"name\"." }
            require(name.length <= 40) { "The theme's name is longer than 40 characters." }
            val dark = if (json.has("dark")) json.optBoolean("dark", true) else true
            val base = if (dark) DarkBase else LightBase
            fun colour(key: String, fallback: Color): Color {
                if (!json.has(key)) return fallback
                return parseColour(json.optString(key))
                    ?: throw IllegalArgumentException("\"$key\" must be a colour like #1ED760.")
            }
            val font = when (val f = json.optString("font", "default").lowercase()) {
                "default", "sans", "" -> null
                "monospace", "mono" -> FontFamily.Monospace
                "serif" -> FontFamily.Serif
                else -> throw IllegalArgumentException("\"font\" must be default, monospace or serif, not \"$f\".")
            }
            return CustomTheme(
                name,
                Palette(
                    accent = colour("accent", base.accent),
                    background = colour("background", base.background),
                    surface = colour("surface", base.surface),
                    raised = colour("raised", base.raised),
                    track = colour("track", base.track),
                    muted = colour("muted", base.muted),
                    text = colour("text", base.text),
                    dark = dark,
                    font = font,
                ),
            )
        }

        /** The themes saved in preferences, skipping any that no longer read. */
        fun listFrom(stored: String?): List<CustomTheme> {
            stored ?: return emptyList()
            val array = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { runCatching { parse(it.toString()) }.getOrNull() }
            }
        }

        fun store(themes: List<CustomTheme>): String =
            JSONArray().apply { themes.forEach { put(it.toJson()) } }.toString()
    }
}

/** `#RRGGBB` or `#RRGGBBAA`, as in desktop Spotifast's theme files. */
internal fun parseColour(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val rgb = hex.substring(0, 6).toLong(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toLong(16) else 0xFF
    return Color(((alpha shl 24) or rgb).toInt())
}

internal fun Color.hex(): String {
    val argb = toArgb()
    val rgb = "%06X".format(argb and 0xFFFFFF)
    val alpha = (argb ushr 24) and 0xFF
    return if (alpha == 0xFF) "#$rgb" else "#$rgb%02X".format(alpha)
}

/** A palette as a theme file, so any theme can be the start of a new one. */
fun Palette.toJson(name: String): JSONObject = JSONObject().apply {
    put("name", name)
    put("dark", dark)
    put("accent", accent.hex())
    put("background", background.hex())
    put("surface", surface.hex())
    put("raised", raised.hex())
    put("track", track.hex())
    put("muted", muted.hex())
    put("text", text.hex())
    when (font) {
        FontFamily.Monospace -> put("font", "monospace")
        FontFamily.Serif -> put("font", "serif")
        else -> {}
    }
}
