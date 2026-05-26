package org.leetboard.ime.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Paint
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.leetboard.ime.engine.CustomizationEngine
import org.leetboard.ime.engine.GlideCorrectionEntry
import org.leetboard.ime.engine.GlideDwellSensitivity
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.GlideSpatialPrecision
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.model.CustomThemeConfig
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyDisplayOverride
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.RowAlignment
import org.leetboard.ime.model.ThemePreset
import org.leetboard.ime.model.displayLabel
import org.leetboard.ime.model.fallbackLabel
import org.leetboard.ime.model.opaque
import org.leetboard.ime.model.resolvedDisplay
import org.leetboard.ime.prefs.CustomThemeColorField
import org.leetboard.ime.prefs.CustomThemeOpacityField
import org.leetboard.ime.prefs.GeometryField
import org.leetboard.ime.prefs.GeometryOrientation
import org.leetboard.ime.prefs.KeyLabelStyleField
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD
import org.leetboard.ime.prefs.MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.prefs.defaultLayoutOptions

@Composable
fun InfoScreen(
    title: String,
    subtitle: String,
    primaryAction: String,
    secondaryAction: String,
    tertiaryAction: String? = null,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onTertiaryAction: (() -> Unit)? = null,
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                        Text(primaryAction)
                    }
                    OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
                        Text(secondaryAction)
                    }
                    if (tertiaryAction != null && onTertiaryAction != null) {
                        OutlinedButton(onClick = onTertiaryAction, modifier = Modifier.fillMaxWidth()) {
                            Text(tertiaryAction)
                        }
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
    var showResetConfirmation by remember { mutableStateOf(false) }

    LeetBoardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 840.dp
                val pageModifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isTablet) 32.dp else 18.dp)

                if (isTablet) {
                    Row(
                        modifier = pageModifier,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SettingsHeader()
                            LayoutThemeSection(preferences, repository)
                            CustomThemeSection(preferences, repository)
                            OptionalKeysSection(preferences, repository)
                            InteractionSection(preferences, repository)
                            FeatureSection(preferences, repository)
                            SettingsActions(onOpenDiagnostics, onClose) {
                                showResetConfirmation = true
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            KeymapPreviewSection(preferences)
                            ActionSlotsSection(preferences, repository)
                            KeyDisplaySection(preferences, repository)
                            LabelStyleSection(preferences, repository)
                            GeometrySection(preferences, repository)
                        }
                    }
                } else {
                    Column(
                        modifier = pageModifier,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SettingsHeader()
                        KeymapPreviewSection(preferences)
                        LayoutThemeSection(preferences, repository)
                        CustomThemeSection(preferences, repository)
                        GeometrySection(preferences, repository)
                        InteractionSection(preferences, repository)
                        LabelStyleSection(preferences, repository)
                        OptionalKeysSection(preferences, repository)
                        ActionSlotsSection(preferences, repository)
                        KeyDisplaySection(preferences, repository)
                        FeatureSection(preferences, repository)
                        SettingsActions(onOpenDiagnostics, onClose) {
                            showResetConfirmation = true
                        }
                    }
                }
            }
            if (showResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text("Reset defaults") },
                    text = { Text("Reset layout, theme, geometry, optional keys, action slots, and feature flags.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetConfirmation = false
                                scope.launch { repository.resetToDefaults() }
                            },
                        ) {
                            Text("Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmation = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("1337 Board Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Layout, theme, keymap, and privacy controls.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LayoutThemeSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Layout and Theme",
        body = "Choose the base layout and color preset.",
    ) {
        defaultLayoutOptions.forEach { option ->
            SelectButton(
                label = option.label,
                selected = preferences.layoutId == option.id,
                onClick = {
                    scope.launch { repository.setLayoutId(option.id) }
                },
            )
        }
        ThemePreset.entries.forEach { preset ->
            SelectButton(
                label = preset.label,
                selected = preferences.themePreset == preset,
                onClick = {
                    scope.launch { repository.setThemePreset(preset) }
                },
            )
        }
    }
}

