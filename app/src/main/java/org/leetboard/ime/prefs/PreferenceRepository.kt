package org.leetboard.ime.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.ThemePreset
import org.leetboard.ime.model.keyActionFromPreferenceValue
import org.leetboard.ime.model.toPreferenceValue

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard_settings")

class PreferenceRepository(context: Context) {
    private val dataStore = context.applicationContext.keyboardDataStore

    val preferences: Flow<KeyboardPreferences> = dataStore.data.map { values ->
        KeyboardPreferences(
            layoutId = values[Keys.layoutId] ?: KeyboardPreferences.defaults().layoutId,
            themePreset = values[Keys.themePreset]?.let(::themePresetFromName) ?: ThemePreset.LEET_GREEN,
            portraitGeometry = geometry(values, "portrait", KeyboardGeometry()),
            landscapeGeometry = geometry(values, "landscape", KeyboardPreferences.defaults().landscapeGeometry),
            hiddenOptionalKeyIds = values[Keys.hiddenOptionalKeys].orEmpty(),
            slotActions = Keys.actionSlotKeys.mapNotNull { (slot, key) ->
                keyActionFromPreferenceValue(values[key])?.let { action -> slot to action }
            }.toMap(),
            numpadToggleEnabled = values[Keys.numpadToggleEnabled] ?: true,
            keyPreviewEnabled = values[Keys.keyPreviewEnabled] ?: true,
            gestureTypingEnabled = values[Keys.gestureTypingEnabled] ?: false,
            speechInputEnabled = values[Keys.speechInputEnabled] ?: false,
        )
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        dataStore.edit { values -> values[Keys.themePreset] = preset.name }
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

    suspend fun setNumpadToggleEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.numpadToggleEnabled] = enabled }
    }

    suspend fun setKeyPreviewEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.keyPreviewEnabled] = enabled }
    }

    suspend fun setGestureTypingEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.gestureTypingEnabled] = enabled }
    }

    suspend fun setSpeechInputEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.speechInputEnabled] = enabled }
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
        dataStore.edit { values -> values[key] = value.coerceIn(0f, 32f) }
    }

    private fun geometry(values: Preferences, prefix: String, defaults: KeyboardGeometry): KeyboardGeometry {
        return KeyboardGeometry(
            keyRadiusPx = values[Keys.geometryKey(prefix, GeometryField.KEY_RADIUS)] ?: defaults.keyRadiusPx,
            borderWidthPx = values[Keys.geometryKey(prefix, GeometryField.BORDER_WIDTH)] ?: defaults.borderWidthPx,
            keyGapPx = values[Keys.geometryKey(prefix, GeometryField.KEY_GAP)] ?: defaults.keyGapPx,
            outerMarginPx = values[Keys.geometryKey(prefix, GeometryField.OUTER_MARGIN)] ?: defaults.outerMarginPx,
            rowGapPx = values[Keys.geometryKey(prefix, GeometryField.ROW_GAP)] ?: defaults.rowGapPx,
        )
    }

    private fun themePresetFromName(name: String): ThemePreset? {
        return ThemePreset.entries.firstOrNull { it.name == name }
    }

    private object Keys {
        val layoutId = stringPreferencesKey("layout_id")
        val themePreset = stringPreferencesKey("theme_preset")
        val hiddenOptionalKeys = stringSetPreferencesKey("hidden_optional_keys")
        val numpadToggleEnabled = booleanPreferencesKey("numpad_toggle_enabled")
        val keyPreviewEnabled = booleanPreferencesKey("key_preview_enabled")
        val gestureTypingEnabled = booleanPreferencesKey("gesture_typing_enabled")
        val speechInputEnabled = booleanPreferencesKey("speech_input_enabled")

        val actionSlotKeys = mapOf(
            "esc" to stringPreferencesKey("slot_esc_action"),
            "ctrl" to stringPreferencesKey("slot_ctrl_action"),
            "alt" to stringPreferencesKey("slot_alt_action"),
            "tab" to stringPreferencesKey("slot_tab_action"),
            "left" to stringPreferencesKey("slot_left_action"),
            "right" to stringPreferencesKey("slot_right_action"),
            "num_toggle" to stringPreferencesKey("slot_num_toggle_action"),
        )

        fun geometryKey(prefix: String, field: GeometryField): Preferences.Key<Float> {
            return floatPreferencesKey("${prefix}_${field.name.lowercase()}")
        }
    }
}
