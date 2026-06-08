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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.leetboard.ime.engine.DEFAULT_GLIDE_DWELL_ACTIVATION_THRESHOLD
import org.leetboard.ime.engine.GlideCorrectionEntry
import org.leetboard.ime.engine.GlideDwellSensitivity
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.GlideSpatialPrecision
import org.leetboard.ime.engine.GlideUserLanguageModel
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
        val legacyLayoutId = values[Keys.layoutId] ?: KeyboardPreferences.defaults().layoutId
        val speechCompleteSilenceMs = values[Keys.speechCompleteSilenceMs]?.coerceIn(
            MIN_SPEECH_SILENCE_MS,
            MAX_SPEECH_SILENCE_MS,
        ) ?: KeyboardPreferences.defaults().speechCompleteSilenceMs
        val speechPossibleSilenceMs = values[Keys.speechPossibleSilenceMs]?.coerceIn(
            MIN_SPEECH_SILENCE_MS,
            speechCompleteSilenceMs,
        ) ?: KeyboardPreferences.defaults().speechPossibleSilenceMs.coerceAtMost(speechCompleteSilenceMs)
        KeyboardPreferences(
            layoutId = legacyLayoutId,
            portraitLayoutId = values[Keys.portraitLayoutId] ?: legacyLayoutId,
            landscapeLayoutId = values[Keys.landscapeLayoutId] ?: legacyLayoutId,
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
            keyHapticsEnabled = values[Keys.keyHapticsEnabled]
                ?: KeyboardPreferences.defaults().keyHapticsEnabled,
            keyLongPressDelayMs = values[Keys.keyLongPressDelayMs]
                ?: KeyboardPreferences.defaults().keyLongPressDelayMs,
            specialLongPressDelayMs = values[Keys.specialLongPressDelayMs]
                ?: values[Keys.fnLongPressDelayMs]
                ?: KeyboardPreferences.defaults().specialLongPressDelayMs,
            edgeKeyWidthScale = values[Keys.edgeKeyWidthScale] ?: KeyboardPreferences.defaults().edgeKeyWidthScale,
            compactBottomControlsRightHandEnabled = values[Keys.compactBottomControlsRightHandEnabled]
                ?: values[Keys.legacyFourRowRightHandControlsEnabled]
                ?: KeyboardPreferences.defaults().compactBottomControlsRightHandEnabled,
            gestureTypingEnabled = values[Keys.gestureTypingEnabled] ?: false,
            typedSuggestionsEnabled = values[Keys.typedSuggestionsEnabled]
                ?: KeyboardPreferences.defaults().typedSuggestionsEnabled,
            typedAutocorrectEnabled = values[Keys.typedAutocorrectEnabled]
                ?: KeyboardPreferences.defaults().typedAutocorrectEnabled,
            autoCapAfterPeriodEnabled = values[Keys.autoCapAfterPeriodEnabled] ?: true,
            swipeUpActionsEnabled = values[Keys.swipeUpActionsEnabled] ?: true,
            speechInputEnabled = values[Keys.speechInputEnabled] ?: false,
            speechPushToTalkEnabled = values[Keys.speechPushToTalkEnabled]
                ?: KeyboardPreferences.defaults().speechPushToTalkEnabled,
            speechSmartCleanupEnabled = values[Keys.speechSmartCleanupEnabled]
                ?: KeyboardPreferences.defaults().speechSmartCleanupEnabled,
            speechAutoSpacingEnabled = values[Keys.speechAutoSpacingEnabled]
                ?: KeyboardPreferences.defaults().speechAutoSpacingEnabled,
            speechAutoCapAfterPunctuationEnabled = values[Keys.speechAutoCapAfterPunctuationEnabled]
                ?: KeyboardPreferences.defaults().speechAutoCapAfterPunctuationEnabled,
            speechAutoCapNamesEnabled = values[Keys.speechAutoCapNamesEnabled]
                ?: KeyboardPreferences.defaults().speechAutoCapNamesEnabled,
            speechSpokenPunctuationEnabled = values[Keys.speechSpokenPunctuationEnabled]
                ?: KeyboardPreferences.defaults().speechSpokenPunctuationEnabled,
            speechCustomNames = normalizeSpeechCustomNames(values[Keys.speechCustomNames].orEmpty()),
            speechCompleteSilenceMs = speechCompleteSilenceMs,
            speechPossibleSilenceMs = speechPossibleSilenceMs,
            glideImportedWordCount = values[Keys.glideImportedWordCount] ?: importedGlideWordsFile().lineCountOrZero(),
            glideCorrectionLearningEnabled = values[Keys.glideCorrectionLearningEnabled]
                ?: KeyboardPreferences.defaults().glideCorrectionLearningEnabled,
            glideCorrections = glideCorrections,
            glidePreferShorterWords = values[Keys.glidePreferShorterWords]
                ?: KeyboardPreferences.defaults().glidePreferShorterWords,
            glideStrictFirstLastLetter = values[Keys.glideStrictFirstLastLetter]
                ?: KeyboardPreferences.defaults().glideStrictFirstLastLetter,
            glidePathTolerance = values[Keys.glidePathTolerance]?.let(::glidePathToleranceFromName)
                ?: KeyboardPreferences.defaults().glidePathTolerance,
            glideSpatialPrecision = values[Keys.glideSpatialPrecision]?.let(::glideSpatialPrecisionFromName)
                ?: KeyboardPreferences.defaults().glideSpatialPrecision,
            glideDwellSensitivity = values[Keys.glideDwellSensitivity]?.let(::glideDwellSensitivityFromName)
                ?: KeyboardPreferences.defaults().glideDwellSensitivity,
            glideDwellActivationThreshold = values[Keys.glideDwellActivationThreshold]
                ?: KeyboardPreferences.defaults().glideDwellActivationThreshold,
            glideImportedWordsPriority = values[Keys.glideImportedWordsPriority]?.let(::glideImportedWordsPriorityFromName)
                ?: KeyboardPreferences.defaults().glideImportedWordsPriority,
            glideRawFallbackMode = values[Keys.glideRawFallbackMode]?.let(::glideRawFallbackModeFromName)
                ?: KeyboardPreferences.defaults().glideRawFallbackMode,
            glidePredictiveRankingEnabled = values[Keys.glidePredictiveRankingEnabled]
                ?: KeyboardPreferences.defaults().glidePredictiveRankingEnabled,
            glideLearningResetRevision = values[Keys.glideLearningResetRevision]
                ?: KeyboardPreferences.defaults().glideLearningResetRevision,
            customTheme = customTheme(values),
            bluetoothRemoteEnabled = values[Keys.bluetoothRemoteEnabled]
                ?: KeyboardPreferences.defaults().bluetoothRemoteEnabled,
            bluetoothActiveDeviceAddress = values[Keys.bluetoothActiveDeviceAddress]?.takeIf { it.isNotBlank() },
            bluetoothDeviceSlotAddresses = bluetoothDeviceSlotAddresses(values),
            bluetoothTrackpadEnabled = values[Keys.bluetoothTrackpadEnabled]
                ?: KeyboardPreferences.defaults().bluetoothTrackpadEnabled,
            bluetoothTrackpadPlacement = values[Keys.bluetoothTrackpadPlacement]?.let(::bluetoothTrackpadPlacementFromName)
                ?: KeyboardPreferences.defaults().bluetoothTrackpadPlacement,
            bluetoothTrackpadHeightPercent = values[Keys.bluetoothTrackpadHeightPercent]?.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
                MAX_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
            ) ?: KeyboardPreferences.defaults().bluetoothTrackpadHeightPercent,
            bluetoothTrackpadSensitivity = values[Keys.bluetoothTrackpadSensitivity]?.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            ) ?: KeyboardPreferences.defaults().bluetoothTrackpadSensitivity,
            bluetoothTrackpadScrollSensitivity = values[Keys.bluetoothTrackpadScrollSensitivity]?.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            ) ?: KeyboardPreferences.defaults().bluetoothTrackpadScrollSensitivity,
            bluetoothTrackpadInvertScrollEnabled = values[Keys.bluetoothTrackpadInvertScrollEnabled]
                ?: KeyboardPreferences.defaults().bluetoothTrackpadInvertScrollEnabled,
            bluetoothTrackpadTapToClickEnabled = values[Keys.bluetoothTrackpadTapToClickEnabled]
                ?: KeyboardPreferences.defaults().bluetoothTrackpadTapToClickEnabled,
            bluetoothTrackpadDedicatedButtonsEnabled = values[Keys.bluetoothTrackpadDedicatedButtonsEnabled]
                ?: KeyboardPreferences.defaults().bluetoothTrackpadDedicatedButtonsEnabled,
            bluetoothTrackpadKeepScreenOnMode = values[Keys.bluetoothTrackpadKeepScreenOnMode]
                ?.let(::bluetoothTrackpadKeepScreenOnModeFromName)
                ?: KeyboardPreferences.defaults().bluetoothTrackpadKeepScreenOnMode,
            bluetoothTrackpadDimWhenInactiveEnabled = values[Keys.bluetoothTrackpadDimWhenInactiveEnabled]
                ?: KeyboardPreferences.defaults().bluetoothTrackpadDimWhenInactiveEnabled,
            bluetoothTrackpadMacroPlacement = values[Keys.bluetoothTrackpadMacroPlacement]
                ?.let(::bluetoothTrackpadMacroPlacementFromName)
                ?: KeyboardPreferences.defaults().bluetoothTrackpadMacroPlacement,
            bluetoothTrackpadMacroStepDelayMs = values[Keys.bluetoothTrackpadMacroStepDelayMs]?.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
                MAX_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
            ) ?: KeyboardPreferences.defaults().bluetoothTrackpadMacroStepDelayMs,
            bluetoothTrackpadMacros = bluetoothTrackpadMacros(values),
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

    suspend fun setLayoutId(orientation: GeometryOrientation, layoutId: String) {
        dataStore.edit { values ->
            values[Keys.layoutIdKey(orientation)] = layoutId
        }
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

    suspend fun setKeyLongPressDelayMs(delayMs: Int) {
        dataStore.edit { values ->
            values[Keys.keyLongPressDelayMs] = delayMs.coerceIn(
                MIN_LONG_PRESS_DELAY_MS,
                MAX_LONG_PRESS_DELAY_MS,
            )
        }
    }

    suspend fun setSpecialLongPressDelayMs(delayMs: Int) {
        dataStore.edit { values ->
            values[Keys.specialLongPressDelayMs] = delayMs.coerceIn(
                MIN_LONG_PRESS_DELAY_MS,
                MAX_LONG_PRESS_DELAY_MS,
            )
        }
    }

    suspend fun setFnLongPressDelayMs(delayMs: Int) {
        setSpecialLongPressDelayMs(delayMs)
    }

    suspend fun setEdgeKeyWidthScale(value: Float) {
        dataStore.edit { values -> values[Keys.edgeKeyWidthScale] = value.coerceIn(MIN_EDGE_KEY_SCALE, MAX_EDGE_KEY_SCALE) }
    }

    suspend fun setCompactBottomControlsRightHandEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.compactBottomControlsRightHandEnabled] = enabled }
    }

    suspend fun setNumpadToggleEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.numpadToggleEnabled] = enabled }
    }

    suspend fun setKeyPreviewEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.keyPreviewEnabled] = enabled }
    }

    suspend fun setKeyHapticsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.keyHapticsEnabled] = enabled }
    }

    suspend fun setGestureTypingEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.gestureTypingEnabled] = enabled }
    }

    suspend fun setTypedSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.typedSuggestionsEnabled] = enabled }
    }

    suspend fun setTypedAutocorrectEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.typedAutocorrectEnabled] = enabled }
    }

    suspend fun setGlideCorrectionLearningEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.glideCorrectionLearningEnabled] = enabled }
    }

    suspend fun recordGlideCorrection(pathSignature: String, word: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        val normalizedWord = normalizeWord(word) ?: return
        val now = System.currentTimeMillis()
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            val nextEntry = corrections.remove(normalizedPathSignature)
                ?.copy(word = normalizedWord)
                ?.accepted(now)
                ?: GlideCorrectionEntry.fromWord(normalizedWord, now)
                ?: return@edit
            while (corrections.size >= MAX_GLIDE_CORRECTIONS) {
                corrections.remove(corrections.keys.first())
            }
            corrections[normalizedPathSignature] = nextEntry
            values[Keys.glideCorrections] = glideCorrectionsToPreferenceValue(corrections)
        }
    }

    suspend fun setGlideCorrection(pathSignature: String, word: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        val entry = GlideCorrectionEntry.fromWord(word, System.currentTimeMillis()) ?: return
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            corrections[normalizedPathSignature] = entry
            values[Keys.glideCorrections] = glideCorrectionsToPreferenceValue(corrections)
        }
    }

    suspend fun rejectGlideCorrection(pathSignature: String, word: String) {
        val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return
        val normalizedWord = normalizeWord(word) ?: return
        val now = System.currentTimeMillis()
        dataStore.edit { values ->
            val corrections = glideCorrectionsFromPreferenceValue(values[Keys.glideCorrections]).toMutableMap()
            val entry = corrections[normalizedPathSignature] ?: return@edit
            if (entry.word != normalizedWord) return@edit
            corrections[normalizedPathSignature] = entry.rejected(now)
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

    suspend fun resetGlideLearning() {
        GlideUserLanguageModel.storageFile(appContext.filesDir).delete()
        dataStore.edit { values ->
            values.remove(Keys.glideCorrections)
            values[Keys.glideLearningResetRevision] = (values[Keys.glideLearningResetRevision] ?: 0) + 1
        }
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

    suspend fun setGlideSpatialPrecision(precision: GlideSpatialPrecision) {
        dataStore.edit { values -> values[Keys.glideSpatialPrecision] = precision.name }
    }

    suspend fun setGlideDwellSensitivity(sensitivity: GlideDwellSensitivity) {
        dataStore.edit { values -> values[Keys.glideDwellSensitivity] = sensitivity.name }
    }

    suspend fun setGlideDwellActivationThreshold(threshold: Float) {
        dataStore.edit {
            it[Keys.glideDwellActivationThreshold] = threshold.coerceIn(
                MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD,
                MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD,
            )
        }
    }

    suspend fun setGlideImportedWordsPriority(priority: GlideImportedWordsPriority) {
        dataStore.edit { values -> values[Keys.glideImportedWordsPriority] = priority.name }
    }

    suspend fun setGlideRawFallbackMode(mode: GlideRawFallbackMode) {
        dataStore.edit { values -> values[Keys.glideRawFallbackMode] = mode.name }
    }

    suspend fun setGlidePredictiveRankingEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.glidePredictiveRankingEnabled] = enabled }
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

    suspend fun setSpeechPushToTalkEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechPushToTalkEnabled] = enabled }
    }

    suspend fun setSpeechSmartCleanupEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechSmartCleanupEnabled] = enabled }
    }

    suspend fun setSpeechAutoSpacingEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechAutoSpacingEnabled] = enabled }
    }

    suspend fun setSpeechAutoCapAfterPunctuationEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechAutoCapAfterPunctuationEnabled] = enabled }
    }

    suspend fun setSpeechAutoCapNamesEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechAutoCapNamesEnabled] = enabled }
    }

    suspend fun setSpeechSpokenPunctuationEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechSpokenPunctuationEnabled] = enabled }
    }

    suspend fun setSpeechCustomNamesFromText(text: String) {
        dataStore.edit { values ->
            val names = normalizeSpeechCustomNamesText(text)
            if (names.isEmpty()) {
                values.remove(Keys.speechCustomNames)
            } else {
                values[Keys.speechCustomNames] = names
            }
        }
    }

    suspend fun setSpeechCompleteSilenceMs(delayMs: Int) {
        dataStore.edit { values ->
            val next = delayMs.coerceIn(MIN_SPEECH_SILENCE_MS, MAX_SPEECH_SILENCE_MS)
            values[Keys.speechCompleteSilenceMs] = next
            values[Keys.speechPossibleSilenceMs]?.takeIf { it > next }?.let {
                values[Keys.speechPossibleSilenceMs] = next
            }
        }
    }

    suspend fun setSpeechPossibleSilenceMs(delayMs: Int) {
        dataStore.edit { values ->
            val completeSilenceMs = values[Keys.speechCompleteSilenceMs]?.coerceIn(
                MIN_SPEECH_SILENCE_MS,
                MAX_SPEECH_SILENCE_MS,
            ) ?: KeyboardPreferences.defaults().speechCompleteSilenceMs
            values[Keys.speechPossibleSilenceMs] = delayMs.coerceIn(
                MIN_SPEECH_SILENCE_MS,
                completeSilenceMs,
            )
        }
    }

    suspend fun setBluetoothRemoteEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothRemoteEnabled] = enabled }
    }

    suspend fun setBluetoothActiveDeviceAddress(address: String?) {
        dataStore.edit { values ->
            if (address.isNullOrBlank()) {
                values.remove(Keys.bluetoothActiveDeviceAddress)
            } else {
                values[Keys.bluetoothActiveDeviceAddress] = address
            }
        }
    }

    suspend fun setBluetoothDeviceSlotAddress(slotIndex: Int, address: String?) {
        val key = Keys.bluetoothDeviceSlotAddressKeys.getOrNull(slotIndex) ?: return
        dataStore.edit { values ->
            if (address.isNullOrBlank()) {
                values.remove(key)
            } else {
                Keys.bluetoothDeviceSlotAddressKeys
                    .filter { slotKey -> slotKey != key && values[slotKey] == address }
                    .forEach { slotKey -> values.remove(slotKey) }
                values[key] = address
            }
            val configuredAddresses = Keys.bluetoothDeviceSlotAddressKeys
                .mapNotNull { slotKey -> values[slotKey]?.takeIf { it.isNotBlank() } }
            val activeAddress = values[Keys.bluetoothActiveDeviceAddress]?.takeIf { it.isNotBlank() }
            if (activeAddress != null && activeAddress !in configuredAddresses) {
                values.remove(Keys.bluetoothActiveDeviceAddress)
                values[Keys.bluetoothRemoteEnabled] = false
            }
        }
    }

    suspend fun setBluetoothTrackpadEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadEnabled] = enabled }
    }

    suspend fun setBluetoothTrackpadPlacement(placement: BluetoothTrackpadPlacement) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadPlacement] = placement.name }
    }

    suspend fun setBluetoothTrackpadHeightPercent(value: Float) {
        dataStore.edit { values ->
            values[Keys.bluetoothTrackpadHeightPercent] = value.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
                MAX_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
            )
        }
    }

    suspend fun setBluetoothTrackpadSensitivity(value: Float) {
        dataStore.edit { values ->
            values[Keys.bluetoothTrackpadSensitivity] = value.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            )
        }
    }

    suspend fun setBluetoothTrackpadScrollSensitivity(value: Float) {
        dataStore.edit { values ->
            values[Keys.bluetoothTrackpadScrollSensitivity] = value.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            )
        }
    }

    suspend fun setBluetoothTrackpadInvertScrollEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadInvertScrollEnabled] = enabled }
    }

    suspend fun setBluetoothTrackpadTapToClickEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadTapToClickEnabled] = enabled }
    }

    suspend fun setBluetoothTrackpadDedicatedButtonsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadDedicatedButtonsEnabled] = enabled }
    }

    suspend fun setBluetoothTrackpadKeepScreenOnMode(mode: BluetoothTrackpadKeepScreenOnMode) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadKeepScreenOnMode] = mode.name }
    }

    suspend fun setBluetoothTrackpadDimWhenInactiveEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadDimWhenInactiveEnabled] = enabled }
    }

    suspend fun setBluetoothTrackpadMacroPlacement(placement: BluetoothTrackpadMacroPlacement) {
        dataStore.edit { values -> values[Keys.bluetoothTrackpadMacroPlacement] = placement.name }
    }

    suspend fun setBluetoothTrackpadMacroStepDelayMs(delayMs: Int) {
        dataStore.edit { values ->
            values[Keys.bluetoothTrackpadMacroStepDelayMs] = delayMs.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
                MAX_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
            )
        }
    }

    suspend fun setBluetoothTrackpadMacro(
        side: BluetoothTrackpadMacroSide,
        index: Int,
        label: String,
        actionText: String,
    ) {
        if (index !in 0 until MAX_BLUETOOTH_TRACKPAD_MACRO_KEYS_PER_SIDE) return
        dataStore.edit { values ->
            val next = bluetoothTrackpadMacros(values).toMutableList()
            next.removeAll { macro -> macro.side == side && macro.index == index }
            if (label.isNotBlank() || actionText.isNotBlank()) {
                next += BluetoothTrackpadMacroKey(
                    side = side,
                    index = index,
                    label = label.trim(),
                    actionText = actionText.trim(),
                )
            }
            values[Keys.bluetoothTrackpadMacros] = next
                .sortedWith(compareBy<BluetoothTrackpadMacroKey> { it.side.name }.thenBy { it.index })
                .map { macro -> macro.toPreferenceValue() }
                .toSet()
        }
    }

    suspend fun importGlideWords(reader: Reader): Int {
        val words = reader.useLines { lines ->
            lines
                .mapNotNull(::normalizeWord)
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

    suspend fun exportSettings(format: SettingsExportFormat): String {
        val current = preferences.first()
        val importedWords = importedGlideWordsFile()
            .takeIf { it.isFile }
            ?.readLines()
            .orEmpty()
        val userLanguageModel = GlideUserLanguageModel.storageFile(appContext.filesDir)
            .takeIf { it.isFile }
            ?.readText()
            .orEmpty()
        return SettingsExportSnapshot(
            preferences = current.toExportPreferenceMap(importedWords.size),
            importedGlideWords = importedWords,
            glideUserLanguageModel = userLanguageModel,
        ).encode(format)
    }

    suspend fun importSettings(reader: Reader): SettingsImportResult {
        val snapshot = decodeSettingsExportSnapshot(reader.readText())
        val importedWords = snapshot.importedGlideWords
            .mapNotNull(::normalizeWord)
            .distinct()
            .take(MAX_IMPORTED_GLIDE_WORDS)
        if (importedWords.isEmpty()) {
            importedGlideWordsFile().delete()
        } else {
            importedGlideWordsFile().writeText(importedWords.joinToString(separator = "\n"))
        }
        val languageModelFile = GlideUserLanguageModel.storageFile(appContext.filesDir)
        if (snapshot.glideUserLanguageModel.isBlank()) {
            languageModelFile.delete()
        } else {
            languageModelFile.parentFile?.mkdirs()
            languageModelFile.writeText(snapshot.glideUserLanguageModel)
        }
        dataStore.edit { values ->
            values.clear()
            values.writeExportedPreferences(snapshot.preferences, importedWords.size)
        }
        return SettingsImportResult(
            preferenceCount = snapshot.preferences.size,
            importedGlideWordCount = importedWords.size,
        )
    }

    suspend fun resetToDefaults() {
        importedGlideWordsFile().delete()
        GlideUserLanguageModel.storageFile(appContext.filesDir).delete()
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

    private fun glideSpatialPrecisionFromName(name: String): GlideSpatialPrecision? {
        return GlideSpatialPrecision.entries.firstOrNull { it.name == name }
    }

    private fun glideDwellSensitivityFromName(name: String): GlideDwellSensitivity? {
        return GlideDwellSensitivity.entries.firstOrNull { it.name == name }
    }

    private fun glideImportedWordsPriorityFromName(name: String): GlideImportedWordsPriority? {
        return GlideImportedWordsPriority.entries.firstOrNull { it.name == name }
    }

    private fun glideRawFallbackModeFromName(name: String): GlideRawFallbackMode? {
        return GlideRawFallbackMode.entries.firstOrNull { it.name == name }
    }

    private fun bluetoothTrackpadPlacementFromName(name: String): BluetoothTrackpadPlacement? {
        return BluetoothTrackpadPlacement.entries.firstOrNull { it.name == name }
    }

    private fun bluetoothTrackpadKeepScreenOnModeFromName(name: String): BluetoothTrackpadKeepScreenOnMode? {
        return BluetoothTrackpadKeepScreenOnMode.entries.firstOrNull { it.name == name }
    }

    private fun bluetoothTrackpadMacroPlacementFromName(name: String): BluetoothTrackpadMacroPlacement? {
        return BluetoothTrackpadMacroPlacement.entries.firstOrNull { it.name == name }
    }

    private fun bluetoothDeviceSlotAddresses(values: Preferences): List<String?> {
        val savedSlots = Keys.bluetoothDeviceSlotAddressKeys.map { key -> values[key]?.takeIf { it.isNotBlank() } }
        if (savedSlots.any { it != null }) return savedSlots
        val migratedActiveAddress = values[Keys.bluetoothActiveDeviceAddress]?.takeIf { it.isNotBlank() }
        return listOf(migratedActiveAddress, null, null)
    }

    private fun bluetoothTrackpadMacros(values: Preferences): List<BluetoothTrackpadMacroKey> {
        return values[Keys.bluetoothTrackpadMacros]
            .orEmpty()
            .mapNotNull(::bluetoothTrackpadMacroFromPreferenceValue)
            .distinctBy { macro -> macro.side to macro.index }
            .sortedWith(compareBy<BluetoothTrackpadMacroKey> { it.side.name }.thenBy { it.index })
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
        val portraitLayoutId = stringPreferencesKey("portrait_layout_id")
        val landscapeLayoutId = stringPreferencesKey("landscape_layout_id")
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
        val keyHapticsEnabled = booleanPreferencesKey("key_haptics_enabled")
        val stickyModifiersEnabled = booleanPreferencesKey("sticky_modifiers_enabled")
        val shiftCapsLockEnabled = booleanPreferencesKey("shift_caps_lock_enabled")
        val keyLongPressDelayMs = intPreferencesKey("key_long_press_delay_ms")
        val specialLongPressDelayMs = intPreferencesKey("special_long_press_delay_ms")
        val fnLongPressDelayMs = intPreferencesKey("fn_long_press_delay_ms")
        val edgeKeyWidthScale = floatPreferencesKey("edge_key_width_scale")
        val compactBottomControlsRightHandEnabled = booleanPreferencesKey("compact_bottom_controls_right_hand_enabled")
        val legacyFourRowRightHandControlsEnabled = booleanPreferencesKey("four_row_right_hand_controls_enabled")
        val gestureTypingEnabled = booleanPreferencesKey("gesture_typing_enabled")
        val typedSuggestionsEnabled = booleanPreferencesKey("typed_suggestions_enabled")
        val typedAutocorrectEnabled = booleanPreferencesKey("typed_autocorrect_enabled")
        val autoCapAfterPeriodEnabled = booleanPreferencesKey("auto_cap_after_period_enabled")
        val swipeUpActionsEnabled = booleanPreferencesKey("swipe_up_actions_enabled")
        val speechInputEnabled = booleanPreferencesKey("speech_input_enabled")
        val speechPushToTalkEnabled = booleanPreferencesKey("speech_push_to_talk_enabled")
        val speechSmartCleanupEnabled = booleanPreferencesKey("speech_smart_cleanup_enabled")
        val speechAutoSpacingEnabled = booleanPreferencesKey("speech_auto_spacing_enabled")
        val speechAutoCapAfterPunctuationEnabled = booleanPreferencesKey("speech_auto_cap_after_punctuation_enabled")
        val speechAutoCapNamesEnabled = booleanPreferencesKey("speech_auto_cap_names_enabled")
        val speechSpokenPunctuationEnabled = booleanPreferencesKey("speech_spoken_punctuation_enabled")
        val speechCustomNames = stringSetPreferencesKey("speech_custom_names")
        val speechCompleteSilenceMs = intPreferencesKey("speech_complete_silence_ms")
        val speechPossibleSilenceMs = intPreferencesKey("speech_possible_silence_ms")
        val glideImportedWordCount = intPreferencesKey("glide_imported_word_count")
        val glideCorrectionLearningEnabled = booleanPreferencesKey("glide_correction_learning_enabled")
        val glideCorrections = stringPreferencesKey("glide_corrections")
        val glidePreferShorterWords = booleanPreferencesKey("glide_prefer_shorter_words")
        val glideStrictFirstLastLetter = booleanPreferencesKey("glide_strict_first_last_letter")
        val glidePathTolerance = stringPreferencesKey("glide_path_tolerance")
        val glideSpatialPrecision = stringPreferencesKey("glide_spatial_precision")
        val glideDwellSensitivity = stringPreferencesKey("glide_dwell_sensitivity")
        val glideDwellActivationThreshold = floatPreferencesKey("glide_dwell_activation_threshold")
        val glideImportedWordsPriority = stringPreferencesKey("glide_imported_words_priority")
        val glideRawFallbackMode = stringPreferencesKey("glide_raw_fallback_mode")
        val glidePredictiveRankingEnabled = booleanPreferencesKey("glide_predictive_ranking_enabled")
        val glideLearningResetRevision = intPreferencesKey("glide_learning_reset_revision")
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
        val bluetoothRemoteEnabled = booleanPreferencesKey("bluetooth_remote_enabled")
        val bluetoothActiveDeviceAddress = stringPreferencesKey("bluetooth_active_device_address")
        val bluetoothDeviceSlot1Address = stringPreferencesKey("bluetooth_device_slot_1_address")
        val bluetoothDeviceSlot2Address = stringPreferencesKey("bluetooth_device_slot_2_address")
        val bluetoothDeviceSlot3Address = stringPreferencesKey("bluetooth_device_slot_3_address")
        val bluetoothTrackpadEnabled = booleanPreferencesKey("bluetooth_trackpad_enabled")
        val bluetoothTrackpadPlacement = stringPreferencesKey("bluetooth_trackpad_placement")
        val bluetoothTrackpadHeightPercent = floatPreferencesKey("bluetooth_trackpad_height_percent")
        val bluetoothTrackpadSensitivity = floatPreferencesKey("bluetooth_trackpad_sensitivity")
        val bluetoothTrackpadScrollSensitivity = floatPreferencesKey("bluetooth_trackpad_scroll_sensitivity")
        val bluetoothTrackpadInvertScrollEnabled = booleanPreferencesKey("bluetooth_trackpad_invert_scroll_enabled")
        val bluetoothTrackpadTapToClickEnabled = booleanPreferencesKey("bluetooth_trackpad_tap_to_click_enabled")
        val bluetoothTrackpadDedicatedButtonsEnabled = booleanPreferencesKey("bluetooth_trackpad_dedicated_buttons_enabled")
        val bluetoothTrackpadKeepScreenOnMode = stringPreferencesKey("bluetooth_trackpad_keep_screen_on_mode")
        val bluetoothTrackpadDimWhenInactiveEnabled = booleanPreferencesKey("bluetooth_trackpad_dim_when_inactive_enabled")
        val bluetoothTrackpadMacroPlacement = stringPreferencesKey("bluetooth_trackpad_macro_placement")
        val bluetoothTrackpadMacroStepDelayMs = intPreferencesKey("bluetooth_trackpad_macro_step_delay_ms")
        val bluetoothTrackpadMacros = stringSetPreferencesKey("bluetooth_trackpad_macros")

        val bluetoothDeviceSlotAddressKeys = listOf(
            bluetoothDeviceSlot1Address,
            bluetoothDeviceSlot2Address,
            bluetoothDeviceSlot3Address,
        )

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
            "bt_local" to stringPreferencesKey("slot_bt_local_action"),
            "bt_device_1" to stringPreferencesKey("slot_bt_device_1_action"),
            "bt_device_2" to stringPreferencesKey("slot_bt_device_2_action"),
            "bt_device_3" to stringPreferencesKey("slot_bt_device_3_action"),
            "bt_device_next" to stringPreferencesKey("slot_bt_device_next_action"),
            "bt_trackpad" to stringPreferencesKey("slot_bt_trackpad_action"),
        )

        fun geometryKey(prefix: String, field: GeometryField): Preferences.Key<Float> {
            return floatPreferencesKey("${prefix}_${field.name.lowercase()}")
        }

        fun layoutIdKey(orientation: GeometryOrientation): Preferences.Key<String> {
            return when (orientation) {
                GeometryOrientation.PORTRAIT -> portraitLayoutId
                GeometryOrientation.LANDSCAPE -> landscapeLayoutId
            }
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

    private fun KeyboardPreferences.toExportPreferenceMap(importedWordCount: Int): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            put(Keys.layoutId.name, layoutId)
            put(Keys.portraitLayoutId.name, portraitLayoutId)
            put(Keys.landscapeLayoutId.name, landscapeLayoutId)
            put(Keys.themePreset.name, themePreset.name)
            putGeometry("portrait", portraitGeometry)
            putGeometry("landscape", landscapeGeometry)
            put(Keys.hiddenOptionalKeys.name, hiddenOptionalKeyIds.sorted())
            put(
                Keys.keyDisplayOverrides.name,
                keyDisplayOverrides.toSortedMap().map { (keyId, override) ->
                    keyDisplayOverrideToPreferenceValue(keyId, override)
                },
            )
            put(Keys.escTouchMode.name, escTouchMode.name)
            put(Keys.primaryTextSizeSp.name, keyLabelStyle.primaryTextSizeSp)
            put(Keys.secondaryTextSizeSp.name, keyLabelStyle.secondaryTextSizeSp)
            put(Keys.fontWeight.name, keyLabelStyle.fontWeight)
            put(Keys.labelOpacity.name, keyLabelStyle.labelOpacity)
            put(Keys.uppercaseOnShift.name, keyLabelStyle.uppercaseOnShift)
            put(Keys.numpadToggleEnabled.name, numpadToggleEnabled)
            put(Keys.keyPreviewEnabled.name, keyPreviewEnabled)
            put(Keys.keyHapticsEnabled.name, keyHapticsEnabled)
            put(Keys.stickyModifiersEnabled.name, stickyModifiersEnabled)
            put(Keys.shiftCapsLockEnabled.name, shiftCapsLockEnabled)
            put(Keys.keyLongPressDelayMs.name, keyLongPressDelayMs)
            put(Keys.specialLongPressDelayMs.name, specialLongPressDelayMs)
            put(Keys.edgeKeyWidthScale.name, edgeKeyWidthScale)
            put(Keys.compactBottomControlsRightHandEnabled.name, compactBottomControlsRightHandEnabled)
            put(Keys.gestureTypingEnabled.name, gestureTypingEnabled)
            put(Keys.typedSuggestionsEnabled.name, typedSuggestionsEnabled)
            put(Keys.typedAutocorrectEnabled.name, typedAutocorrectEnabled)
            put(Keys.autoCapAfterPeriodEnabled.name, autoCapAfterPeriodEnabled)
            put(Keys.swipeUpActionsEnabled.name, swipeUpActionsEnabled)
            put(Keys.speechInputEnabled.name, speechInputEnabled)
            put(Keys.speechPushToTalkEnabled.name, speechPushToTalkEnabled)
            put(Keys.speechSmartCleanupEnabled.name, speechSmartCleanupEnabled)
            put(Keys.speechAutoSpacingEnabled.name, speechAutoSpacingEnabled)
            put(Keys.speechAutoCapAfterPunctuationEnabled.name, speechAutoCapAfterPunctuationEnabled)
            put(Keys.speechAutoCapNamesEnabled.name, speechAutoCapNamesEnabled)
            put(Keys.speechSpokenPunctuationEnabled.name, speechSpokenPunctuationEnabled)
            put(Keys.speechCustomNames.name, speechCustomNames.sorted())
            put(Keys.speechCompleteSilenceMs.name, speechCompleteSilenceMs)
            put(Keys.speechPossibleSilenceMs.name, speechPossibleSilenceMs)
            put(Keys.glideImportedWordCount.name, importedWordCount)
            put(Keys.glideCorrectionLearningEnabled.name, glideCorrectionLearningEnabled)
            put(Keys.glideCorrections.name, glideCorrectionsToPreferenceValue(glideCorrections))
            put(Keys.glidePreferShorterWords.name, glidePreferShorterWords)
            put(Keys.glideStrictFirstLastLetter.name, glideStrictFirstLastLetter)
            put(Keys.glidePathTolerance.name, glidePathTolerance.name)
            put(Keys.glideSpatialPrecision.name, glideSpatialPrecision.name)
            put(Keys.glideDwellSensitivity.name, glideDwellSensitivity.name)
            put(Keys.glideDwellActivationThreshold.name, glideDwellActivationThreshold)
            put(Keys.glideImportedWordsPriority.name, glideImportedWordsPriority.name)
            put(Keys.glideRawFallbackMode.name, glideRawFallbackMode.name)
            put(Keys.glidePredictiveRankingEnabled.name, glidePredictiveRankingEnabled)
            put(Keys.glideLearningResetRevision.name, glideLearningResetRevision)
            put(Keys.customBasePreset.name, customTheme.basePreset.name)
            put(Keys.customBackgroundColor.name, customTheme.backgroundColor.opaque())
            put(Keys.customKeyFillColor.name, customTheme.keyFillColor.opaque())
            put(Keys.customKeyStrokeColor.name, customTheme.keyStrokeColor.opaque())
            put(Keys.customKeyTextColor.name, customTheme.keyTextColor.opaque())
            put(Keys.customPressedFillColor.name, customTheme.pressedFillColor.opaque())
            put(Keys.customActiveModifierFillColor.name, customTheme.activeModifierFillColor.opaque())
            put(Keys.customBackgroundImageUri.name, customTheme.backgroundImageUri.orEmpty())
            put(Keys.customBackgroundImageOpacity.name, customTheme.backgroundImageOpacity)
            put(Keys.customKeyFillOpacity.name, customTheme.keyFillOpacity)
            put(Keys.customKeyStrokeOpacity.name, customTheme.keyStrokeOpacity)
            put(Keys.customKeyTextOpacity.name, customTheme.keyTextOpacity)
            put(Keys.bluetoothRemoteEnabled.name, bluetoothRemoteEnabled)
            put(Keys.bluetoothActiveDeviceAddress.name, bluetoothActiveDeviceAddress.orEmpty())
            Keys.bluetoothDeviceSlotAddressKeys.forEachIndexed { index, key ->
                put(key.name, bluetoothDeviceSlotAddresses.getOrNull(index).orEmpty())
            }
            put(Keys.bluetoothTrackpadEnabled.name, bluetoothTrackpadEnabled)
            put(Keys.bluetoothTrackpadPlacement.name, bluetoothTrackpadPlacement.name)
            put(Keys.bluetoothTrackpadHeightPercent.name, bluetoothTrackpadHeightPercent)
            put(Keys.bluetoothTrackpadSensitivity.name, bluetoothTrackpadSensitivity)
            put(Keys.bluetoothTrackpadScrollSensitivity.name, bluetoothTrackpadScrollSensitivity)
            put(Keys.bluetoothTrackpadInvertScrollEnabled.name, bluetoothTrackpadInvertScrollEnabled)
            put(Keys.bluetoothTrackpadTapToClickEnabled.name, bluetoothTrackpadTapToClickEnabled)
            put(Keys.bluetoothTrackpadDedicatedButtonsEnabled.name, bluetoothTrackpadDedicatedButtonsEnabled)
            put(Keys.bluetoothTrackpadKeepScreenOnMode.name, bluetoothTrackpadKeepScreenOnMode.name)
            put(Keys.bluetoothTrackpadDimWhenInactiveEnabled.name, bluetoothTrackpadDimWhenInactiveEnabled)
            put(Keys.bluetoothTrackpadMacroPlacement.name, bluetoothTrackpadMacroPlacement.name)
            put(Keys.bluetoothTrackpadMacroStepDelayMs.name, bluetoothTrackpadMacroStepDelayMs)
            put(
                Keys.bluetoothTrackpadMacros.name,
                bluetoothTrackpadMacros.map { macro -> macro.toPreferenceValue() },
            )
            Keys.actionSlotKeys.forEach { (slotId, key) ->
                slotActions[slotId]?.let { action -> put(key.name, action.toPreferenceValue()) }
            }
        }
    }

    private fun MutableMap<String, Any?>.putGeometry(prefix: String, geometry: KeyboardGeometry) {
        put(Keys.geometryKey(prefix, GeometryField.KEYBOARD_HEIGHT_PERCENT).name, geometry.keyboardHeightPercent)
        put(Keys.geometryKey(prefix, GeometryField.KEY_RADIUS).name, geometry.keyRadiusDp)
        put(Keys.geometryKey(prefix, GeometryField.BORDER_WIDTH).name, geometry.borderWidthDp)
        put(Keys.geometryKey(prefix, GeometryField.KEY_GAP).name, geometry.keyGapDp)
        put(Keys.geometryKey(prefix, GeometryField.HORIZONTAL_MARGIN).name, geometry.horizontalMarginDp)
        put(Keys.geometryKey(prefix, GeometryField.BOTTOM_MARGIN).name, geometry.bottomMarginDp)
        put(Keys.geometryKey(prefix, GeometryField.ROW_GAP).name, geometry.rowGapDp)
    }

    private fun MutablePreferences.writeExportedPreferences(settings: Map<String, Any?>, importedWordCount: Int) {
        settings.string(Keys.layoutId)?.let { this[Keys.layoutId] = it }
        settings.string(Keys.portraitLayoutId)?.let { this[Keys.portraitLayoutId] = it }
        settings.string(Keys.landscapeLayoutId)?.let { this[Keys.landscapeLayoutId] = it }
        settings.string(Keys.themePreset)?.let(::themePresetFromName)?.let { this[Keys.themePreset] = it.name }
        writeGeometry(settings, "portrait")
        writeGeometry(settings, "landscape")
        settings.stringSet(Keys.hiddenOptionalKeys)?.let { this[Keys.hiddenOptionalKeys] = it }
        settings.stringSet(Keys.keyDisplayOverrides)?.let { this[Keys.keyDisplayOverrides] = it }
        settings.string(Keys.escTouchMode)?.let(::escTouchModeFromName)?.let { this[Keys.escTouchMode] = it.name }
        settings.float(Keys.primaryTextSizeSp)?.let {
            this[Keys.primaryTextSizeSp] = it.coerceIn(
                KeyLabelStyleField.PRIMARY_TEXT_SIZE.minimum,
                KeyLabelStyleField.PRIMARY_TEXT_SIZE.maximum,
            )
        }
        settings.float(Keys.secondaryTextSizeSp)?.let {
            this[Keys.secondaryTextSizeSp] = it.coerceIn(
                KeyLabelStyleField.SECONDARY_TEXT_SIZE.minimum,
                KeyLabelStyleField.SECONDARY_TEXT_SIZE.maximum,
            )
        }
        settings.float(Keys.fontWeight)?.let {
            this[Keys.fontWeight] = it.coerceIn(
                KeyLabelStyleField.FONT_WEIGHT.minimum,
                KeyLabelStyleField.FONT_WEIGHT.maximum,
            )
        }
        settings.float(Keys.labelOpacity)?.let {
            this[Keys.labelOpacity] = it.coerceIn(
                KeyLabelStyleField.LABEL_OPACITY.minimum,
                KeyLabelStyleField.LABEL_OPACITY.maximum,
            )
        }
        settings.boolean(Keys.uppercaseOnShift)?.let { this[Keys.uppercaseOnShift] = it }
        settings.boolean(Keys.numpadToggleEnabled)?.let { this[Keys.numpadToggleEnabled] = it }
        settings.boolean(Keys.keyPreviewEnabled)?.let { this[Keys.keyPreviewEnabled] = it }
        settings.boolean(Keys.keyHapticsEnabled)?.let { this[Keys.keyHapticsEnabled] = it }
        settings.boolean(Keys.stickyModifiersEnabled)?.let { this[Keys.stickyModifiersEnabled] = it }
        settings.boolean(Keys.shiftCapsLockEnabled)?.let { this[Keys.shiftCapsLockEnabled] = it }
        settings.int(Keys.keyLongPressDelayMs)?.let {
            this[Keys.keyLongPressDelayMs] = it.coerceIn(MIN_LONG_PRESS_DELAY_MS, MAX_LONG_PRESS_DELAY_MS)
        }
        settings.int(Keys.specialLongPressDelayMs)?.let {
            this[Keys.specialLongPressDelayMs] = it.coerceIn(MIN_LONG_PRESS_DELAY_MS, MAX_LONG_PRESS_DELAY_MS)
        }
        settings.float(Keys.edgeKeyWidthScale)?.let {
            this[Keys.edgeKeyWidthScale] = it.coerceIn(MIN_EDGE_KEY_SCALE, MAX_EDGE_KEY_SCALE)
        }
        (
            settings.boolean(Keys.compactBottomControlsRightHandEnabled)
                ?: settings.boolean(Keys.legacyFourRowRightHandControlsEnabled)
        )?.let {
            this[Keys.compactBottomControlsRightHandEnabled] = it
        }
        settings.boolean(Keys.gestureTypingEnabled)?.let { this[Keys.gestureTypingEnabled] = it }
        settings.boolean(Keys.typedSuggestionsEnabled)?.let { this[Keys.typedSuggestionsEnabled] = it }
        settings.boolean(Keys.typedAutocorrectEnabled)?.let { this[Keys.typedAutocorrectEnabled] = it }
        settings.boolean(Keys.autoCapAfterPeriodEnabled)?.let { this[Keys.autoCapAfterPeriodEnabled] = it }
        settings.boolean(Keys.swipeUpActionsEnabled)?.let { this[Keys.swipeUpActionsEnabled] = it }
        settings.boolean(Keys.speechInputEnabled)?.let { this[Keys.speechInputEnabled] = it }
        settings.boolean(Keys.speechPushToTalkEnabled)?.let { this[Keys.speechPushToTalkEnabled] = it }
        settings.boolean(Keys.speechSmartCleanupEnabled)?.let { this[Keys.speechSmartCleanupEnabled] = it }
        settings.boolean(Keys.speechAutoSpacingEnabled)?.let { this[Keys.speechAutoSpacingEnabled] = it }
        settings.boolean(Keys.speechAutoCapAfterPunctuationEnabled)?.let {
            this[Keys.speechAutoCapAfterPunctuationEnabled] = it
        }
        settings.boolean(Keys.speechAutoCapNamesEnabled)?.let { this[Keys.speechAutoCapNamesEnabled] = it }
        settings.boolean(Keys.speechSpokenPunctuationEnabled)?.let { this[Keys.speechSpokenPunctuationEnabled] = it }
        settings.stringSet(Keys.speechCustomNames)?.let {
            val names = normalizeSpeechCustomNames(it)
            if (names.isNotEmpty()) this[Keys.speechCustomNames] = names
        }
        val speechCompleteSilenceMs = settings.int(Keys.speechCompleteSilenceMs)
            ?.coerceIn(MIN_SPEECH_SILENCE_MS, MAX_SPEECH_SILENCE_MS)
            ?: KeyboardPreferences.defaults().speechCompleteSilenceMs
        settings.int(Keys.speechCompleteSilenceMs)?.let {
            this[Keys.speechCompleteSilenceMs] = speechCompleteSilenceMs
        }
        settings.int(Keys.speechPossibleSilenceMs)?.let {
            this[Keys.speechPossibleSilenceMs] = it.coerceIn(MIN_SPEECH_SILENCE_MS, speechCompleteSilenceMs)
        }
        this[Keys.glideImportedWordCount] = importedWordCount
        settings.boolean(Keys.glideCorrectionLearningEnabled)?.let { this[Keys.glideCorrectionLearningEnabled] = it }
        settings.string(Keys.glideCorrections)?.let { corrections ->
            val normalized = glideCorrectionsToPreferenceValue(glideCorrectionsFromPreferenceValue(corrections))
            if (normalized.isNotBlank()) this[Keys.glideCorrections] = normalized
        }
        settings.boolean(Keys.glidePreferShorterWords)?.let { this[Keys.glidePreferShorterWords] = it }
        settings.boolean(Keys.glideStrictFirstLastLetter)?.let { this[Keys.glideStrictFirstLastLetter] = it }
        settings.string(Keys.glidePathTolerance)?.let(::glidePathToleranceFromName)?.let {
            this[Keys.glidePathTolerance] = it.name
        }
        settings.string(Keys.glideSpatialPrecision)?.let(::glideSpatialPrecisionFromName)?.let {
            this[Keys.glideSpatialPrecision] = it.name
        }
        settings.string(Keys.glideDwellSensitivity)?.let(::glideDwellSensitivityFromName)?.let {
            this[Keys.glideDwellSensitivity] = it.name
        }
        settings.float(Keys.glideDwellActivationThreshold)?.let {
            this[Keys.glideDwellActivationThreshold] = it.coerceIn(
                MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD,
                MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD,
            )
        }
        settings.string(Keys.glideImportedWordsPriority)?.let(::glideImportedWordsPriorityFromName)?.let {
            this[Keys.glideImportedWordsPriority] = it.name
        }
        settings.string(Keys.glideRawFallbackMode)?.let(::glideRawFallbackModeFromName)?.let {
            this[Keys.glideRawFallbackMode] = it.name
        }
        settings.boolean(Keys.glidePredictiveRankingEnabled)?.let { this[Keys.glidePredictiveRankingEnabled] = it }
        settings.int(Keys.glideLearningResetRevision)?.let {
            this[Keys.glideLearningResetRevision] = it.coerceAtLeast(0)
        }
        settings.string(Keys.customBasePreset)?.let(::themePresetFromName)?.takeUnless { it == ThemePreset.CUSTOM }?.let {
            this[Keys.customBasePreset] = it.name
        }
        settings.int(Keys.customBackgroundColor)?.let { this[Keys.customBackgroundColor] = it.opaque() }
        settings.int(Keys.customKeyFillColor)?.let { this[Keys.customKeyFillColor] = it.opaque() }
        settings.int(Keys.customKeyStrokeColor)?.let { this[Keys.customKeyStrokeColor] = it.opaque() }
        settings.int(Keys.customKeyTextColor)?.let { this[Keys.customKeyTextColor] = it.opaque() }
        settings.int(Keys.customPressedFillColor)?.let { this[Keys.customPressedFillColor] = it.opaque() }
        settings.int(Keys.customActiveModifierFillColor)?.let { this[Keys.customActiveModifierFillColor] = it.opaque() }
        settings.string(Keys.customBackgroundImageUri)?.takeIf { it.isNotBlank() }?.let {
            this[Keys.customBackgroundImageUri] = it
        }
        settings.float(Keys.customBackgroundImageOpacity)?.let {
            this[Keys.customBackgroundImageOpacity] = it.coerceIn(0f, 1f)
        }
        settings.float(Keys.customKeyFillOpacity)?.let { this[Keys.customKeyFillOpacity] = it.coerceIn(0f, 1f) }
        settings.float(Keys.customKeyStrokeOpacity)?.let { this[Keys.customKeyStrokeOpacity] = it.coerceIn(0f, 1f) }
        settings.float(Keys.customKeyTextOpacity)?.let { this[Keys.customKeyTextOpacity] = it.coerceIn(0f, 1f) }
        settings.boolean(Keys.bluetoothRemoteEnabled)?.let { this[Keys.bluetoothRemoteEnabled] = it }
        settings.string(Keys.bluetoothActiveDeviceAddress)?.takeIf { it.isNotBlank() }?.let {
            this[Keys.bluetoothActiveDeviceAddress] = it
        }
        Keys.bluetoothDeviceSlotAddressKeys.forEach { key ->
            settings.string(key)?.let { address ->
                if (address.isNotBlank()) this[key] = address
            }
        }
        settings.boolean(Keys.bluetoothTrackpadEnabled)?.let { this[Keys.bluetoothTrackpadEnabled] = it }
        settings.string(Keys.bluetoothTrackpadPlacement)?.let(::bluetoothTrackpadPlacementFromName)?.let {
            this[Keys.bluetoothTrackpadPlacement] = it.name
        }
        settings.float(Keys.bluetoothTrackpadHeightPercent)?.let {
            this[Keys.bluetoothTrackpadHeightPercent] = it.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
                MAX_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT,
            )
        }
        settings.float(Keys.bluetoothTrackpadSensitivity)?.let {
            this[Keys.bluetoothTrackpadSensitivity] = it.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            )
        }
        settings.float(Keys.bluetoothTrackpadScrollSensitivity)?.let {
            this[Keys.bluetoothTrackpadScrollSensitivity] = it.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_SENSITIVITY,
                MAX_BLUETOOTH_TRACKPAD_SENSITIVITY,
            )
        }
        settings.boolean(Keys.bluetoothTrackpadInvertScrollEnabled)?.let {
            this[Keys.bluetoothTrackpadInvertScrollEnabled] = it
        }
        settings.boolean(Keys.bluetoothTrackpadTapToClickEnabled)?.let {
            this[Keys.bluetoothTrackpadTapToClickEnabled] = it
        }
        settings.boolean(Keys.bluetoothTrackpadDedicatedButtonsEnabled)?.let {
            this[Keys.bluetoothTrackpadDedicatedButtonsEnabled] = it
        }
        settings.string(Keys.bluetoothTrackpadKeepScreenOnMode)?.let(::bluetoothTrackpadKeepScreenOnModeFromName)?.let {
            this[Keys.bluetoothTrackpadKeepScreenOnMode] = it.name
        }
        settings.boolean(Keys.bluetoothTrackpadDimWhenInactiveEnabled)?.let {
            this[Keys.bluetoothTrackpadDimWhenInactiveEnabled] = it
        }
        settings.string(Keys.bluetoothTrackpadMacroPlacement)?.let(::bluetoothTrackpadMacroPlacementFromName)?.let {
            this[Keys.bluetoothTrackpadMacroPlacement] = it.name
        }
        settings.int(Keys.bluetoothTrackpadMacroStepDelayMs)?.let {
            this[Keys.bluetoothTrackpadMacroStepDelayMs] = it.coerceIn(
                MIN_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
                MAX_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS,
            )
        }
        settings.stringSet(Keys.bluetoothTrackpadMacros)?.let { macros ->
            val normalized = macros.mapNotNull(::bluetoothTrackpadMacroFromPreferenceValue)
                .map { macro -> macro.toPreferenceValue() }
                .toSet()
            if (normalized.isNotEmpty()) this[Keys.bluetoothTrackpadMacros] = normalized
        }
        Keys.actionSlotKeys.forEach { (_, key) ->
            settings.string(key)?.let { encodedAction ->
                keyActionFromPreferenceValue(encodedAction)?.let { action -> this[key] = action.toPreferenceValue() }
            }
        }
    }

    private fun MutablePreferences.writeGeometry(settings: Map<String, Any?>, prefix: String) {
        GeometryField.entries.forEach { field ->
            val key = Keys.geometryKey(prefix, field)
            settings.float(key)?.let { this[key] = it.coerceIn(field.minimum, field.maximum) }
        }
    }

    private fun Map<String, Any?>.string(key: Preferences.Key<String>): String? = this[key.name] as? String

    private fun Map<String, Any?>.boolean(key: Preferences.Key<Boolean>): Boolean? = this[key.name] as? Boolean

    private fun Map<String, Any?>.int(key: Preferences.Key<Int>): Int? = when (val value = this[key.name]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun Map<String, Any?>.float(key: Preferences.Key<Float>): Float? = when (val value = this[key.name]) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }

    private fun Map<String, Any?>.stringSet(key: Preferences.Key<Set<String>>): Set<String>? {
        return when (val value = this[key.name]) {
            is Iterable<*> -> value.mapNotNull { it as? String }.toSet()
            is String -> setOf(value)
            else -> null
        }
    }

    private fun importedGlideWordsFile() = appContext.filesDir.resolve(IMPORTED_GLIDE_WORDS_FILE)
}

