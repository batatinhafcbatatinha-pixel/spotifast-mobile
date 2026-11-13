package io.github.batatinha.spotifast.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeTest {
    @Test
    fun a_name_alone_is_spotifasts_dark_theme() {
        val theme = CustomTheme.parse("""{"name": "Plain"}""")
        assertEquals("Plain", theme.name)
        assertEquals(DarkBase, theme.palette)
    }

    @Test
    fun colours_left_out_come_from_the_chosen_base() {
        val theme = CustomTheme.parse("""{"name": "Paper", "dark": false, "accent": "#B58900"}""")
        assertEquals(LightBase.copy(accent = Color(0xFFB58900)), theme.palette)
    }

    @Test
    fun every_field_is_read() {
        val theme = CustomTheme.parse(
            """{"name":"All","dark":true,"accent":"#010203","background":"#040506","surface":"#070809",
               "raised":"#0A0B0C","track":"#0D0E0F","muted":"#101112","text":"#13141580","font":"serif"}""",
        )
        with(theme.palette) {
            assertEquals(Color(0xFF010203), accent)
            assertEquals(Color(0xFF040506), background)
            assertEquals(Color(0xFF070809), surface)
            assertEquals(Color(0xFF0A0B0C), raised)
            assertEquals(Color(0xFF0D0E0F), track)
            assertEquals(Color(0xFF101112), muted)
            assertEquals(Color(0x80131415), text)
            assertEquals(FontFamily.Serif, font)
        }
    }

    @Test
    fun a_theme_survives_being_copied_and_imported_again() {
        Appearance.entries.mapNotNull { it.palette }.forEach { palette ->
            val again = CustomTheme.parse(palette.toJson("Copy").toString())
            assertEquals(palette, again.palette)
        }
    }

    @Test
    fun saved_themes_load_and_broken_ones_are_skipped() {
        val good = CustomTheme.parse("""{"name": "Good", "accent": "#FF0000"}""")
        val stored = CustomTheme.store(listOf(good)).dropLast(1) + ""","not a theme",{"name":""}]"""
        assertEquals(listOf(good), CustomTheme.listFrom(stored))
        assertTrue(CustomTheme.listFrom("garbage").isEmpty())
        assertTrue(CustomTheme.listFrom(null).isEmpty())
    }

    @Test
    fun problems_are_explained() {
        fun problem(text: String) = assertThrows(IllegalArgumentException::class.java) { CustomTheme.parse(text) }.message
        assertEquals("That is not a theme file (it is not JSON).", problem("hello"))
        assertEquals("The theme needs a \"name\".", problem("""{"accent": "#FFFFFF"}"""))
        assertEquals("\"accent\" must be a colour like #1ED760.", problem("""{"name": "x", "accent": "green"}"""))
        assertTrue(problem("""{"name": "x", "font": "comic"}""")!!.startsWith("\"font\""))
        assertEquals("That theme is too large.", problem(" ".repeat(CustomTheme.MAX_BYTES + 1)))
    }

    @Test
    fun colours_are_hex_with_optional_alpha() {
        assertEquals(Color(0xFF1ED760), parseColour("#1ed760"))
        assertEquals(Color(0x201ED760), parseColour("#1ED76020"))
        assertNull(parseColour("#1ED76"))
        assertNull(parseColour("#GGGGGG"))
        assertEquals("#1ED760", Color(0xFF1ED760).hex())
        assertEquals("#1ED76020", Color(0x201ED760).hex())
    }
}

class CommunityThemesTest {
    @Test
    fun every_community_theme_reads() {
        val folder = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "themes") }
            .first { java.io.File(it, "README.md").exists() }
        val files = folder.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        val names = files.map { CustomTheme.parse(it.readText()).name }
        assertEquals("Two community themes share a name", names.size, names.toSet().size)
    }
}
