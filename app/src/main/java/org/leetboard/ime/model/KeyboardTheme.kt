package org.leetboard.ime.model

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
    val keyboardHeightPercent: Float = 34f,
    val keyRadiusDp: Float = 10f,
    val borderWidthDp: Float = 2f,
    val keyGapDp: Float = 6f,
    val horizontalMarginDp: Float = 16f,
    val bottomMarginDp: Float = 12f,
    val rowGapDp: Float = 6f,
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
        keyboardHeightPercent = 48f,
        keyRadiusDp = 8f,
        borderWidthDp = 2f,
        keyGapDp = 5f,
        horizontalMarginDp = 24f,
        bottomMarginDp = 12f,
        rowGapDp = 5f,
    ),
) {
    companion object {
        val leetGreen = KeyboardTheme(
            preset = ThemePreset.LEET_GREEN,
            colors = KeyboardColors(
                background = 0xFF050807.toInt(),
                keyFill = 0xFF0C140F.toInt(),
                keyStroke = 0xFF00E676.toInt(),
                keyText = 0xFFE6F4EA.toInt(),
                pressedFill = 0xFF005C2D.toInt(),
                activeModifierFill = 0xFF00A050.toInt(),
            ),
        )
    }
}
