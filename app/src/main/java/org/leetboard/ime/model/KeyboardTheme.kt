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
    CUSTOM,
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

data class CustomThemeConfig(
    val basePreset: ThemePreset = ThemePreset.LEET_GREEN,
    val backgroundColor: Int = KeyboardTheme.leetGreen.colors.background,
    val keyFillColor: Int = KeyboardTheme.leetGreen.colors.keyFill,
    val keyStrokeColor: Int = KeyboardTheme.leetGreen.colors.keyStroke,
    val keyTextColor: Int = KeyboardTheme.leetGreen.colors.keyText,
    val pressedFillColor: Int = KeyboardTheme.leetGreen.colors.pressedFill,
    val activeModifierFillColor: Int = KeyboardTheme.leetGreen.colors.activeModifierFill,
    val backgroundImageUri: String? = null,
    val backgroundImageOpacity: Float = 0.35f,
    val keyFillOpacity: Float = 1f,
    val keyStrokeOpacity: Float = 1f,
    val keyTextOpacity: Float = 1f,
) {
    companion object {
        fun fromTheme(basePreset: ThemePreset, theme: KeyboardTheme): CustomThemeConfig {
            return CustomThemeConfig(
                basePreset = basePreset.customBasePreset(),
                backgroundColor = theme.colors.background.opaque(),
                keyFillColor = theme.colors.keyFill.opaque(),
                keyStrokeColor = theme.colors.keyStroke.opaque(),
                keyTextColor = theme.colors.keyText.opaque(),
                pressedFillColor = theme.colors.pressedFill.opaque(),
                activeModifierFillColor = theme.colors.activeModifierFill.opaque(),
            )
        }
    }
}

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
    val backgroundImageUri: String? = null,
    val backgroundImageOpacity: Float = 0f,
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

fun ThemePreset.customBasePreset(): ThemePreset {
    return when (this) {
        ThemePreset.CUSTOM -> ThemePreset.LEET_GREEN
        else -> this
    }
}

fun Int.withOpacity(opacity: Float): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
    return (this and 0x00FFFFFF) or (alpha shl 24)
}

fun Int.opaque(): Int {
    return (this and 0x00FFFFFF) or 0xFF000000.toInt()
}
