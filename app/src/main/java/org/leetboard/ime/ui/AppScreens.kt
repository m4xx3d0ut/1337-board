package org.leetboard.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.ThemePreset
import org.leetboard.ime.model.displayLabel
import org.leetboard.ime.prefs.GeometryField
import org.leetboard.ime.prefs.GeometryOrientation
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.PreferenceRepository

@Composable
fun InfoScreen(
    title: String,
    subtitle: String,
    primaryAction: String,
    secondaryAction: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    LeetBoardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPrimaryAction) {
                        Text(primaryAction)
                    }
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryAction)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    repository: PreferenceRepository,
    onOpenDiagnostics: () -> Unit,
    onClose: () -> Unit,
) {
    val preferences by repository.preferences.collectAsState(initial = KeyboardPreferences.defaults())
    val scope = rememberCoroutineScope()

    LeetBoardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("1337 Board Settings", style = MaterialTheme.typography.headlineMedium)
                SettingsSection(
                    title = "Theme",
                    body = "Current preset: ${preferences.themePreset.label}",
                )
                Button(
                    onClick = {
                        scope.launch {
                            repository.setThemePreset(preferences.themePreset.next())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Next theme")
                }

                SettingsSection(title = "Optional Keys", body = "Show or hide terminal/navigation keys.")
                optionalKeyControls.forEach { key ->
                    SettingSwitch(
                        label = key.label,
                        checked = key.id !in preferences.hiddenOptionalKeyIds,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setOptionalKeyHidden(key.id, hidden = !checked) }
                        },
                    )
                }
                SettingSwitch(
                    label = "Numpad toggle",
                    checked = preferences.numpadToggleEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setNumpadToggleEnabled(checked) }
                    },
                )

                SettingsSection(title = "Action Slots", body = "Cycle safe preset actions for configurable keys.")
                actionSlots.forEach { slot ->
                    val action = preferences.slotActions[slot.id] ?: slot.defaultAction
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.setSlotAction(slot.id, nextAction(action))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${slot.label}: ${action.displayLabel()}")
                    }
                }

                SettingsSection(
                    title = "Portrait Geometry",
                    body = "Adjust key shape and spacing for portrait.",
                )
                GeometryControls(
                    orientation = GeometryOrientation.PORTRAIT,
                    geometry = preferences.portraitGeometry,
                    onChange = { field, value ->
                        scope.launch { repository.setGeometryValue(GeometryOrientation.PORTRAIT, field, value) }
                    },
                )
                SettingsSection(
                    title = "Landscape Geometry",
                    body = "Adjust key shape and spacing for landscape and numpad modes.",
                )
                GeometryControls(
                    orientation = GeometryOrientation.LANDSCAPE,
                    geometry = preferences.landscapeGeometry,
                    onChange = { field, value ->
                        scope.launch { repository.setGeometryValue(GeometryOrientation.LANDSCAPE, field, value) }
                    },
                )

                SettingsSection(title = "Privacy-Gated Features", body = "Speech and swipe remain local-only feature flags.")
                SettingSwitch(
                    label = "Swipe typing prototype",
                    checked = preferences.gestureTypingEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setGestureTypingEnabled(checked) }
                    },
                )
                SettingSwitch(
                    label = "Speech input placeholder",
                    checked = preferences.speechInputEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setSpeechInputEnabled(checked) }
                    },
                )

                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Text("Open diagnostics")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GeometryControls(
    orientation: GeometryOrientation,
    geometry: KeyboardGeometry,
    onChange: (GeometryField, Float) -> Unit,
) {
    GeometrySlider("Radius", geometry.keyRadiusPx, orientation, GeometryField.KEY_RADIUS, onChange)
    GeometrySlider("Border", geometry.borderWidthPx, orientation, GeometryField.BORDER_WIDTH, onChange)
    GeometrySlider("Key gap", geometry.keyGapPx, orientation, GeometryField.KEY_GAP, onChange)
    GeometrySlider("Margin", geometry.outerMarginPx, orientation, GeometryField.OUTER_MARGIN, onChange)
    GeometrySlider("Row gap", geometry.rowGapPx, orientation, GeometryField.ROW_GAP, onChange)
}

@Composable
private fun GeometrySlider(
    label: String,
    value: Float,
    orientation: GeometryOrientation,
    field: GeometryField,
    onChange: (GeometryField, Float) -> Unit,
) {
    val max = if (field == GeometryField.BORDER_WIDTH) 8f else 24f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${orientation.name.lowercase().replaceFirstChar { it.uppercase() }} $label: ${value.toInt()}px")
        Slider(
            value = value.coerceIn(0f, max),
            onValueChange = { onChange(field, it) },
            valueRange = 0f..max,
        )
    }
}

private data class OptionalKeyControl(
    val id: String,
    val label: String,
)

private data class ActionSlotControl(
    val id: String,
    val label: String,
    val defaultAction: KeyAction,
)

private val optionalKeyControls = listOf(
    OptionalKeyControl("esc", "Esc"),
    OptionalKeyControl("ctrl", "Ctrl"),
    OptionalKeyControl("alt", "Alt"),
    OptionalKeyControl("tab", "Tab"),
    OptionalKeyControl("left", "Left arrow"),
    OptionalKeyControl("right", "Right arrow"),
    OptionalKeyControl("up", "Up arrow"),
    OptionalKeyControl("down", "Down arrow"),
)

private val actionSlots = listOf(
    ActionSlotControl("esc", "Esc slot", KeyAction(KeyActionType.ESCAPE)),
    ActionSlotControl("ctrl", "Ctrl slot", KeyAction(KeyActionType.CTRL)),
    ActionSlotControl("alt", "Alt slot", KeyAction(KeyActionType.ALT)),
    ActionSlotControl("tab", "Tab slot", KeyAction(KeyActionType.TAB)),
    ActionSlotControl("left", "Left slot", KeyAction(KeyActionType.ARROW_LEFT)),
    ActionSlotControl("right", "Right slot", KeyAction(KeyActionType.ARROW_RIGHT)),
    ActionSlotControl("num_toggle", "Numpad slot", KeyAction(KeyActionType.NUMPAD_TOGGLE)),
)

private val actionCycle = listOf(
    KeyAction(KeyActionType.ESCAPE),
    KeyAction(KeyActionType.TAB),
    KeyAction(KeyActionType.CTRL),
    KeyAction(KeyActionType.ALT),
    KeyAction(KeyActionType.ARROW_LEFT),
    KeyAction(KeyActionType.ARROW_RIGHT),
    KeyAction(KeyActionType.NUMPAD_TOGGLE),
    KeyAction(KeyActionType.SWITCH_SYMBOLS),
    KeyAction(KeyActionType.SETTINGS),
    KeyAction.text("|"),
    KeyAction.text("~"),
)

private fun nextAction(current: KeyAction): KeyAction {
    val index = actionCycle.indexOfFirst { it.type == current.type && it.text == current.text }
    return actionCycle[(index + 1).floorMod(actionCycle.size)]
}

private fun Int.floorMod(modulus: Int): Int {
    return ((this % modulus) + modulus) % modulus
}

private fun ThemePreset.next(): ThemePreset {
    val presets = ThemePreset.entries
    return presets[(ordinal + 1) % presets.size]
}

private val ThemePreset.label: String
    get() = name.lowercase().split("_").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
