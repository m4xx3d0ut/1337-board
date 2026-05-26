package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.leetboard.ime.model.CustomThemeConfig
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.ThemePreset

class ThemeEngineTest {
    private val engine = ThemeEngine()

    @Test
    fun customThemeAppliesColorsOpacityAndImage() {
        val config = CustomThemeConfig(
            backgroundColor = 0xFF111111.toInt(),
            keyFillColor = 0xFF222222.toInt(),
            keyStrokeColor = 0xFF333333.toInt(),
            keyTextColor = 0xFF444444.toInt(),
            pressedFillColor = 0xFF555555.toInt(),
            activeModifierFillColor = 0xFF666666.toInt(),
            backgroundImageUri = "content://theme/background",
            backgroundImageOpacity = 0.5f,
            keyFillOpacity = 0.25f,
            keyStrokeOpacity = 0.75f,
            keyTextOpacity = 0.8f,
        )

        val theme = engine.resolve(ThemePreset.CUSTOM, customTheme = config)

        assertEquals(ThemePreset.CUSTOM, theme.preset)
        assertEquals(0xFF111111.toInt(), theme.colors.background)
        assertEquals(0x3F222222, theme.colors.keyFill)
        assertEquals(0xBF333333.toInt(), theme.colors.keyStroke)
        assertEquals(0xCC444444.toInt(), theme.colors.keyText)
        assertEquals(0xFF555555.toInt(), theme.colors.pressedFill)
        assertEquals(0xFF666666.toInt(), theme.colors.activeModifierFill)
        assertEquals("content://theme/background", theme.backgroundImageUri)
        assertEquals(0.5f, theme.backgroundImageOpacity, 0.001f)
    }

    @Test
    fun cloneFromThemeStoresOpaqueColorsWithoutImage() {
        val config = CustomThemeConfig.fromTheme(ThemePreset.LEET_GREEN, KeyboardTheme.leetGreen)

        assertEquals(ThemePreset.LEET_GREEN, config.basePreset)
        assertEquals(KeyboardTheme.leetGreen.colors.background, config.backgroundColor)
        assertEquals(KeyboardTheme.leetGreen.colors.keyFill, config.keyFillColor)
        assertEquals(KeyboardTheme.leetGreen.colors.keyStroke, config.keyStrokeColor)
        assertEquals(KeyboardTheme.leetGreen.colors.keyText, config.keyTextColor)
        assertNull(config.backgroundImageUri)
    }
}