@Composable
private fun CustomThemeSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeEngine = remember { ThemeEngine() }
    val currentTheme = themeEngine.resolve(
        preset = preferences.themePreset,
        portrait = preferences.portraitGeometry,
        landscape = preferences.landscapeGeometry,
        customTheme = preferences.customTheme,
    )
    val cloneBase = if (preferences.themePreset == ThemePreset.CUSTOM) {
        preferences.customTheme.basePreset
    } else {
        preferences.themePreset
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Some providers do not offer persistable grants; keep the URI as a best-effort preview source.
            }
            scope.launch { repository.setCustomThemeBackgroundImage(uri.toString()) }
        }
    }

    SettingsGroup(
        title = "Custom Theme",
        body = "Clone a preset, then tune colors, transparency, and an optional local background image.",
    ) {
        Button(
            onClick = {
                scope.launch {
                    repository.setCustomTheme(CustomThemeConfig.fromTheme(cloneBase, currentTheme))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clone ${cloneBase.label} into custom")
        }
        CustomThemeColorField.entries.forEach { field ->
            HexColorControl(
                label = field.label,
                color = preferences.customTheme.colorFor(field),
                onApply = { color ->
                    scope.launch { repository.setCustomThemeColor(field, color) }
                },
            )
        }
        CustomThemeOpacityField.entries.forEach { field ->
            OpacitySlider(
                label = field.label,
                value = preferences.customTheme.opacityFor(field),
                onChange = { value ->
                    scope.launch { repository.setCustomThemeOpacity(field, value) }
                },
            )
        }
        OutlinedButton(
            onClick = { imageLauncher.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (preferences.customTheme.backgroundImageUri == null) "Pick background image" else "Replace background image")
        }
        if (preferences.customTheme.backgroundImageUri != null) {
            OutlinedButton(
                onClick = { scope.launch { repository.setCustomThemeBackgroundImage(null) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove background image")
            }
        }
        OutlinedButton(
            onClick = { scope.launch { repository.resetCustomTheme() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset custom theme")
        }
    }
}

@Composable
private fun KeymapPreviewSection(preferences: KeyboardPreferences) {
    SettingsGroup(
        title = "Keymap Preview",
        body = "Live preview of the current layout, hidden keys, and reassigned action slots.",
    ) {
        KeymapPreview(
            title = "Portrait",
            preferences = preferences,
            orientation = Configuration.ORIENTATION_PORTRAIT,
            geometry = preferences.portraitGeometry,
        )
        KeymapPreview(
            title = "Landscape",
            preferences = preferences,
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            geometry = preferences.landscapeGeometry,
        )
    }
}

@Composable
private fun GeometrySection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Keyboard Size and Spacing",
        body = "Tune height, left/right margin, key shape, and gaps separately for portrait and landscape.",
    ) {
        EdgeKeyWidthSlider(preferences.edgeKeyWidthScale) { value ->
            scope.launch { repository.setEdgeKeyWidthScale(value) }
        }
        GeometryControls(
            orientation = GeometryOrientation.PORTRAIT,
            geometry = preferences.portraitGeometry,
            onChange = { field, value ->
                scope.launch { repository.setGeometryValue(GeometryOrientation.PORTRAIT, field, value) }
            },
        )
        GeometryControls(
            orientation = GeometryOrientation.LANDSCAPE,
            geometry = preferences.landscapeGeometry,
            onChange = { field, value ->
                scope.launch { repository.setGeometryValue(GeometryOrientation.LANDSCAPE, field, value) }
            },
        )
    }
}

@Composable
private fun OptionalKeysSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Optional Keys",
        body = "Show or hide terminal, navigation, speech, settings, and numpad controls.",
    ) {
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
        SettingSwitch(
            label = "Key preview",
            checked = preferences.keyPreviewEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setKeyPreviewEnabled(checked) }
            },
        )
    }
}

@Composable
private fun InteractionSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Touch Behavior",
        body = "Tune Esc handling and modifier stickiness for terminal-style shortcuts.",
    ) {
        EscTouchMode.entries.forEach { mode ->
            SelectButton(
                label = when (mode) {
                    EscTouchMode.REGULAR -> "Esc tap: regular"
                    EscTouchMode.LONG_TAP -> "Esc tap: long tap only"
                },
                selected = preferences.escTouchMode == mode,
                onClick = { scope.launch { repository.setEscTouchMode(mode) } },
            )
        }
        SettingSwitch(
            label = "Sticky Ctrl, Alt, and Shift",
            checked = preferences.stickyModifiersEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setStickyModifiersEnabled(checked) }
            },
        )
        SettingSwitch(
            label = "Shift caps lock cycle",
            checked = preferences.shiftCapsLockEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setShiftCapsLockEnabled(checked) }
            },
        )
    }
}

@Composable
private fun ActionSlotsSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Action Slots",
        body = "Cycle safe preset actions for configurable non-alphanumeric keys.",
    ) {
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
    }
}