const val MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD = 0.2f
const val MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD = 0.85f
const val MIN_LONG_PRESS_DELAY_MS = 300
const val MAX_LONG_PRESS_DELAY_MS = 900
const val MIN_FN_LONG_PRESS_DELAY_MS = MIN_LONG_PRESS_DELAY_MS
const val MAX_FN_LONG_PRESS_DELAY_MS = MAX_LONG_PRESS_DELAY_MS
const val MIN_SPEECH_SILENCE_MS = 1000
const val MAX_SPEECH_SILENCE_MS = 8000
const val MIN_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT = 18f
const val MAX_BLUETOOTH_TRACKPAD_HEIGHT_PERCENT = 55f
const val MIN_BLUETOOTH_TRACKPAD_SENSITIVITY = 0.35f
const val MAX_BLUETOOTH_TRACKPAD_SENSITIVITY = 2.5f
const val MIN_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS = 40
const val MAX_BLUETOOTH_TRACKPAD_MACRO_STEP_DELAY_MS = 500

fun glideCorrectionsFromPreferenceValue(value: String?): Map<String, GlideCorrectionEntry> {
    if (value.isNullOrBlank()) return emptyMap()
    val corrections = linkedMapOf<String, GlideCorrectionEntry>()
    value.lineSequence().forEach { line ->
        val parts = line.split(GLIDE_CORRECTION_SEPARATOR)
        val pathSignature = parts.getOrNull(0)?.let(::normalizeGlidePathSignature) ?: return@forEach
        val word = parts.getOrNull(1)?.let(::normalizeWord) ?: return@forEach
        val acceptedCount = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rejectedCount = parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val lastUsedEpochMillis = parts.getOrNull(4)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        corrections[pathSignature] = GlideCorrectionEntry(
            word = word,
            acceptedCount = acceptedCount,
            rejectedCount = rejectedCount,
            lastUsedEpochMillis = lastUsedEpochMillis,
        )
    }
    return corrections
}

