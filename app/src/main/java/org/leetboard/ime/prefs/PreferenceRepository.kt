package org.leetboard.ime.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.Reader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.normalizeGlidePathSignature
import org.leetboard.ime.engine.normalizeWord
import org.leetboard.ime.model.CustomThemeConfig
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyDisplayOverride
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.ThemePreset
import org.leetboard.ime.model.keyActionFromPreferenceValue
import org.leetboard.ime.model.opaque
import org.leetboard.ime.model.toPreferenceValue

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard_settings")

class PreferenceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.keyboardDataStore

    val preferences: Flow<KeyboardPreferences> = dataStore.data.map { values ->
        val glideCorrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections])
        KeyboardPreferences(
            layoutId = values[Keys.layoutId] ?: KeyboardPreferences.defaults().layoutId,
            themePreset = values[Keys.themePreset]?.let(::themePresetFromName) ?: ThemePreset.LEET_GREEN,
            portraitGeometry = geometry(values, "portrait", KeyboardGeometry()),
            landscapeGeometry = geometry(values, "landscape", KeyboardPreferences.defaults().landscapeGeometry),
            hiddenOptionalKeyIds = values[Keys.hiddenOptionalKeys].orEmpty(),
            slotActions = Keys.actionSlotKeys.mapNotNull { (slot, key) ->
                keyActionFromPreferenceValue(values[key])?.let { action -> slot to action }
            }.toMap(),
            keyDisplayOverrides = keyDisplayOverridesFromPreferenceValues(values[Keys.keyDisplayOverrides].orEmpty()),
            escTouchMode = values[Keys.escTouchMode]?.let(::escTouchModeFromName) ?: EscTouchMode.REGULAR,
            keyLabelStyle = KeyLabelStyle(
                primaryTextSizeSp = values[Keys.primaryTextSizeSp] ?: KeyboardPreferences.defaults().keyLabelStyle.primaryTextSizeSp,
                secondaryTextSizeSp = values[Keys.secondaryTextSizeSp] ?: KeyboardPreferences.defaults().keyLabelStyle.secondaryTextSizeSp,
                fontWeight = values[Keys.fontWeight] ?: KeyboardPreferences.defaults().keyLabelStyle.fontWeight,
                labelOpacity = values[Keys.labelOpacity] ?: KeyboardPreferences.defaults().keyLabelStyle.labelOpacity,
                uppercaseOnShift = values[Keys.uppercaseOnShift] ?: KeyboardPreferences.defaults().keyLabelStyle.uppercaseOnShift,
            ),
            numpadToggleEnabled = values[Keys.numpadToggleEnabled] ?: true,
            keyPreviewEnabled = values[Keys.keyPreviewEnabled] ?: true,
            stickyModifiersEnabled = values[Keys.stickyModifiersEnabled] ?: true,
            shiftCapsLockEnabled = values[Keys.shiftCapsLockEnabled] ?: true,
            edgeKeyWidthScale = values[Keys.edgeKeyWidthScale] ?: KeyboardPreferences.defaults().edgeKeyWidthScale,
            gestureTypingEnabled = values[Keys.gestureTypingEnabled] ?: false,
            autoCapAfterPeriodEnabled = values[Keys.autoCapAfterPeriodEnabled] ?: true,
            swipeUpActionsEnabled = values[Keys.swipeUpActionsEnabled] ?: true,
            speechInputEnabled = values[Keys.speechInputEnabled] ?: false,
            glideImportedWordCount = values[Keys.glideImportedWordCount] ?: importedGlideWordsFile().lineCountOrZero(),
            glideCorrectionLearningEnabled = values[Keys.glideCorrectionLearningEnabled]
                ?: KeyboardPreferences.defaults().glideCorrectionLearningEnabled,
            glideCorrections = glideCorrections,
            glidePreferShorterWords = values[Keys.glidePreferShorterWords] ?: false,
            glideStrictFirstLastLetter = values[Keys.glideStrictFirstLastLetter] ?: true,
            glidePathTolerance = values[Keys.glidePathTolerance]?.let(::glidePathToleranceFromName)
                ?: GlidePathTolerance.BALANCED,
            glideImportedWordsPriority = values[Keys.glideImportedWordsPriority]?.let(::glideImportedWordsPriorityFromName)
                ?: GlideImportedWordsPriority.NORMAL,
            glideRawFallbackMode = values[Keys.glideRawFallbackMode]?.let(::glideRawFallbackModeFromName)
                ?: GlideRawFallbackMode.SHORT_ONLY,
            customTheme = customTheme(values),
        )
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        dataStore.edit { values -> values[Keys.themePreset] = preset.name }
    }

    suspend fun setCustomTheme(config: CustomThemeConfig) {
        dataStore.edit { values -> writeCustomTheme(values, config) }
    }

    suspend fun setCustomThemeColor(field: CustomThemeColorField, color: Int) {
        dataStore.edit { values ->
            when (field) {
                CustomThemeColorField.BACKGROUND -> values[Keys.customBackgroundColor] = color.opaque()
                CustomThemeColorField.KEY_FILL -> values[Keys.customKeyFillColor] = color.opaque()
                CustomThemeColorField.KEY_STROKE -> values[Keys.customKeyStrokeColor] = color.opaque()
                CustomThemeColorField.KEY_TEXT -> values[Keys.customKeyTextColor] = color.opaque()
                CustomThemeColorField.PRESSED_FILL -> values[Keys.customPressedFillColor] = color.opaque()
                CustomThemeColorField.ACTIVE_MODIFIER_FILL -> values[Keys.customActiveModifierFillColor] = color.opaque()
            }
            values[Keys.themePreset] = ThemePreset.CUSTOM.name
        }
    }

    suspend fun setCustomThemeOpacity(field: CustomThemeOpacityField, opacity: Float) {
        dataStore.edit { values ->
            val next = opacity.coerceIn(0f, 1f)
            when (field) {
                CustomThemeOpacityField.BACKGROUND_IMAGE -> values[Keys.customBackgroundImageOpacity] = next
                CustomThemeOpacityField.KEY_FILL -> values[Keys.customKeyFillOpacity] = next
                CustomThemeOpacityField.KEY_STROKE -> values[Keys.customKeyStrokeOpacity] = next
                CustomThemeOpacityField.KEY_TEXT -> values[Keys.customKeyTextOpacity] = next
            }
            values[Keys.themePreset] = ThemePreset.CUSTOM.name
        }
    }

    suspend fun setCustomThemeBackgroundImage(uri: String?) {
        dataStore.edit { values ->
            if (uri.isNullOrBlank()) {
                values.remove(Keys.customBackgroundImageUri)
            } else {
                values[Keys.customBackgroundImageUri] = uri
            }
            values[Keys.themePreset] = ThemePreset.CUSTOM.name
        }
    }

    suspend fun resetCustomTheme() {
        dataStore.edit { values ->
            removeCustomTheme(values)
            values[Keys.themePreset] = ThemePreset.LEET_GREEN.name
        }
    }

    suspend fun setLayoutId(layoutId: String) {
        dataStore.edit { values -> values[Keys.layoutId] = layoutId }
    }

    suspend fun setOptionalKeyHidden(keyId: String, hidden: Boolean) {
        dataStore.edit { values ->
            val next = values[Keys.hiddenOptionalKeys].orEmpty().toMutableSet()
            if (hidden) {
                next += keyId
            } else {
                next -= keyId
            }
            values[Keys.hiddenOptionalKeys] = next
        }
    }

    suspend fun setSlotAction(slotId: String, action: KeyAction?) {
        val key = Keys.actionSlotKeys[slotId] ?: return
        dataStore.edit { values ->
            if (action == null) {
                values.remove(key)
            } else {
                values[key] = action.toPreferenceValue()
            }
        }
    }

    suspend fun setKeyDisplayOverride(keyId: String, override: KeyDisplayOverride?) {
        dataStore.edit { values ->
            val next = keyDisplayOverridesFromPreferenceValues(values[Keys.keyDisplayOverrides].orEmpty()).toMutableMap()
            if (override == null || override.isEmpty()) {
                next -= keyId
            } else {
                next[keyId] = override
            }
            values[Keys.keyDisplayOverrides] = next.map { (id, value) ->
                keyDisplayOverrideToPreferenceValue(id, value)
            }.toSet()
        }
    }

    suspend fun setEscTouchMode(mode: EscTouchMode) {
        dataStore.edit { values -> values[Keys.escTouchMode] = mode.name }
    }

    suspend fun setKeyLabelStyleValue(field: KeyLabelStyleField, value: Float) {
        val key = when (field) {
            KeyLabelStyleField.PRIMARY_TEXT_SIZE -> Keys.primaryTextSizeSp
            KeyLabelStyleField.SECONDARY_TEXT_SIZE -> Keys.secondaryTextSizeSp
            KeyLabelStyleField.FONT_WEIGHT -> Keys.fontWeight
            KeyLabelStyleField.LABEL_OPACITY -> Keys.labelOpacity
        }
        dataStore.edit { values -> values[key] = value.coerceIn(field.minimum, field.maximum) }
    }

    suspend fun setUppercaseOnShift(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.uppercaseOnShift] = enabled }
    }

    suspend fun setStickyModifiersEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.stickyModifiersEnabled] = enabled }
    }

    suspend fun setShiftCapsLockEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.shiftCapsLockEnabled] = enabled }
    }

    suspend fun setEdgeKeyWidthScale(value: Float) {
        dataStore.edit { values -> values[Keys.edgeKeyWidthScale] = value.coerceIn(MIN_EDGE_KEY_SCALE, MAX_EDGE_KEY_SCALE) }
    }

    suspend fun setNumpadToggleEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.numpadToggleEnabled] = enabled }
    }

    suspend fun setKeyPreviewEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.keyPreviewEnabled] = enabled }
    }

    suspend fun setGestureTypingEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.gestureTypingEnabled] = enabled }
    }

    suspend fun setGlideCorrectionLearningEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.glideCorrectionLearningEnabled] = enabled }
    }

    suspend fun recordGlideCorrection(pathSignature: String, word: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        val normalizedWord = normalizeWord(word) ?: return
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            corrections.remove(normalizedPathSignature)
            while (corrections.size >= MAX_GLIDE_CORRECTIONS) {
                corrections.remove(corrections.keys.first())
            }
            corrections[normalizedPathSignature] = normalizedWord
            values[Keys.glideCorrections] = glideCorrectionsToPreferenceValue(corrections)
        }
    }

    suspend fun setGlideCorrection(pathSignature: String, word: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        val normalizedWord = normalizeWord(word) ?: return
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            corrections[normalizedPathSignature] = normalizedWord
            values[Keys.glideCorrections] = glideCorrectionsToPreferenceValue(corrections)
        }
    }

    suspend fun removeGlideCorrection(pathSignature: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            corrections.remove(normalizedPathSignature)
            if (corrections.isEmpty()) {
                values.remove(Keys.glideCorrections)
            } else {
                values[Keys.glideCorrections] = glideCorrectionsToPreferenceValue(corrections)
            }
        }
    }

    suspend fun clearGlideCorrections() {
        dataStore.edit { values -> values.remove(Keys.glideCorrections) }
    }

    suspend fun setGlidePreferShorterWords(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.glidePreferShorterWords] = enabled }
    }

    suspend fun setGlideStrictFirstLastLetter(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.glideStrictFirstLastLetter] = enabled }
    }

    suspend fun setGlidePathTolerance(tolerance: GlidePathTolerance) {
        dataStore.edit { values -> values[Keys.glidePathTolerance] = tolerance.name }
    }

    suspend fun setGlideImportedWordsPriority(priority: GlideImportedWordsPriority) {
        dataStore.edit { values -> values[Keys.glideImportedWordsPriority] = priority.name }
    }

    suspend fun setGlideRawFallbackMode(mode: GlideRawFallbackMode) {
        dataStore.edit { values -> values[Keys.glideRawFallbackMode] = mode.name }
    }

    suspend fun setAutoCapAfterPeriodEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.autoCapAfterPeriodEnabled] = enabled }
    }

    suspend fun setSwipeUpActionsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.swipeUpActionsEnabled] = enabled }
    }

    suspend fun setSpeechInputEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechInputEnabled] = enabled }
    }

    suspend fun importGlideWords(reader: Reader): Int {
        val words = reader.useLines { lines ->
            lines
                .mapNotNull(::normalizeImportedWord)
                .distinct()
                .take(MAX_IMPORTED_GLIDE_WORDS)
                .toList()
        }
        importedGlideWordsFile().writeText(words.joinToString(separator = "\n"))
        dataStore.edit { values -> values[Keys.glideImportedWordCount] = words.size }
        return words.size
    }

    suspend fun clearImportedGlideWords() {
        importedGlideWordsFile().delete()
        dataStore.edit { values -> values[Keys.glideImportedWordCount] = 0 }
    }

    suspend fun resetToDefaults() {
        importedGlideWordsFile().delete()
        dataStore.edit { values -> values.clear() }
    }

    suspend fun setGeometryValue(
        orientation: GeometryOrientation,
        field: GeometryField,
        value: Float,
    ) {
        val keyPrefix = when (orientation) {
            GeometryOrientation.PORTRAIT -> "portrait"
            GeometryOrientation.LANDSCAPE -> "landscape"
        }
        val key = Keys.geometryKey(keyPrefix, field)
        dataStore.edit { values -> values[key] = value.coerceIn(field.minimum, field.maximum) }
    }

    private fun geometry(values: Preferences, prefix: String, defaults: KeyboardGeometry): KeyboardGeometry {
        return KeyboardGeometry(
            keyboardHeightPercent = values[Keys.geometryKey(prefix, GeometryField.KEYBOARD_HEIGHT_PERCENT)]
                ?: defaults.keyboardHeightPercent,
            keyRadiusDp = values[Keys.geometryKey(prefix, GeometryField.KEY_RADIUS)] ?: defaults.keyRadiusDp,
            borderWidthDp = values[Keys.geometryKey(prefix, GeometryField.BORDER_WIDTH)] ?: defaults.borderWidthDp,
            keyGapDp = values[Keys.geometryKey(prefix, GeometryField.KEY_GAP)] ?: defaults.keyGapDp,
            horizontalMarginDp = values[Keys.geometryKey(prefix, GeometryField.HORIZONTAL_MARGIN)]
                ?: defaults.horizontalMarginDp,
            bottomMarginDp = values[Keys.geometryKey(prefix, GeometryField.BOTTOM_MARGIN)]
                ?: values[Keys.legacyOuterMarginKey(prefix)]
                ?: defaults.bottomMarginDp,
            rowGapDp = values[Keys.geometryKey(prefix, GeometryField.ROW_GAP)] ?: defaults.rowGapDp,
        )
    }

    private fun themePresetFromName(name: String): ThemePreset? {
        return ThemePreset.entries.firstOrNull { it.name == name }
    }

    private fun escTouchModeFromName(name: String): EscTouchMode? {
        return EscTouchMode.entries.firstOrNull { it.name == name }
    }

    private fun glidePathToleranceFromName(name: String): GlidePathTolerance? {
        return GlidePathTolerance.entries.firstOrNull { it.name == name }
    }

    private fun glideImportedWordsPriorityFromName(name: String): GlideImportedWordsPriority? {
        return GlideImportedWordsPriority.entries.firstOrNull { it.name == name }
    }

    private fun glideRawFallbackModeFromName(name: String): GlideRawFallbackMode? {
        return GlideRawFallbackMode.entries.firstOrNull { it.name == name }
    }

    private fun customTheme(values: Preferences): CustomThemeConfig {
        val defaults = CustomThemeConfig()
        return CustomThemeConfig(
            basePreset = values[Keys.customBasePreset]?.let(::themePresetFromName)?.takeUnless { it == ThemePreset.CUSTOM }
                ?: defaults.basePreset,
            backgroundColor = values[Keys.customBackgroundColor]?.opaque() ?: defaults.backgroundColor,
            keyFillColor = values[Keys.customKeyFillColor]?.opaque() ?: defaults.keyFillColor,
            keyStrokeColor = values[Keys.customKeyStrokeColor]?.opaque() ?: defaults.keyStrokeColor,
            keyTextColor = values[Keys.customKeyTextColor]?.opaque() ?: defaults.keyTextColor,
            pressedFillColor = values[Keys.customPressedFillColor]?.opaque() ?: defaults.pressedFillColor,
            activeModifierFillColor = values[Keys.customActiveModifierFillColor]?.opaque()
                ?: defaults.activeModifierFillColor,
            backgroundImageUri = values[Keys.customBackgroundImageUri],
            backgroundImageOpacity = values[Keys.customBackgroundImageOpacity] ?: defaults.backgroundImageOpacity,
            keyFillOpacity = values[Keys.customKeyFillOpacity] ?: defaults.keyFillOpacity,
            keyStrokeOpacity = values[Keys.customKeyStrokeOpacity] ?: defaults.keyStrokeOpacity,
            keyTextOpacity = values[Keys.customKeyTextOpacity] ?: defaults.keyTextOpacity,
        )
    }

    private fun writeCustomTheme(values: MutablePreferences, config: CustomThemeConfig) {
        values[Keys.customBasePreset] = config.basePreset.name
        values[Keys.customBackgroundColor] = config.backgroundColor.opaque()
        values[Keys.customKeyFillColor] = config.keyFillColor.opaque()
        values[Keys.customKeyStrokeColor] = config.keyStrokeColor.opaque()
        values[Keys.customKeyTextColor] = config.keyTextColor.opaque()
        values[Keys.customPressedFillColor] = config.pressedFillColor.opaque()
        values[Keys.customActiveModifierFillColor] = config.activeModifierFillColor.opaque()
        if (config.backgroundImageUri.isNullOrBlank()) {
            values.remove(Keys.customBackgroundImageUri)
        } else {
            values[Keys.customBackgroundImageUri] = config.backgroundImageUri
        }
        values[Keys.customBackgroundImageOpacity] = config.backgroundImageOpacity.coerceIn(0f, 1f)
        values[Keys.customKeyFillOpacity] = config.keyFillOpacity.coerceIn(0f, 1f)
        values[Keys.customKeyStrokeOpacity] = config.keyStrokeOpacity.coerceIn(0f, 1f)
        values[Keys.customKeyTextOpacity] = config.keyTextOpacity.coerceIn(0f, 1f)
        values[Keys.themePreset] = ThemePreset.CUSTOM.name
    }

    private fun removeCustomTheme(values: MutablePreferences) {
        values.remove(Keys.customBasePreset)
        values.remove(Keys.customBackgroundColor)
        values.remove(Keys.customKeyFillColor)
        values.remove(Keys.customKeyStrokeColor)
        values.remove(Keys.customKeyTextColor)
        values.remove(Keys.customPressedFillColor)
        values.remove(Keys.customActiveModifierFillColor)
        values.remove(Keys.customBackgroundImageUri)
        values.remove(Keys.customBackgroundImageOpacity)
        values.remove(Keys.customKeyFillOpacity)
        values.remove(Keys.customKeyStrokeOpacity)
        values.remove(Keys.customKeyTextOpacity)
    }

    private fun keyDisplayOverridesFromPreferenceValues(values: Set<String>): Map<String, KeyDisplayOverride> {
        return values.mapNotNull { value ->
            val parts = value.split(DISPLAY_OVERRIDE_SEPARATOR, limit = 3)
            val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val icon = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { name ->
                KeyIcon.entries.firstOrNull { it.name == name }
            }
            id to KeyDisplayOverride(label = label, icon = icon)
        }.toMap()
    }

    private fun keyDisplayOverrideToPreferenceValue(keyId: String, override: KeyDisplayOverride): String {
        return listOf(
            keyId,
            override.label.orEmpty(),
            override.icon?.name.orEmpty(),
        ).joinToString(DISPLAY_OVERRIDE_SEPARATOR)
    }

    private object Keys {
        val layoutId = stringPreferencesKey("layout_id")
        val themePreset = stringPreferencesKey("theme_preset")
        val hiddenOptionalKeys = stringSetPreferencesKey("hidden_optional_keys")
        val keyDisplayOverrides = stringSetPreferencesKey("key_display_overrides")
        val escTouchMode = stringPreferencesKey("esc_touch_mode")
        val primaryTextSizeSp = floatPreferencesKey("primary_text_size_sp")
        val secondaryTextSizeSp = floatPreferencesKey("secondary_text_size_sp")
        val fontWeight = floatPreferencesKey("font_weight")
        val labelOpacity = floatPreferencesKey("label_opacity")
        val uppercaseOnShift = booleanPreferencesKey("uppercase_on_shift")
        val numpadToggleEnabled = booleanPreferencesKey("numpad_toggle_enabled")
        val keyPreviewEnabled = booleanPreferencesKey("key_preview_enabled")
        val stickyModifiersEnabled = booleanPreferencesKey("sticky_modifiers_enabled")
        val shiftCapsLockEnabled = booleanPreferencesKey("shift_caps_lock_enabled")
        val edgeKeyWidthScale = floatPreferencesKey("edge_key_width_scale")
        val gestureTypingEnabled = booleanPreferencesKey("gesture_typing_enabled")
        val autoCapAfterPeriodEnabled = booleanPreferencesKey("auto_cap_after_period_enabled")
        val swipeUpActionsEnabled = booleanPreferencesKey("swipe_up_actions_enabled")
        val speechInputEnabled = booleanPreferencesKey("speech_input_enabled")
        val glideImportedWordCount = intPreferencesKey("glide_imported_word_count")
        val glideCorrectionLearningEnabled = booleanPreferencesKey("glide_correction_learning_enabled")
        val glideCorrections = stringPreferencesKey("glide_corrections")
        val glidePreferShorterWords = booleanPreferencesKey("glide_prefer_shorter_words")
        val glideStrictFirstLastLetter = booleanPreferencesKey("glide_strict_first_last_letter")
        val glidePathTolerance = stringPreferencesKey("glide_path_tolerance")
        val glideImportedWordsPriority = stringPreferencesKey("glide_imported_words_priority")
        val glideRawFallbackMode = stringPreferencesKey("glide_raw_fallback_mode")
        val customBasePreset = stringPreferencesKey("custom_base_preset")
        val customBackgroundColor = intPreferencesKey("custom_background_color")
        val customKeyFillColor = intPreferencesKey("custom_key_fill_color")
        val customKeyStrokeColor = intPreferencesKey("custom_key_stroke_color")
        val customKeyTextColor = intPreferencesKey("custom_key_text_color")
        val customPressedFillColor = intPreferencesKey("custom_pressed_fill_color")
        val customActiveModifierFillColor = intPreferencesKey("custom_active_modifier_fill_color")
        val customBackgroundImageUri = stringPreferencesKey("custom_background_image_uri")
        val customBackgroundImageOpacity = floatPreferencesKey("custom_background_image_opacity")
        val customKeyFillOpacity = floatPreferencesKey("custom_key_fill_opacity")
        val customKeyStrokeOpacity = floatPreferencesKey("custom_key_stroke_opacity")
        val customKeyTextOpacity = floatPreferencesKey("custom_key_text_opacity")

        val actionSlotKeys = mapOf(
            "esc" to stringPreferencesKey("slot_esc_action"),
            "tab" to stringPreferencesKey("slot_tab_action"),
            "ctrl" to stringPreferencesKey("slot_ctrl_action"),
            "alt" to stringPreferencesKey("slot_alt_action"),
            "fn" to stringPreferencesKey("slot_fn_action"),
            "symbols" to stringPreferencesKey("slot_symbols_action"),
            "settings" to stringPreferencesKey("slot_settings_action"),
            "left" to stringPreferencesKey("slot_left_action"),
            "up" to stringPreferencesKey("slot_up_action"),
            "down" to stringPreferencesKey("slot_down_action"),
            "right" to stringPreferencesKey("slot_right_action"),
            "enter" to stringPreferencesKey("slot_enter_action"),
            "delete" to stringPreferencesKey("slot_delete_action"),
            "mic" to stringPreferencesKey("slot_mic_action"),
            "num_toggle" to stringPreferencesKey("slot_num_toggle_action"),
        )

        fun geometryKey(prefix: String, field: GeometryField): Preferences.Key<Float> {
            return floatPreferencesKey("${prefix}_${field.name.lowercase()}")
        }

        fun legacyOuterMarginKey(prefix: String): Preferences.Key<Float> {
            return floatPreferencesKey("${prefix}_outer_margin")
        }
    }

    private companion object {
        const val DISPLAY_OVERRIDE_SEPARATOR = "\t"
        const val MIN_EDGE_KEY_SCALE = 0.6f
        const val MAX_EDGE_KEY_SCALE = 1.1f
        const val IMPORTED_GLIDE_WORDS_FILE = "glide_words_user.txt"
        const val MAX_IMPORTED_GLIDE_WORDS = 5000
    }

    private fun importedGlideWordsFile() = appContext.filesDir.resolve(IMPORTED_GLIDE_WORDS_FILE)
}