@Composable
private fun KeyDisplaySection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Key Labels and Icons",
        body = "Customize primary text or icons for special keys.",
    ) {
        keyDisplayControls.forEach { control ->
            val override = preferences.keyDisplayOverrides[control.id]
            val label = override?.label.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { nextLabel ->
                        scope.launch {
                            repository.setKeyDisplayOverride(
                                control.id,
                                KeyDisplayOverride(
                                    label = nextLabel.takeIf { it.isNotBlank() },
                                    icon = override?.icon,
                                ),
                            )
                        }
                    },
                    label = { Text("${control.label} label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextIcon = control.nextIcon(override?.icon)
                            scope.launch {
                                repository.setKeyDisplayOverride(
                                    control.id,
                                    KeyDisplayOverride(
                                        label = override?.label,
                                        icon = nextIcon,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Icon: ${override?.icon?.fallbackLabel() ?: "default"}")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch { repository.setKeyDisplayOverride(control.id, null) }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelStyleSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(
        title = "Key Text Style",
        body = "Adjust label size, secondary legends, weight, opacity, and caps display.",
    ) {
        KeyLabelStyleSlider(
            "Primary size",
            preferences.keyLabelStyle.primaryTextSizeSp,
            KeyLabelStyleField.PRIMARY_TEXT_SIZE,
            onChange = { field, value -> scope.launch { repository.setKeyLabelStyleValue(field, value) } },
        )
        KeyLabelStyleSlider(
            "Shift symbol size",
            preferences.keyLabelStyle.secondaryTextSizeSp,
            KeyLabelStyleField.SECONDARY_TEXT_SIZE,
            onChange = { field, value -> scope.launch { repository.setKeyLabelStyleValue(field, value) } },
        )
        KeyLabelStyleSlider(
            "Font weight",
            preferences.keyLabelStyle.fontWeight,
            KeyLabelStyleField.FONT_WEIGHT,
            onChange = { field, value -> scope.launch { repository.setKeyLabelStyleValue(field, value) } },
        )
        KeyLabelStyleSlider(
            "Opacity",
            preferences.keyLabelStyle.labelOpacity,
            KeyLabelStyleField.LABEL_OPACITY,
            onChange = { field, value -> scope.launch { repository.setKeyLabelStyleValue(field, value) } },
        )
        SettingSwitch(
            label = "Uppercase labels on Shift/Caps",
            checked = preferences.keyLabelStyle.uppercaseOnShift,
            onCheckedChange = { checked ->
                scope.launch { repository.setUppercaseOnShift(checked) }
            },
        )
    }
}

@Composable
private fun FeatureSection(
    preferences: KeyboardPreferences,
    repository: PreferenceRepository,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showGlideCorrections by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch { repository.setSpeechInputEnabled(granted) }
    }
    val wordImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        repository.importGlideWords(InputStreamReader(stream))
                    }
                }
            }
        }
    }
    SettingsGroup(
        title = "Feature Opt-In",
        body = "Glide typing and system speech input are off on fresh installs. Enable only the input features you want.",
    ) {
        SettingSwitch(
            label = "Enable glide typing",
            checked = preferences.gestureTypingEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setGestureTypingEnabled(checked) }
            },
        )
        SettingSwitch(
            label = "Enable system mic input",
            checked = preferences.speechInputEnabled,
            onCheckedChange = { checked ->
                if (!checked) {
                    scope.launch { repository.setSpeechInputEnabled(false) }
                } else if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    scope.launch { repository.setSpeechInputEnabled(true) }
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
    SettingsGroup(
        title = "Glide Dictionary",
        body = "Bundled English words stay local. Import a plain text wordlist only when you want different vocabulary.",
    ) {
        OutlinedButton(
            onClick = { scope.launch { repository.clearImportedGlideWords() } },
            enabled = preferences.glideImportedWordCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use bundled English dictionary")
        }
        OutlinedButton(
            onClick = { wordImportLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import glide wordlist (${preferences.glideImportedWordCount})")
        }
        if (preferences.glideImportedWordCount > 0) {
            OutlinedButton(
                onClick = { scope.launch { repository.clearImportedGlideWords() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear imported wordlist")
            }
        }
    }
    SettingsGroup(
        title = "Glide Behavior",
        body = "Glide decoding is local. Correction learning is optional and stored only on this device.",
    ) {
        SettingSwitch(
            label = "Learn from glide corrections",
            checked = preferences.glideCorrectionLearningEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setGlideCorrectionLearningEnabled(checked) }
            },
        )
        if (preferences.glideCorrections.isNotEmpty()) {
            OutlinedButton(
                onClick = { showGlideCorrections = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review glide corrections (${preferences.glideCorrections.size})")
            }
            OutlinedButton(
                onClick = { scope.launch { repository.clearGlideCorrections() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear glide corrections (${preferences.glideCorrections.size})")
            }
        }
        OutlinedButton(
            onClick = { scope.launch { repository.resetGlideLearning() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset glide learning")
        }
        SettingSwitch(
            label = "Strict first/last glide letters",
            checked = preferences.glideStrictFirstLastLetter,
            onCheckedChange = { checked ->
                scope.launch { repository.setGlideStrictFirstLastLetter(checked) }
            },
        )
        SettingSwitch(
            label = "Predictive glide ranking",
            checked = preferences.glidePredictiveRankingEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setGlidePredictiveRankingEnabled(checked) }
            },
        )
        SettingSwitch(
            label = "Prefer shorter glide words",
            checked = preferences.glidePreferShorterWords,
            onCheckedChange = { checked ->
                scope.launch { repository.setGlidePreferShorterWords(checked) }
            },
        )
        Text("Path tolerance", style = MaterialTheme.typography.labelLarge)
        GlidePathTolerance.entries.forEach { tolerance ->
            SelectButton(
                label = tolerance.label,
                selected = preferences.glidePathTolerance == tolerance,
                onClick = { scope.launch { repository.setGlidePathTolerance(tolerance) } },
            )
        }
        Text("Spatial precision", style = MaterialTheme.typography.labelLarge)
        GlideSpatialPrecision.entries.forEach { precision ->
            SelectButton(
                label = precision.label,
                selected = preferences.glideSpatialPrecision == precision,
                onClick = { scope.launch { repository.setGlideSpatialPrecision(precision) } },
            )
        }
        Text("Dwell sensitivity", style = MaterialTheme.typography.labelLarge)
        GlideDwellSensitivity.entries.forEach { sensitivity ->
            SelectButton(
                label = sensitivity.label,
                selected = preferences.glideDwellSensitivity == sensitivity,
                onClick = { scope.launch { repository.setGlideDwellSensitivity(sensitivity) } },
            )
        }
        GlideDwellThresholdSlider(preferences.glideDwellActivationThreshold) { value ->
            scope.launch { repository.setGlideDwellActivationThreshold(value) }
        }
        Text("Imported word priority", style = MaterialTheme.typography.labelLarge)
        GlideImportedWordsPriority.entries.forEach { priority ->
            SelectButton(
                label = priority.label,
                selected = preferences.glideImportedWordsPriority == priority,
                onClick = { scope.launch { repository.setGlideImportedWordsPriority(priority) } },
            )
        }
        Text("Raw path fallback", style = MaterialTheme.typography.labelLarge)
        GlideRawFallbackMode.entries.forEach { mode ->
            SelectButton(
                label = mode.label,
                selected = preferences.glideRawFallbackMode == mode,
                onClick = { scope.launch { repository.setGlideRawFallbackMode(mode) } },
            )
        }
        SettingSwitch(
            label = "Auto-cap after period",
            checked = preferences.autoCapAfterPeriodEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setAutoCapAfterPeriodEnabled(checked) }
            },
        )
        SettingSwitch(
            label = "Per-key swipe-up actions",
            checked = preferences.swipeUpActionsEnabled,
            onCheckedChange = { checked ->
                scope.launch { repository.setSwipeUpActionsEnabled(checked) }
            },
        )
    }
    if (showGlideCorrections) {
        GlideCorrectionsDialog(
            corrections = preferences.glideCorrections,
            repository = repository,
            onDismiss = { showGlideCorrections = false },
        )
    }
}

@Composable
private fun SettingsActions(
    onOpenDiagnostics: () -> Unit,
    onClose: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    SettingsGroup(title = "Maintenance", body = "Diagnostics and reset controls.") {
        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Open diagnostics")
        }
        OutlinedButton(onClick = onResetDefaults, modifier = Modifier.fillMaxWidth()) {
            Text("Reset defaults")
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun GlideCorrectionsDialog(
    corrections: Map<String, GlideCorrectionEntry>,
    repository: PreferenceRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sortedCorrections = corrections.toSortedMap()
    var editedCorrections by remember(corrections) {
        mutableStateOf(sortedCorrections.mapValues { (_, entry) -> entry.word })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Learned glide corrections") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sortedCorrections.forEach { (path, entry) ->
                    val editedWord = editedCorrections[path] ?: entry.word
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Path: $path", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Accepted ${entry.acceptedCount}, rejected ${entry.rejectedCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = editedWord,
                            onValueChange = { next ->
                                editedCorrections = editedCorrections.toMutableMap().apply {
                                    this[path] = next.lowercase().filter { char -> char in 'a'..'z' }.take(24)
                                }
                            },
                            label = { Text("Replacement word") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.setGlideCorrection(path, editedWord)
                                    }
                                },
                                enabled = editedWord.isNotBlank() && editedWord != entry.word,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.removeGlideCorrection(path)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    scope.launch { repository.clearGlideCorrections() }
                    onDismiss()
                },
            ) {
                Text("Clear all")
            }
        },
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSection(title, body)
            content()
        }
    }
}