fun glideCorrectionsToPreferenceValue(corrections: Map<String, GlideCorrectionEntry>): String {
    return corrections.entries
        .mapNotNull { (pathSignature, entry) ->
            val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return@mapNotNull null
            val normalizedWord = normalizeWord(entry.word) ?: return@mapNotNull null
            listOf(
                normalizedPathSignature,
                normalizedWord,
                entry.acceptedCount.coerceAtLeast(1).toString(),
                entry.rejectedCount.coerceAtLeast(0).toString(),
                entry.lastUsedEpochMillis.coerceAtLeast(0L).toString(),
            ).joinToString(separator = GLIDE_CORRECTION_SEPARATOR)
        }
        .takeLast(MAX_GLIDE_CORRECTIONS)
        .joinToString(separator = "\n")
}

fun normalizeSpeechCustomNamesText(value: String): Set<String> {
    return normalizeSpeechCustomNames(value.split(',', '\n', '\r'))
}

fun normalizeSpeechCustomNames(values: Iterable<String>): Set<String> {
    val seen = mutableSetOf<String>()
    return values
        .asSequence()
        .mapNotNull(::normalizeSpeechCustomName)
        .filter { name -> seen.add(name.lowercase()) }
        .take(MAX_SPEECH_CUSTOM_NAMES)
        .toSet()
}

private fun normalizeSpeechCustomName(value: String): String? {
    val normalized = value.trim()
    return normalized.takeIf { name ->
        name.length in 2..32 &&
            name.any { char -> char.isLetter() } &&
            name.all { char -> char.isLetterOrDigit() || char == '\'' || char == '-' }
    }
}

private const val GLIDE_CORRECTION_SEPARATOR = "\t"
private const val MAX_GLIDE_CORRECTIONS = 500
private const val MAX_SPEECH_CUSTOM_NAMES = 200

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
        KeyLabelStyleField.PRIMARY_TEXT_SIZE -> 32f
        KeyLabelStyleField.SECONDARY_TEXT_SIZE -> 24f
        KeyLabelStyleField.FONT_WEIGHT -> 900f
        KeyLabelStyleField.LABEL_OPACITY -> 1f
    }