fun glideCorrectionsFromPreferenceValue(value: String?): Map<String, String> {
    if (value.isNullOrBlank()) return emptyMap()
    val corrections = linkedMapOf<String, String>()
    value.lineSequence().forEach { line ->
        val parts = line.split(GLIDE_CORRECTION_SEPARATOR, limit = 2)
        val pathSignature = parts.getOrNull(0)?.let(::normalizeGlidePathSignature) ?: return@forEach
        val word = parts.getOrNull(1)?.let(::normalizeWord) ?: return@forEach
        corrections[pathSignature] = word
    }
    return corrections
}

fun glideCorrectionsToPreferenceValue(corrections: Map<String, String>): String {
    return corrections.entries
        .mapNotNull { (pathSignature, word) ->
            val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return@mapNotNull null
            val normalizedWord = normalizeWord(word) ?: return@mapNotNull null
            "$normalizedPathSignature$GLIDE_CORRECTION_SEPARATOR$normalizedWord"
        }
        .takeLast(MAX_GLIDE_CORRECTIONS)
        .joinToString(separator = "\n")
}

private fun normalizeImportedWord(value: String): String? {
    val normalized = value.trim().lowercase()
    return normalized.takeIf { word ->
        word.length in 2..24 && word.all { char -> char in 'a'..'z' }
    }
}