@Composable
private fun KeymapPreview(
    title: String,
    preferences: KeyboardPreferences,
    orientation: Int,
    geometry: KeyboardGeometry,
) {
    val layoutEngine = remember { LayoutEngine() }
    val customizationEngine = remember { CustomizationEngine() }
    val themeEngine = remember { ThemeEngine() }
    val theme = themeEngine.resolve(
        preset = preferences.themePreset,
        portrait = preferences.portraitGeometry,
        landscape = preferences.landscapeGeometry,
        customTheme = preferences.customTheme,
    )
    val layout = customizationEngine.apply(
        layout = layoutEngine.layoutFor(
            KeyboardState(
                activeLayoutId = preferences.layoutId,
                edgeKeyWidthScale = preferences.edgeKeyWidthScale,
            ),
            orientation,
        ),
        customization = preferences.customizationState(),
    )
    val previewHeight = ((geometry.keyboardHeightPercent / 75f) * 220f).coerceIn(98f, 220f).dp

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$title: ${geometry.keyboardHeightPercent.toInt()}% height, ${geometry.horizontalMarginDp.toInt()}dp L/R, ${geometry.bottomMarginDp.toInt()}dp bottom",
            style = MaterialTheme.typography.labelLarge,
        )
        KeyboardLayoutCanvas(
            layout = layout,
            theme = theme,
            geometry = geometry,
            labelStyle = preferences.keyLabelStyle,
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
        )
    }
}

