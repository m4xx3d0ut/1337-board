package org.leetboard.ime.model

import android.graphics.Color

enum class ThemePreset {
    SYSTEM,
    LIGHT,
    DARK,
    ALT_DARK,
    TERMINAL,
    CYBERPUNK,
    LEET_GREEN,
    HIGH_CONTRAST,
}

data class KeyboardGeometry(
    val keyRadiusPx: Float = 10f,
    val borderWidthPx: Float = 2f,
    val keyGapPx: Float = 6f,
    val outerMarginPx: Float = 8f,
    val rowGapPx: Float = 6f,
)

data class KeyboardColors(
    val background: Int,
    val keyFill: Int,
    val keyStroke: Int,
    val keyText: Int,
    val pressedFill: Int,
    val activeModifierFill: Int,
)

data class KeyboardTheme(
    val preset: ThemePreset,
    val colors: KeyboardColors,
    val portrait: KeyboardGeometry = KeyboardGeometry(),
    val landscape: KeyboardGeometry = KeyboardGeometry(
        keyRadiusPx = 8f,
        borderWidthPx = 2f,
        keyGapPx = 5f,
        outerMarginPx = 8f,
        rowGapPx = 5f,
    ),
) {
    companion object {
        val leetGreen = KeyboardTheme(
            preset = ThemePreset.LEET_GREEN,
            colors = KeyboardColors(
                background = Color.rgb(5, 8, 7),
                keyFill = Color.rgb(12, 20, 15),
                keyStroke = Color.rgb(0, 230, 118),
                keyText = Color.rgb(230, 244, 234),
                pressedFill = Color.rgb(0, 92, 45),
                activeModifierFill = Color.rgb(0, 160, 80),
            ),
        )
    }
}

