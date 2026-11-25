package gnosis.keyboard

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LucidVoidThemeTest {

    @Test
    fun testThemeCatalogRegistration() {
        assertEquals("lucid-void", Skins.KeyboardTheme.LUCID_VOID)
        assertEquals("lucid-void_light", Skins.KeyboardTheme.LUCID_VOID_LIGHT)
        assertEquals("lucid-void", Skins.THEME_LUCID_VOID)
        assertEquals("lucid-void_light", Skins.THEME_LUCID_VOID_LIGHT)

        val darkPreset = Skins.PRESETS[Skins.KeyboardTheme.LUCID_VOID]
        assertNotNull("Dark theme preset must be registered in PRESETS", darkPreset)
        assertEquals("Lucid Void", darkPreset!!.name)
        assertFalse("Dark theme must have isLight = false", darkPreset.isLight)

        val lightPreset = Skins.PRESETS[Skins.KeyboardTheme.LUCID_VOID_LIGHT]
        assertNotNull("Light theme preset must be registered in PRESETS", lightPreset)
        assertEquals("Lucid Void Light", lightPreset!!.name)
        assertTrue("Light theme must have isLight = true", lightPreset.isLight)
    }

    @Test
    fun testThemeColorDefinitions() {
        val darkPreset = Skins.PRESETS[Skins.KeyboardTheme.LUCID_VOID]!!
        assertEquals("#330A0D14", darkPreset.bg)
        assertEquals("#240A0D14", darkPreset.surface)
        assertEquals("#291E293B", darkPreset.key)
        assertEquals("#2E94A3B8", darkPreset.keyBorder)
        assertEquals("#F8FAFC", darkPreset.text)
        assertEquals("#4D0091FF", darkPreset.defaultAccent)

        val lightPreset = Skins.PRESETS[Skins.KeyboardTheme.LUCID_VOID_LIGHT]!!
        assertEquals("#2EFFFFFF", lightPreset.bg)
        assertEquals("#1FFFFFFF", lightPreset.surface)
        assertEquals("#47FFFFFF", lightPreset.key)
        assertEquals("#66FFFFFF", lightPreset.keyBorder)
        assertEquals("#E60F172A", lightPreset.text)
        assertEquals("#52007AFF", lightPreset.defaultAccent)
    }

    @Test
    fun testThemePairing() {
        val pairedLight = Skins.getPairedThemeId("lucid-void", targetLight = true)
        assertEquals("lucid-void_light", pairedLight)

        val pairedDark = Skins.getPairedThemeId("lucid-void_light", targetLight = false)
        assertEquals("lucid-void", pairedDark)

        val pairedSelfDark = Skins.getPairedThemeId("lucid-void", targetLight = false)
        assertEquals("lucid-void", pairedSelfDark)
    }

    @Test
    fun testLucidVoidPreferencesAndDefaults() {
        assertEquals("lucid_void_opacity", Skins.KEY_LUCID_VOID_OPACITY)
        assertEquals("lucid_void_blur", Skins.KEY_LUCID_VOID_BLUR)
        assertEquals("lucid_void_tint_dark", Skins.KEY_LUCID_VOID_TINT_DARK)
        assertEquals("lucid_void_tint_light", Skins.KEY_LUCID_VOID_TINT_LIGHT)
        assertEquals(48, Skins.DEFAULT_LUCID_VOID_OPACITY_DARK)
        assertEquals(44, Skins.DEFAULT_LUCID_VOID_OPACITY_LIGHT)
        assertEquals(48, Skins.DEFAULT_LUCID_VOID_BLUR_DARK)
        assertEquals(42, Skins.DEFAULT_LUCID_VOID_BLUR_LIGHT)
        assertEquals(25, Skins.DEFAULT_LUCID_VOID_TINT_DARK)
        assertEquals(20, Skins.DEFAULT_LUCID_VOID_TINT_LIGHT)
    }

    @Test
    fun testLucidVoidAccentLocking() {
        val darkPreset = Skins.PRESETS[Skins.THEME_LUCID_VOID]!!
        val lightPreset = Skins.PRESETS[Skins.THEME_LUCID_VOID_LIGHT]!!
        assertEquals("#4D0091FF", darkPreset.defaultAccent)
        assertEquals("#52007AFF", lightPreset.defaultAccent)
    }
}