@Composable
private fun KeyboardLayoutCanvas(
    layout: KeyboardLayout,
    theme: KeyboardTheme,
    geometry: KeyboardGeometry,
    labelStyle: KeyLabelStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        drawRect(Color(theme.colors.background))
        val rows = layout.rows
        if (rows.isEmpty()) return@Canvas

        val topMargin = PREVIEW_TOP_MARGIN_DP * density.density
        val bottomMargin = geometry.bottomMarginDp * density.density
        val rowGap = geometry.rowGapDp * density.density
        val keyGap = geometry.keyGapDp * density.density
        val horizontalMargin = geometry.horizontalMarginDp * density.density
        val rowHeight = (size.height - topMargin - bottomMargin - rowGap * (rows.size - 1)) / rows.size
        var top = topMargin
        rows.forEach { row ->
            val occupiedWeight = row.startInsetWeight + row.endInsetWeight +
                row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val layoutWeight = row.layoutWeight?.coerceAtLeast(occupiedWeight) ?: occupiedWeight
            val slackWeight = layoutWeight - occupiedWeight
            val alignmentInsetWeight = when (row.alignment) {
                RowAlignment.START -> 0f
                RowAlignment.CENTER -> slackWeight / 2f
                RowAlignment.END -> slackWeight
            }
            val gapCount = (row.keys.size - 1).coerceAtLeast(0)
            val availableWidth = size.width - horizontalMargin * 2 - keyGap * gapCount
            val widthUnit = availableWidth / layoutWeight
            var left = horizontalMargin + widthUnit * (alignmentInsetWeight + row.startInsetWeight)

            row.keys.forEach { key ->
                val keyWidth = widthUnit * key.weight
                if (!key.isSpacer) {
                    drawPreviewKey(key, theme, geometry, labelStyle, left, top, keyWidth, rowHeight, density.density, labelPaint)
                }
                left += keyWidth + keyGap
            }
            top += rowHeight + rowGap
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewKey(
    key: KeySpec,
    theme: KeyboardTheme,
    geometry: KeyboardGeometry,
    labelStyle: KeyLabelStyle,
    left: Float,
    top: Float,
    keyWidth: Float,
    rowHeight: Float,
    density: Float,
    labelPaint: Paint,
) {
    val radius = geometry.keyRadiusDp * density
    val borderWidth = geometry.borderWidthDp * density
    val topLeft = Offset(left, top)
    val size = Size(keyWidth, rowHeight)
    drawRoundRect(
        color = Color(theme.colors.keyFill),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(radius, radius),
    )
    drawRoundRect(
        color = Color(theme.colors.keyStroke),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = borderWidth),
    )

    val display = key.resolvedDisplay(shiftActive = false, labelStyle = labelStyle)
    labelPaint.color = theme.colors.keyText.withCombinedAlpha((labelStyle.labelOpacity * 255).toInt())
    labelPaint.isFakeBoldText = labelStyle.fontWeight >= 600f
    val label = display.icon?.fallbackLabel() ?: display.label
    labelPaint.textSize = density * if (label.length > 4) labelStyle.primaryTextSizeSp - 4f else labelStyle.primaryTextSizeSp - 2f
    val baseline = top + rowHeight / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(label, left + keyWidth / 2f, baseline, labelPaint)

    val secondary = display.secondaryIcon?.fallbackLabel() ?: display.secondaryLabel
    if (!secondary.isNullOrBlank()) {
        labelPaint.textSize = density * labelStyle.secondaryTextSizeSp
        val secondaryBaseline = top + rowHeight * 0.28f - (labelPaint.descent() + labelPaint.ascent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(secondary, left + keyWidth * 0.78f, secondaryBaseline, labelPaint)
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
private fun SelectButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val text = if (selected) "$label selected" else label
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    }
}

@Composable
private fun HexColorControl(
    label: String,
    color: Int,
    onApply: (Int) -> Unit,
) {
    var value by remember(label, color) { mutableStateOf(color.toHexColor()) }
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp),
            ) {
                drawRoundRect(
                    color = Color(color.opaque()),
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next -> value = next.take(9) },
                label = { Text("#RRGGBB") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { parseHexColor(value)?.let(onApply) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Apply")
            }
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Pick RGB")
            }
        }
    }
    if (showPicker) {
        RgbColorPickerDialog(
            title = label,
            initialColor = parseHexColor(value) ?: color.opaque(),
            onDismiss = { showPicker = false },
            onApply = { selectedColor ->
                value = selectedColor.toHexColor()
                onApply(selectedColor)
                showPicker = false
            },
        )
    }
}

@Composable
private fun RgbColorPickerDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var red by remember(initialColor) { mutableStateOf(initialColor.redChannel().toFloat()) }
    var green by remember(initialColor) { mutableStateOf(initialColor.greenChannel().toFloat()) }
    var blue by remember(initialColor) { mutableStateOf(initialColor.blueChannel().toFloat()) }
    val selectedColor = rgbColor(red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title RGB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    drawRoundRect(
                        color = Color(selectedColor),
                        size = size,
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    )
                }
                Text(selectedColor.toHexColor(), style = MaterialTheme.typography.labelLarge)
                RgbChannelSlider("Red", red) { red = it }
                RgbChannelSlider("Green", green) { green = it }
                RgbChannelSlider("Blue", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedColor) }) {
                Text("Use color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RgbChannelSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label: ${value.toInt().coerceIn(0, 255)}")
        Slider(
            value = value.coerceIn(0f, 255f),
            onValueChange = onChange,
            valueRange = 0f..255f,
        )
    }
}

