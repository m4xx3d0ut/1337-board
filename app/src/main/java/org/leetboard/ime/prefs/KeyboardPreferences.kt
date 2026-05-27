package org.leetboard.ime.prefs

import android.content.res.Configuration
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.GlideDwellSensitivity
import org.leetboard.ime.engine.GlideSpatialPrecision
import org.leetboard.ime.engine.GlideTypingOptions
import org.leetboard.ime.engine.GlideCorrectionEntry
import org.leetboard.ime.engine.DEFAULT_GLIDE_DWELL_ACTIVATION_THRESHOLD
import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.CustomThemeConfig
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyDisplayOverride
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.ThemePreset

const val DEFAULT_KEY_LONG_PRESS_DELAY_MS = 520
const val DEFAULT_SPECIAL_LONG_PRESS_DELAY_MS = 520

data class KeyboardPreferences(
    val layoutId: String = "qwerty5",
    val portraitLayoutId: String = "qwerty5",
    val landscapeLayoutId: String = "qwerty5",
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
    val keyLongPressDelayMs: Int = DEFAULT_KEY_LONG_PRESS_DELAY_MS,
    val specialLongPressDelayMs: Int = DEFAULT_SPECIAL_LONG_PRESS_DELAY_MS,
    val edgeKeyWidthScale: Float = 0.75f,
    val gestureTypingEnabled: Boolean = false,
    val typedSuggestionsEnabled: Boolean = true,
    val typedAutocorrectEnabled: Boolean = false,
    val autoCapAfterPeriodEnabled: Boolean = true,
    val swipeUpActionsEnabled: Boolean = true,
    val speechInputEnabled: Boolean = false,
    val glideImportedWordCount: Int = 0,
    val glideCorrectionLearningEnabled: Boolean = false,
    val glideCorrections: Map<String, GlideCorrectionEntry> = emptyMap(),
    val glidePreferShorterWords: Boolean = true,
    val glideStrictFirstLastLetter: Boolean = false,
    val glidePathTolerance: GlidePathTolerance = GlidePathTolerance.LOOSE,
    val glideSpatialPrecision: GlideSpatialPrecision = GlideSpatialPrecision.STANDARD,
    val glideDwellSensitivity: GlideDwellSensitivity = GlideDwellSensitivity.HIGH,
    val glideDwellActivationThreshold: Float = DEFAULT_GLIDE_DWELL_ACTIVATION_THRESHOLD,
    val glideImportedWordsPriority: GlideImportedWordsPriority = GlideImportedWordsPriority.HIGH,
    val glideRawFallbackMode: GlideRawFallbackMode = GlideRawFallbackMode.OFF,
    val glidePredictiveRankingEnabled: Boolean = true,
    val glideLearningResetRevision: Int = 0,
    val customTheme: CustomThemeConfig = CustomThemeConfig(),
) {
    fun customizationState(): CustomizationState {
        val hiddenKeys = buildSet {
            addAll(hiddenOptionalKeyIds)
            if (!numpadToggleEnabled) add("num_toggle")
            if (!speechInputEnabled) add("mic")
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
            spatialPrecision = glideSpatialPrecision,
            dwellSensitivity = glideDwellSensitivity,
            dwellActivationThreshold = glideDwellActivationThreshold,
            importedWordsPriority = glideImportedWordsPriority,
            rawPathFallbackMode = glideRawFallbackMode,
            importedWordCount = glideImportedWordCount,
            predictiveRankingEnabled = glidePredictiveRankingEnabled,
        )
    }

    companion object {
        fun defaults() = KeyboardPreferences()
    }
}

fun KeyboardPreferences.layoutIdForOrientation(orientation: Int): String {
    return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        landscapeLayoutId
    } else {
        portraitLayoutId
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
