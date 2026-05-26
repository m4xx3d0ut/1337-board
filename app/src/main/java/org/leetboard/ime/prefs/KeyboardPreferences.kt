package org.leetboard.ime.prefs

import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.GlideTypingOptions
import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.CustomThemeConfig
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyDisplayOverride
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.ThemePreset

data class KeyboardPreferences(
    val layoutId: String = "qwerty5",
    val themePreset: ThemePreset = ThemePreset.LEET_GREEN,
    val portraitGeometry: KeyboardGeometry = KeyboardGeometry(),
    val landscapeGeometry: KeyboardGeometry = KeyboardTheme.leetGreen.landscape,
    val hiddenOptionalKeyIds: Set<String> = emptySet(),
    val slotActions: Map<String, KeyAction> = emptyMap(),
    val keyDisplayOverrides: Map<String, KeyDisplayOverride> = emptyMap(),
    val escTouchMode: EscTouchMode = EscTouchMode.REGULAR,
    val keyLabelStyle: KeyLabelStyle = KeyLabelStyle(),
    val numpadToggleEnabled: Boolean = true,
    val keyPreviewEnabled: Boolean = true,
    val stickyModifiersEnabled: Boolean = true,
    val shiftCapsLockEnabled: Boolean = true,
    val edgeKeyWidthScale: Float = 0.75f,
    val gestureTypingEnabled: Boolean = false,
    val autoCapAfterPeriodEnabled: Boolean = true,
    val swipeUpActionsEnabled: Boolean = true,
    val speechInputEnabled: Boolean = false,
    val glideImportedWordCount: Int = 0,
    val glideCorrectionLearningEnabled: Boolean = true,
    val glideCorrections: Map<String, String> = emptyMap(),
    val glidePreferShorterWords: Boolean = false,
    val glideStrictFirstLastLetter: Boolean = true,
    val glidePathTolerance: GlidePathTolerance = GlidePathTolerance.BALANCED,
    val glideImportedWordsPriority: GlideImportedWordsPriority = GlideImportedWordsPriority.NORMAL,
    val glideRawFallbackMode: GlideRawFallbackMode = GlideRawFallbackMode.SHORT_ONLY,
    val customTheme: CustomThemeConfig = CustomThemeConfig(),
) {
    fun customizationState(): CustomizationState {
        val hiddenKeys = buildSet {
            addAll(hiddenOptionalKeyIds)
            if (!numpadToggleEnabled) add("num_toggle")
        }
        return CustomizationState(
            hiddenOptionalKeyIds = hiddenKeys,
            slotActions = slotActions,
            keyDisplayOverrides = keyDisplayOverrides,
            escTouchMode = escTouchMode,
        )
    }

    fun glideTypingOptions(): GlideTypingOptions {
        return GlideTypingOptions(
            preferShorterWords = glidePreferShorterWords,
            strictFirstLastLetter = glideStrictFirstLastLetter,
            pathTolerance = glidePathTolerance,
            importedWordsPriority = glideImportedWordsPriority,
            rawPathFallbackMode = glideRawFallbackMode,
            importedWordCount = glideImportedWordCount,
        )
    }

    companion object {
        fun defaults() = KeyboardPreferences()
    }
}

enum class GeometryOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class GeometryField {
    KEYBOARD_HEIGHT_PERCENT,
    KEY_RADIUS,
    BORDER_WIDTH,
    KEY_GAP,
    HORIZONTAL_MARGIN,
    BOTTOM_MARGIN,
    ROW_GAP,
}

enum class KeyLabelStyleField {
    PRIMARY_TEXT_SIZE,
    SECONDARY_TEXT_SIZE,
    FONT_WEIGHT,
    LABEL_OPACITY,
}

enum class CustomThemeColorField {
    BACKGROUND,
    KEY_FILL,
    KEY_STROKE,
    KEY_TEXT,
    PRESSED_FILL,
    ACTIVE_MODIFIER_FILL,
}

enum class CustomThemeOpacityField {
    BACKGROUND_IMAGE,
    KEY_FILL,
    KEY_STROKE,
    KEY_TEXT,
}

data class LayoutOption(
    val id: String,
    val label: String,
)

val defaultLayoutOptions = listOf(
    LayoutOption("qwerty5", "HK-style five-row PC"),
    LayoutOption("qwerty4", "HK-style four-row phone"),
    LayoutOption("compact5", "HK-style compact five-row"),
)