@Composable
private fun OpacitySlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label: ${(value.coerceIn(0f, 1f) * 100).toInt()}%")
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun GeometryControls(
    orientation: GeometryOrientation,
    geometry: KeyboardGeometry,
    onChange: (GeometryField, Float) -> Unit,
) {
    GeometrySlider(
        "Height",
        geometry.keyboardHeightPercent,
        orientation,
        GeometryField.KEYBOARD_HEIGHT_PERCENT,
        onChange,
        unit = "%",
    )
    GeometrySlider("Radius", geometry.keyRadiusDp, orientation, GeometryField.KEY_RADIUS, onChange)
    GeometrySlider("Border", geometry.borderWidthDp, orientation, GeometryField.BORDER_WIDTH, onChange)
    GeometrySlider("Key gap", geometry.keyGapDp, orientation, GeometryField.KEY_GAP, onChange)
    GeometrySlider("L/R margin", geometry.horizontalMarginDp, orientation, GeometryField.HORIZONTAL_MARGIN, onChange)
    GeometrySlider("Bottom margin", geometry.bottomMarginDp, orientation, GeometryField.BOTTOM_MARGIN, onChange)
    GeometrySlider("Row gap", geometry.rowGapDp, orientation, GeometryField.ROW_GAP, onChange)
}

@Composable
private fun EdgeKeyWidthSlider(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Rows 2-4 edge key width: ${(value * 100).toInt()}%")
        Slider(
            value = value.coerceIn(0.6f, 1.1f),
            onValueChange = onChange,
            valueRange = 0.6f..1.1f,
        )
    }
}

@Composable
private fun GlideDwellThresholdSlider(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Dwell key threshold: ${(value.coerceIn(0f, 1f) * 100).toInt()}%")
        Slider(
            value = value.coerceIn(MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD, MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD),
            onValueChange = onChange,
            valueRange = MIN_GLIDE_DWELL_ACTIVATION_THRESHOLD..MAX_GLIDE_DWELL_ACTIVATION_THRESHOLD,
        )
    }
}

@Composable
private fun GeometrySlider(
    label: String,
    value: Float,
    orientation: GeometryOrientation,
    field: GeometryField,
    onChange: (GeometryField, Float) -> Unit,
    unit: String = "px",
) {
    val min = field.sliderMin
    val max = field.sliderMax
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${orientation.name.lowercase().replaceFirstChar { it.uppercase() }} $label: ${value.toInt()}$unit")
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange(field, it) },
            valueRange = min..max,
        )
    }
}

private val GeometryField.sliderMin: Float
    get() = when (this) {
        GeometryField.KEYBOARD_HEIGHT_PERCENT -> 15f
        else -> 0f
    }

private val GeometryField.sliderMax: Float
    get() = when (this) {
        GeometryField.KEYBOARD_HEIGHT_PERCENT -> 75f
        GeometryField.BORDER_WIDTH -> 8f
        GeometryField.HORIZONTAL_MARGIN,
        GeometryField.BOTTOM_MARGIN -> 40f
        else -> 32f
    }

@Composable
private fun KeyLabelStyleSlider(
    label: String,
    value: Float,
    field: KeyLabelStyleField,
    onChange: (KeyLabelStyleField, Float) -> Unit,
) {
    val min = field.sliderMin
    val max = field.sliderMax
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label: ${field.displayValue(value)}")
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange(field, it) },
            valueRange = min..max,
        )
    }
}

private val KeyLabelStyleField.sliderMin: Float
    get() = when (this) {
        KeyLabelStyleField.PRIMARY_TEXT_SIZE -> 10f
        KeyLabelStyleField.SECONDARY_TEXT_SIZE -> 6f
        KeyLabelStyleField.FONT_WEIGHT -> 300f
        KeyLabelStyleField.LABEL_OPACITY -> 0.35f
    }

private val KeyLabelStyleField.sliderMax: Float
    get() = when (this) {
        KeyLabelStyleField.PRIMARY_TEXT_SIZE -> 24f
        KeyLabelStyleField.SECONDARY_TEXT_SIZE -> 16f
        KeyLabelStyleField.FONT_WEIGHT -> 900f
        KeyLabelStyleField.LABEL_OPACITY -> 1f
    }