private const val GLIDE_CORRECTION_SEPARATOR = "\t"
private const val MAX_GLIDE_CORRECTIONS = 500

private fun java.io.File.lineCountOrZero(): Int {
    if (!isFile) return 0
    return useLines { lines -> lines.count() }
}

private val GeometryField.minimum: Float
    get() = when (this) {
        GeometryField.KEYBOARD_HEIGHT_PERCENT -> 15f
        else -> 0f
    }

private val GeometryField.maximum: Float
    get() = when (this) {
        GeometryField.KEYBOARD_HEIGHT_PERCENT -> 75f
        GeometryField.BORDER_WIDTH -> 8f
        GeometryField.HORIZONTAL_MARGIN,
        GeometryField.BOTTOM_MARGIN -> 40f
        else -> 32f
    }

private val KeyLabelStyleField.minimum: Float
    get() = when (this) {
        KeyLabelStyleField.LABEL_OPACITY -> 0.35f
        KeyLabelStyleField.FONT_WEIGHT -> 300f
        KeyLabelStyleField.PRIMARY_TEXT_SIZE -> 10f
        KeyLabelStyleField.SECONDARY_TEXT_SIZE -> 6f
    }

private val KeyLabelStyleField.maximum: Float
    get() = when (this) {
        KeyLabelStyleField.PRIMARY_TEXT_SIZE -> 24f
        KeyLabelStyleField.SECONDARY_TEXT_SIZE -> 16f
        KeyLabelStyleField.FONT_WEIGHT -> 900f
        KeyLabelStyleField.LABEL_OPACITY -> 1f
    }
