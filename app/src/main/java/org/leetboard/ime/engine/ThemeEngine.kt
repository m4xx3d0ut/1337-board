package org.leetboard.ime.engine

import android.graphics.Color
import org.leetboard.ime.model.KeyboardColors
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.ThemePreset

class ThemeEngine {
    fun resolve(
        preset: ThemePreset,
        portrait: KeyboardGeometry? = null,
        landscape: KeyboardGeometry? = null,
    ): KeyboardTheme {
        val theme = when (preset) {
            ThemePreset.SYSTEM,
            ThemePreset.DARK -> dark(ThemePreset.DARK)
            ThemePreset.LIGHT -> KeyboardTheme(
                preset = ThemePreset.LIGHT,
                colors = KeyboardColors(
                    background = Color.rgb(246, 248, 247),
                    keyFill = Color.WHITE,
                    keyStroke = Color.rgb(93, 100, 96),
                    keyText = Color.rgb(14, 20, 17),
                    pressedFill = Color.rgb(210, 232, 220),
                    activeModifierFill = Color.rgb(184, 224, 204),
                ),
            )
            ThemePreset.ALT_DARK -> dark(ThemePreset.ALT_DARK, accent = Color.rgb(98, 177, 255))
            ThemePreset.TERMINAL -> dark(ThemePreset.TERMINAL, accent = Color.rgb(0, 230, 118))
            ThemePreset.CYBERPUNK -> dark(
                preset = ThemePreset.CYBERPUNK,
                background = Color.rgb(17, 9, 28),
                keyFill = Color.rgb(29, 20, 48),
                accent = Color.rgb(255, 46, 136),
                pressed = Color.rgb(37, 235, 255),
            )
            ThemePreset.LEET_GREEN -> KeyboardTheme.leetGreen
            ThemePreset.HIGH_CONTRAST -> dark(
                preset = ThemePreset.HIGH_CONTRAST,
                background = Color.BLACK,
                keyFill = Color.rgb(20, 20, 20),
                accent = Color.WHITE,
                text = Color.WHITE,
            )
        }
        return theme.copy(
            portrait = portrait ?: theme.portrait,
            landscape = landscape ?: theme.landscape,
        )
    }

    private fun dark(
        preset: ThemePreset,
        background: Int = Color.rgb(8, 11, 10),
        keyFill: Int = Color.rgb(25, 31, 28),
        accent: Int = Color.rgb(0, 230, 118),
        pressed: Int = Color.rgb(0, 92, 45),
        text: Int = Color.rgb(230, 244, 234),
    ): KeyboardTheme {
        return KeyboardTheme(
            preset = preset,
            colors = KeyboardColors(
                background = background,
                keyFill = keyFill,
                keyStroke = accent,
                keyText = text,
                pressedFill = pressed,
                activeModifierFill = accent,
            ),
        )
    }
}