private fun KeyLabelStyleField.displayValue(value: Float): String {
    return when (this) {
        KeyLabelStyleField.LABEL_OPACITY -> "${(value * 100).toInt()}%"
        KeyLabelStyleField.FONT_WEIGHT -> value.toInt().toString()
        else -> "${value.toInt()}sp"
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
    OptionalKeyControl("fn", "Fn"),
    OptionalKeyControl("settings", "Settings"),
    OptionalKeyControl("mic", "Mic"),
    OptionalKeyControl("num_toggle", "Numpad toggle"),
    OptionalKeyControl("left", "Left arrow"),
    OptionalKeyControl("up", "Up arrow"),
    OptionalKeyControl("down", "Down arrow"),
    OptionalKeyControl("right", "Right arrow"),
)

private val actionSlots = listOf(
    ActionSlotControl("esc", "Esc slot", KeyAction(KeyActionType.ESCAPE)),
    ActionSlotControl("tab", "Tab slot", KeyAction(KeyActionType.TAB)),
    ActionSlotControl("ctrl", "Ctrl slot", KeyAction(KeyActionType.CTRL)),
    ActionSlotControl("alt", "Alt slot", KeyAction(KeyActionType.ALT)),
    ActionSlotControl("fn", "Fn slot", KeyAction(KeyActionType.SWITCH_FN)),
    ActionSlotControl("symbols", "Sym/Fn slot", KeyAction(KeyActionType.SWITCH_SYMBOLS)),
    ActionSlotControl("settings", "Settings slot", KeyAction(KeyActionType.SETTINGS)),
    ActionSlotControl("left", "Left slot", KeyAction(KeyActionType.ARROW_LEFT)),
    ActionSlotControl("up", "Up slot", KeyAction(KeyActionType.ARROW_UP)),
    ActionSlotControl("down", "Down slot", KeyAction(KeyActionType.ARROW_DOWN)),
    ActionSlotControl("right", "Right slot", KeyAction(KeyActionType.ARROW_RIGHT)),
    ActionSlotControl("enter", "Enter slot", KeyAction(KeyActionType.ENTER)),
    ActionSlotControl("delete", "Delete slot", KeyAction(KeyActionType.DELETE)),
    ActionSlotControl("mic", "Mic slot", KeyAction(KeyActionType.MICROPHONE)),
    ActionSlotControl("num_toggle", "Numpad slot", KeyAction(KeyActionType.NUMPAD_TOGGLE)),
)

private val actionCycle = listOf(
    KeyAction(KeyActionType.ESCAPE),
    KeyAction(KeyActionType.TAB),
    KeyAction(KeyActionType.CTRL),
    KeyAction(KeyActionType.ALT),
    KeyAction(KeyActionType.SWITCH_FN),
    KeyAction(KeyActionType.DELETE),
    KeyAction(KeyActionType.ENTER),
    KeyAction(KeyActionType.ARROW_LEFT),
    KeyAction(KeyActionType.ARROW_UP),
    KeyAction(KeyActionType.ARROW_DOWN),
    KeyAction(KeyActionType.ARROW_RIGHT),
    KeyAction(KeyActionType.NUMPAD_TOGGLE),
    KeyAction(KeyActionType.SWITCH_SYMBOLS),
    KeyAction(KeyActionType.SETTINGS),
    KeyAction(KeyActionType.MICROPHONE),
    KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_HOME, "Home"),
    KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_END, "End"),
    KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_UP, "PgUp"),
    KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn"),
    KeyAction.keyEvent(KeyEvent.KEYCODE_INSERT, "Ins"),
    KeyAction.keyEvent(KeyEvent.KEYCODE_FORWARD_DEL, "FDel"),
    KeyAction.text("|"),
    KeyAction.text("~"),
)

private data class KeyDisplayControl(
    val id: String,
    val label: String,
    val icons: List<KeyIcon>,
) {
    fun nextIcon(current: KeyIcon?): KeyIcon? {
        val cycle = listOf<KeyIcon?>(null) + icons
        val index = cycle.indexOf(current).takeIf { it >= 0 } ?: 0
        return cycle[(index + 1).floorMod(cycle.size)]
    }
}

private val keyDisplayControls = listOf(
    KeyDisplayControl("esc", "Esc", listOf(KeyIcon.ESC)),
    KeyDisplayControl("tab", "Tab", listOf(KeyIcon.TAB)),
    KeyDisplayControl("ctrl", "Ctrl", listOf(KeyIcon.CTRL)),
    KeyDisplayControl("alt", "Alt", listOf(KeyIcon.ALT)),
    KeyDisplayControl("shift", "Left Shift", listOf(KeyIcon.SHIFT)),
    KeyDisplayControl("shift_right", "Right Shift", listOf(KeyIcon.SHIFT)),
    KeyDisplayControl("symbols", "Symbols", listOf(KeyIcon.SYMBOLS)),
    KeyDisplayControl("fn", "Fn", listOf(KeyIcon.FN)),
    KeyDisplayControl("space", "Space", listOf(KeyIcon.SPACE_BAR)),
    KeyDisplayControl("settings", "Settings", listOf(KeyIcon.GEAR)),
    KeyDisplayControl("delete", "Backspace", listOf(KeyIcon.BACKSPACE)),
    KeyDisplayControl("enter", "Enter", listOf(KeyIcon.ENTER)),
    KeyDisplayControl("mic", "Mic", listOf(KeyIcon.MIC)),
    KeyDisplayControl("num_toggle", "Numpad", listOf(KeyIcon.NUMPAD)),
    KeyDisplayControl("left", "Left arrow", listOf(KeyIcon.ARROW_LEFT)),
    KeyDisplayControl("up", "Up arrow", listOf(KeyIcon.ARROW_UP)),
    KeyDisplayControl("down", "Down arrow", listOf(KeyIcon.ARROW_DOWN)),
    KeyDisplayControl("right", "Right arrow", listOf(KeyIcon.ARROW_RIGHT)),
)

private const val PREVIEW_TOP_MARGIN_DP = 4f

private fun nextAction(current: KeyAction): KeyAction {
    val index = actionCycle.indexOfFirst { action ->
        action.type == current.type &&
            action.text == current.text &&
            action.keyCode == current.keyCode &&
            action.label == current.label
    }
    return actionCycle[(index + 1).floorMod(actionCycle.size)]
}

private fun Int.floorMod(modulus: Int): Int {
    return ((this % modulus) + modulus) % modulus
}

private val ThemePreset.label: String
    get() = name.lowercase().split("_").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

private val CustomThemeColorField.label: String
    get() = when (this) {
        CustomThemeColorField.BACKGROUND -> "Keyboard background"
        CustomThemeColorField.KEY_FILL -> "Key background"
        CustomThemeColorField.KEY_STROKE -> "Key border"
        CustomThemeColorField.KEY_TEXT -> "Key label"
        CustomThemeColorField.PRESSED_FILL -> "Pressed key"
        CustomThemeColorField.ACTIVE_MODIFIER_FILL -> "Active modifier"
    }

private val CustomThemeOpacityField.label: String
    get() = when (this) {
        CustomThemeOpacityField.BACKGROUND_IMAGE -> "Background image opacity"
        CustomThemeOpacityField.KEY_FILL -> "Key background opacity"
        CustomThemeOpacityField.KEY_STROKE -> "Key border opacity"
        CustomThemeOpacityField.KEY_TEXT -> "Key label opacity"
    }

private fun CustomThemeConfig.colorFor(field: CustomThemeColorField): Int {
    return when (field) {
        CustomThemeColorField.BACKGROUND -> backgroundColor
        CustomThemeColorField.KEY_FILL -> keyFillColor
        CustomThemeColorField.KEY_STROKE -> keyStrokeColor
        CustomThemeColorField.KEY_TEXT -> keyTextColor
        CustomThemeColorField.PRESSED_FILL -> pressedFillColor
        CustomThemeColorField.ACTIVE_MODIFIER_FILL -> activeModifierFillColor
    }
}

private fun CustomThemeConfig.opacityFor(field: CustomThemeOpacityField): Float {
    return when (field) {
        CustomThemeOpacityField.BACKGROUND_IMAGE -> backgroundImageOpacity
        CustomThemeOpacityField.KEY_FILL -> keyFillOpacity
        CustomThemeOpacityField.KEY_STROKE -> keyStrokeOpacity
        CustomThemeOpacityField.KEY_TEXT -> keyTextOpacity
    }
}

private fun Int.toHexColor(): String {
    return "#%06X".format(this and 0x00FFFFFF)
}

private fun parseHexColor(value: String): Int? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    return normalized.toLongOrNull(16)?.toInt()?.opaque()
}

private fun rgbColor(red: Int, green: Int, blue: Int): Int {
    return 0xFF000000.toInt() or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)
}

private fun Int.redChannel(): Int {
    return (this shr 16) and 0xFF
}

private fun Int.greenChannel(): Int {
    return (this shr 8) and 0xFF
}

private fun Int.blueChannel(): Int {
    return this and 0xFF
}

private val GlidePathTolerance.label: String
    get() = when (this) {
        GlidePathTolerance.STRICT -> "Strict"
        GlidePathTolerance.BALANCED -> "Balanced"
        GlidePathTolerance.LOOSE -> "Loose"
    }

private val GlideSpatialPrecision.label: String
    get() = when (this) {
        GlideSpatialPrecision.FORGIVING -> "Forgiving"
        GlideSpatialPrecision.STANDARD -> "Standard"
        GlideSpatialPrecision.PRECISE -> "Precise"
    }

private val GlideDwellSensitivity.label: String
    get() = when (this) {
        GlideDwellSensitivity.OFF -> "Off"
        GlideDwellSensitivity.STANDARD -> "Standard"
        GlideDwellSensitivity.HIGH -> "High"
    }

private val GlideImportedWordsPriority.label: String
    get() = when (this) {
        GlideImportedWordsPriority.NORMAL -> "Normal"
        GlideImportedWordsPriority.HIGH -> "High"
    }

private val GlideRawFallbackMode.label: String
    get() = when (this) {
        GlideRawFallbackMode.OFF -> "Off"
        GlideRawFallbackMode.SHORT_ONLY -> "Short paths only"
        GlideRawFallbackMode.ALWAYS -> "Always"
    }

private fun Int.withCombinedAlpha(alpha: Int): Int {
    val baseAlpha = (this ushr 24) and 0xFF
    val nextAlpha = (baseAlpha * (alpha.coerceIn(0, 255) / 255f)).toInt()
    return (this and 0x00FFFFFF) or (nextAlpha shl 24)
}
