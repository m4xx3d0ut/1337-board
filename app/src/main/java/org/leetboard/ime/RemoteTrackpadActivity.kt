package org.leetboard.ime

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.leetboard.ime.engine.CustomizationEngine
import org.leetboard.ime.engine.FrequencyContextGlidePredictionEngine
import org.leetboard.ime.engine.GestureTypingEngine
import org.leetboard.ime.engine.GlideDictionaryLoader
import org.leetboard.ime.engine.GlidePredictionContext
import org.leetboard.ime.engine.GlideTouchTrace
import org.leetboard.ime.engine.GlideUserLanguageModel
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.SpeechInputEngine
import org.leetboard.ime.engine.SpeechInputEngineState
import org.leetboard.ime.engine.SpeechInsertion
import org.leetboard.ime.engine.SpeechStartResult
import org.leetboard.ime.engine.TextContextPolicy
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.engine.applyKeyboardCapitalization
import org.leetboard.ime.engine.formatSpeechInsertion
import org.leetboard.ime.engine.shouldInsertLeadingSpaceBeforeText
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.activeKeyIds
import org.leetboard.ime.prefs.BluetoothTrackpadKeepScreenOnMode
import org.leetboard.ime.prefs.BluetoothTrackpadMacroKey
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.prefs.layoutIdForOrientation
import org.leetboard.ime.remote.BluetoothHidController
import org.leetboard.ime.remote.BluetoothHidControllerRegistry
import org.leetboard.ime.remote.BluetoothHidSupport
import org.leetboard.ime.remote.RemoteHidDevice
import org.leetboard.ime.remote.RemoteHidKeyMapper
import org.leetboard.ime.remote.RemoteHidKeyRouter
import org.leetboard.ime.remote.RemoteHidState
import org.leetboard.ime.remote.RemoteHidStatus
import org.leetboard.ime.ui.KeyboardInputView

class RemoteTrackpadActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val layoutEngine = LayoutEngine()
    private val customizationEngine = CustomizationEngine()
    private val themeEngine = ThemeEngine()
    private val textContextPolicy = TextContextPolicy()
    private lateinit var preferenceRepository: PreferenceRepository
    private lateinit var bluetoothHidController: BluetoothHidController
    private lateinit var remoteHidKeyRouter: RemoteHidKeyRouter
    private lateinit var speechInputEngine: SpeechInputEngine
    private lateinit var gestureTypingEngine: GestureTypingEngine
    private lateinit var rootLayout: LinearLayout
    private lateinit var headerLayout: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var localButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var padButton: Button
    private lateinit var keyboardInputView: KeyboardInputView
    private var preferences = KeyboardPreferences.defaults()
    private var remoteHidState = RemoteHidState(status = RemoteHidStatus.DISABLED)
    private var keyboardState = KeyboardState()
    private var targetAddress: String? = null
    private var targetName: String? = null
    private var preferencesLoaded = false
    private var autoConnectEnabled = true
    private var remoteKeyboardVisible = true
    private var speechUiState = RemoteSpeechUiState.IDLE
    private var speechPushToTalkActive = false
    private var speechPushToTalkStartedAtMs = 0L
    private var speechPushToTalkStopPending = false
    private var speechResetJob: Job? = null
    private var remoteSpeechSendJob: Job? = null
    private var inactiveDimJob: Job? = null
    private var powerReceiverRegistered = false
    private val glideUserLanguageModel = GlideUserLanguageModel()
    private val remoteTextContext = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyDisplayPowerPolicy()
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        connectSelectedTarget()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showToast("Mic permission granted")
        } else {
            updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
            showToast("Microphone permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        targetAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        targetName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        preferenceRepository = PreferenceRepository(applicationContext)
        bluetoothHidController = BluetoothHidControllerRegistry.acquire(applicationContext)
        val glideDictionaryLoader = GlideDictionaryLoader(this)
        activityScope.launch(Dispatchers.IO) {
            glideUserLanguageModel.loadFrom(GlideUserLanguageModel.storageFile(filesDir))
        }
        gestureTypingEngine = GestureTypingEngine(
            textContextPolicy = textContextPolicy,
            predictionEngine = FrequencyContextGlidePredictionEngine(glideUserLanguageModel),
            wordsProvider = glideDictionaryLoader::loadWords,
        )
        speechInputEngine = SpeechInputEngine(this, textContextPolicy)
        remoteHidKeyRouter = RemoteHidKeyRouter { chord ->
            bluetoothHidController.sendKeyboardChord(chord.modifiers, chord.usage)
        }
        setContentView(buildContentView())
        keyboardInputView.post { hideSystemBars() }
        observeState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BluetoothHidSupport.hasConnectPermission(this)) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun onDestroy() {
        unregisterPowerReceiver()
        speechResetJob?.cancel()
        remoteSpeechSendJob?.cancel()
        inactiveDimJob?.cancel()
        speechInputEngine.destroy()
        activityScope.cancel()
        BluetoothHidControllerRegistry.release(bluetoothHidController)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        registerPowerReceiver()
        applyDisplayPowerPolicy()
    }

    override fun onPause() {
        inactiveDimJob?.cancel()
        restoreWindowBrightness()
        unregisterPowerReceiver()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            markUserActivity()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (
            event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            markUserActivity()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun buildContentView(): LinearLayout {
        statusLabel = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), 0, dp(10), 0)
        }
        localButton = Button(this).apply {
            text = "LOCAL"
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            minimumHeight = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { switchToLocalInput() }
        }
        padButton = Button(this).apply {
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            minimumHeight = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { toggleTrackpad() }
        }
        keyboardButton = Button(this).apply {
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            minimumHeight = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { toggleKeyboardVisibility() }
        }
        keyboardInputView = KeyboardInputView(this).also { view ->
            view.keyboardView.onKey = { action, heldModifiers ->
                handleKeyAction(action, heldModifiers)
                renderRemoteSurface()
            }
            view.keyboardView.onGlide = { path, touchTrace, heldModifiers ->
                handleGlide(path, touchTrace, heldModifiers)
                renderRemoteSurface()
            }
            view.keyboardView.onFnHoldChanged = { active ->
                keyboardState = keyboardState.copy(fnHold = active, fn = if (active) false else keyboardState.fn)
                renderRemoteSurface()
            }
            view.keyboardView.onQuickNavHoldChanged = { active ->
                keyboardState = keyboardState.copy(
                    quickNavHold = active,
                    numpad = if (active) false else keyboardState.numpad,
                )
                renderRemoteSurface()
            }
            view.keyboardView.onMicHoldChanged = { active ->
                handleSpeechPushToTalk(active)
                renderRemoteSurface()
            }
            view.onQuickModifier = { action ->
                handleKeyAction(action, HeldModifiers())
                renderRemoteSurface()
            }
            view.onRemotePointerReport = { report ->
                if (remoteHidState.connected) {
                    bluetoothHidController.sendMouseReport(
                        buttons = report.buttons,
                        dx = report.dx,
                        dy = report.dy,
                        wheel = report.wheel,
                    )
                }
            }
            view.onRemoteMacro = { macro ->
                handleTrackpadMacro(macro)
            }
        }
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            headerLayout = LinearLayout(this@RemoteTrackpadActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
                addView(
                    statusLabel,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f,
                    ),
                )
                addView(
                    keyboardButton,
                    headerButtonLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    padButton,
                    headerButtonLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    localButton,
                    headerButtonLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            addView(
                headerLayout,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44),
                ),
            )
            addView(
                keyboardInputView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
        return rootLayout
    }

    private fun observeState() {
        activityScope.launch {
            preferenceRepository.preferences.collectLatest { nextPreferences ->
                val firstPreferences = !preferencesLoaded
                gestureTypingEngine.setCorrections(
                    if (nextPreferences.glideCorrectionLearningEnabled) {
                        nextPreferences.glideCorrections
                    } else {
                        emptyMap()
                    },
                )
                preferences = nextPreferences
                preferencesLoaded = true
                renderRemoteSurface()
                if (firstPreferences && autoConnectEnabled) {
                    connectSelectedTarget()
                }
            }
        }
        activityScope.launch {
            bluetoothHidController.state.collectLatest { nextState ->
                remoteHidState = nextState
                updateStatus()
                renderRemoteSurface()
            }
        }
    }

    private fun connectSelectedTarget() {
        if (!autoConnectEnabled || !preferencesLoaded) return
        val address = targetAddress ?: preferences.bluetoothActiveDeviceAddress
        if (address.isNullOrBlank()) {
            bluetoothHidController.setEnabled(false, null)
            updateStatus("Pick a Bluetooth host in 1337 Board settings")
            return
        }
        activityScope.launch {
            if (preferences.bluetoothActiveDeviceAddress != address) {
                preferenceRepository.setBluetoothActiveDeviceAddress(address)
            }
            if (!preferences.bluetoothRemoteEnabled) {
                preferenceRepository.setBluetoothRemoteEnabled(true)
            }
            if (!preferences.bluetoothTrackpadEnabled) {
                preferenceRepository.setBluetoothTrackpadEnabled(true)
            }
        }
        bluetoothHidController.setEnabled(true, address)
    }

    private fun renderRemoteSurface() {
        val orientation = resources.configuration.orientation
        val layoutState = keyboardState.copy(
            activeLayoutId = preferences.layoutIdForOrientation(orientation),
            keyPreviewEnabled = preferences.keyPreviewEnabled,
            stickyModifiersEnabled = preferences.stickyModifiersEnabled,
            shiftCapsLockEnabled = preferences.shiftCapsLockEnabled,
            edgeKeyWidthScale = preferences.edgeKeyWidthScale,
            compactBottomControlsRightHandEnabled = preferences.compactBottomControlsRightHandEnabled,
        )
        val layout = customizationEngine.apply(
            layout = layoutEngine.layoutFor(layoutState, orientation)
                .withSpeechUiState()
                .withFeatureUiState(),
            customization = preferences.customizationState(),
        )
        val theme = themeEngine.resolve(
            preset = preferences.themePreset,
            portrait = preferences.portraitGeometry,
            landscape = preferences.landscapeGeometry,
            customTheme = preferences.customTheme,
        )
        applyWindowTheme(theme)
        applyDisplayPowerPolicy()
        val effectiveTrackpadEnabled = preferences.bluetoothTrackpadEnabled || !remoteKeyboardVisible
        keyboardInputView.render(
            layout = layout,
            theme = theme,
            activeKeyIds = layoutState.activeKeyIds() +
                activeSpeechKeyIds(layout) +
                activeFeatureKeyIds(layout) +
                activeQuickModifierIds() +
                activeBluetoothTargetKeyIds(layout),
            keyPreviewEnabled = preferences.keyPreviewEnabled,
            keyHapticsEnabled = preferences.keyHapticsEnabled,
            stickyModifiersEnabled = preferences.stickyModifiersEnabled,
            keyLabelStyle = preferences.keyLabelStyle,
            glideTypingEnabled = preferences.gestureTypingEnabled,
            swipeUpActionsEnabled = preferences.swipeUpActionsEnabled,
            speechPushToTalkEnabled = preferences.speechPushToTalkEnabled,
            keyLongPressDelayMs = preferences.keyLongPressDelayMs,
            specialLongPressDelayMs = preferences.specialLongPressDelayMs,
            suggestionBarEnabled = false,
            suggestions = emptyList(),
            quickModifierBarEnabled = keyboardState.quickNavHold &&
                layoutState.activeLayoutId in QUICK_MODIFIER_BAR_LAYOUTS,
            activeQuickModifierIds = activeQuickModifierIds(),
            quickFunctionRowEnabled = layoutState.activeLayoutId == "qwerty4",
            remoteTrackpadEnabled = effectiveTrackpadEnabled,
            remoteTrackpadFillRemaining = true,
            remoteTrackpadPlacement = preferences.bluetoothTrackpadPlacement,
            remoteTrackpadHeightPercent = preferences.bluetoothTrackpadHeightPercent,
            remoteTrackpadSensitivity = preferences.bluetoothTrackpadSensitivity,
            remoteTrackpadScrollSensitivity = preferences.bluetoothTrackpadScrollSensitivity,
            remoteTrackpadInvertScrollEnabled = preferences.bluetoothTrackpadInvertScrollEnabled,
            remoteTrackpadTapToClickEnabled = preferences.bluetoothTrackpadTapToClickEnabled,
            remoteTrackpadDedicatedButtonsEnabled = preferences.bluetoothTrackpadDedicatedButtonsEnabled,
            keyboardSurfaceVisible = remoteKeyboardVisible,
            remoteTrackpadMacroPlacement = preferences.bluetoothTrackpadMacroPlacement,
            remoteTrackpadMacros = preferences.bluetoothTrackpadMacros,
        )
        updateStatus()
    }

    private fun headerButtonLayoutParams(width: Int, height: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height).apply {
            marginStart = dp(6)
        }
    }

    private fun handleKeyAction(action: KeyAction, heldModifiers: HeldModifiers) {
        if (handleBluetoothControlAction(action)) return
        if (!remoteHidState.connected) {
            showToast(remoteHidState.message ?: "Bluetooth host is not connected")
            return
        }
        val stateBeforeAction = keyboardState
        keyboardState = remoteHidKeyRouter.handle(action, keyboardState, heldModifiers)
        recordRemoteTextContext(action, stateBeforeAction, heldModifiers)
    }

    private fun handleGlide(
        path: List<String>,
        touchTrace: GlideTouchTrace?,
        heldModifiers: HeldModifiers,
    ) {
        if (!preferences.gestureTypingEnabled) return
        if (!remoteHidState.connected) {
            showToast(remoteHidState.message ?: "Bluetooth host is not connected")
            return
        }
        val predictionContext = GlidePredictionContext(textBeforeCursor = remoteTextBeforeCursor())
        val word = gestureTypingEngine.candidates(
            pathLabels = path,
            options = preferences.glideTypingOptions(),
            context = predictionContext,
            touchTrace = touchTrace,
            limit = REMOTE_GLIDE_CANDIDATE_LIMIT,
        ).firstOrNull()?.word ?: return
        val outputWord = word.applyKeyboardCapitalization(
            modifiers = keyboardState.modifiers,
            heldModifiers = heldModifiers,
            autoCapAfterPeriod = preferences.autoCapAfterPeriodEnabled,
            textBeforeCursor = remoteTextBeforeCursor(),
            allCapsOnShift = false,
        )
        val committedText = formatRemoteGlideCommitText(outputWord)
        val action = KeyAction.text(committedText)
        val routingState = keyboardState.copy(
            modifiers = keyboardState.modifiers.copy(shift = false),
        )
        keyboardState = remoteHidKeyRouter.handle(action, routingState, HeldModifiers())
        appendRemoteTextContext(RemoteHidKeyMapper.outputText(action.text.orEmpty(), routingState, HeldModifiers()))
        if (preferences.glidePredictiveRankingEnabled) {
            gestureTypingEngine.recordAcceptedWord(word, predictionContext)
            activityScope.launch(Dispatchers.IO) {
                glideUserLanguageModel.saveTo(GlideUserLanguageModel.storageFile(filesDir))
            }
        }
    }

    private fun handleTrackpadMacro(macro: BluetoothTrackpadMacroKey) {
        if (!remoteHidState.connected) {
            showToast(remoteHidState.message ?: "Bluetooth host is not connected")
            return
        }
        macro.steps.forEach { step ->
            val stateBeforeAction = keyboardState
            keyboardState = remoteHidKeyRouter.handle(step.action, keyboardState, step.heldModifiers)
            recordRemoteTextContext(step.action, stateBeforeAction, step.heldModifiers)
        }
        renderRemoteSurface()
    }

    private fun formatRemoteGlideCommitText(word: String): String {
        val beforeCursor = remoteTextBeforeCursor()
        val leadingSpace = if (shouldInsertLeadingSpaceBeforeText(beforeCursor, word)) " " else ""
        return "$leadingSpace$word "
    }

    private fun handleBluetoothControlAction(action: KeyAction): Boolean {
        when (action.type) {
            KeyActionType.SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            KeyActionType.TOGGLE_BLUETOOTH_REMOTE -> {
                if (preferences.bluetoothRemoteEnabled) {
                    switchToLocalInput()
                } else {
                    autoConnectEnabled = true
                    connectSelectedTarget()
                }
                return true
            }
            KeyActionType.BLUETOOTH_LOCAL_INPUT -> {
                switchToLocalInput()
                return true
            }
            KeyActionType.BLUETOOTH_DEVICE_NEXT -> {
                selectNextBluetoothDevice()
                return true
            }
            KeyActionType.BLUETOOTH_DEVICE_1 -> {
                selectBluetoothDeviceSlot(0)
                return true
            }
            KeyActionType.BLUETOOTH_DEVICE_2 -> {
                selectBluetoothDeviceSlot(1)
                return true
            }
            KeyActionType.BLUETOOTH_DEVICE_3 -> {
                selectBluetoothDeviceSlot(2)
                return true
            }
            KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD -> {
                toggleTrackpad()
                return true
            }
            KeyActionType.MICROPHONE,
            KeyActionType.TOGGLE_SPEECH_INPUT -> {
                handleSpeechInput()
                return true
            }
            KeyActionType.TOGGLE_GESTURE_TYPING -> {
                val nextEnabled = !preferences.gestureTypingEnabled
                preferences = preferences.copy(gestureTypingEnabled = nextEnabled)
                activityScope.launch {
                    preferenceRepository.setGestureTypingEnabled(nextEnabled)
                }
                showToast("Glide typing ${if (nextEnabled) "on" else "off"}")
                renderRemoteSurface()
                return true
            }
            KeyActionType.LANGUAGE_SWITCH -> return true
            else -> return false
        }
    }

    private fun selectNextBluetoothDevice() {
        val devices = configuredBluetoothSlotDevices()
        if (devices.isEmpty()) {
            showToast("Assign a BT hotkey slot in settings")
            return
        }
        val currentIndex = devices.indexOfFirst { device -> device.address == preferences.bluetoothActiveDeviceAddress }
        selectBluetoothDevice(devices[(currentIndex + 1).floorMod(devices.size)])
    }

    private fun selectBluetoothDeviceSlot(slotIndex: Int) {
        val address = preferences.bluetoothDeviceSlotAddress(slotIndex)
        val device = BluetoothHidSupport.bondedDevices(this).firstOrNull { it.address == address }
        if (device == null) {
            showToast("Assign BT${slotIndex + 1} in settings")
            return
        }
        selectBluetoothDevice(device)
    }

    private fun selectBluetoothDevice(device: RemoteHidDevice) {
        autoConnectEnabled = true
        targetAddress = device.address
        targetName = device.name
        activityScope.launch {
            preferenceRepository.setBluetoothActiveDeviceAddress(device.address)
            preferenceRepository.setBluetoothRemoteEnabled(true)
        }
        bluetoothHidController.selectDevice(device.address)
        showToast("Bluetooth host: ${device.name}")
    }

    private fun switchToLocalInput() {
        autoConnectEnabled = false
        bluetoothHidController.setEnabled(false, preferences.bluetoothActiveDeviceAddress)
        activityScope.launch {
            preferenceRepository.setBluetoothRemoteEnabled(false)
        }
        showToast("Local Android input")
        finish()
    }

    private fun toggleTrackpad() {
        val nextEnabled = !preferences.bluetoothTrackpadEnabled
        if (!remoteKeyboardVisible && !nextEnabled) {
            remoteKeyboardVisible = true
        }
        preferences = preferences.copy(bluetoothTrackpadEnabled = nextEnabled)
        activityScope.launch {
            preferenceRepository.setBluetoothTrackpadEnabled(nextEnabled)
        }
        renderRemoteSurface()
    }

    private fun toggleKeyboardVisibility() {
        remoteKeyboardVisible = !remoteKeyboardVisible
        if (!remoteKeyboardVisible && !preferences.bluetoothTrackpadEnabled) {
            preferences = preferences.copy(bluetoothTrackpadEnabled = true)
            activityScope.launch {
                preferenceRepository.setBluetoothTrackpadEnabled(true)
            }
        }
        renderRemoteSurface()
    }

    private fun configuredBluetoothSlotDevices(): List<RemoteHidDevice> {
        val bondedByAddress = BluetoothHidSupport.bondedDevices(this).associateBy { device -> device.address }
        return preferences.configuredBluetoothSlotAddresses().mapNotNull { address -> bondedByAddress[address] }
    }

    private fun activeBluetoothTargetKeyIds(layout: KeyboardLayout): Set<String> {
        return when (preferences.bluetoothActiveDeviceAddress) {
            preferences.bluetoothDeviceSlotAddress(0) -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_1)
            preferences.bluetoothDeviceSlotAddress(1) -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_2)
            preferences.bluetoothDeviceSlotAddress(2) -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_3)
            else -> emptySet()
        }
    }

    private fun KeyboardLayout.keyIdsForAction(type: KeyActionType): Set<String> {
        return rows.flatMap { row -> row.keys }
            .filter { key ->
                key.action.type == type ||
                    key.longPressAction?.type == type ||
                    key.swipeUpAction?.type == type
            }
            .map { key -> key.id }
            .toSet()
    }

    private fun activeQuickModifierIds(): Set<String> = buildSet {
        if (keyboardState.modifiers.ctrl) add("ctrl")
        if (keyboardState.modifiers.alt) add("alt")
        if (keyboardState.modifiers.fn) add("quick_fn")
    }

    private fun activeSpeechKeyIds(layout: KeyboardLayout): Set<String> {
        return if (speechUiState == RemoteSpeechUiState.LISTENING || speechUiState == RemoteSpeechUiState.PROCESSING) {
            layout.keyIdsForAction(KeyActionType.MICROPHONE)
        } else {
            emptySet()
        }
    }

    private fun activeFeatureKeyIds(layout: KeyboardLayout): Set<String> {
        return if (preferences.gestureTypingEnabled) {
            layout.keyIdsForAction(KeyActionType.SETTINGS)
        } else {
            emptySet()
        }
    }

    private fun handleSpeechInput() {
        if (!preferences.speechInputEnabled) {
            showToast("Enable mic input in settings")
            return
        }
        when (speechUiState) {
            RemoteSpeechUiState.LISTENING -> {
                if (speechInputEngine.stopListening()) {
                    updateSpeechUiState(RemoteSpeechUiState.PROCESSING)
                    showToast("Finishing speech")
                }
                return
            }
            RemoteSpeechUiState.PROCESSING -> {
                showToast("Processing speech")
                return
            }
            RemoteSpeechUiState.IDLE,
            RemoteSpeechUiState.COMPLETE,
            RemoteSpeechUiState.ERROR -> Unit
        }
        startSpeechInput("Listening")
    }

    private fun handleSpeechPushToTalk(active: Boolean) {
        if (!preferences.speechPushToTalkEnabled) return
        if (active) {
            if (!preferences.speechInputEnabled) {
                showToast("Enable mic input in settings")
                return
            }
            if (speechUiState == RemoteSpeechUiState.LISTENING || speechUiState == RemoteSpeechUiState.PROCESSING) return
            speechPushToTalkActive = true
            speechPushToTalkStopPending = false
            speechPushToTalkStartedAtMs = SystemClock.uptimeMillis()
            startSpeechInput("Hold to talk")
            return
        }

        if (!speechPushToTalkActive) return
        speechPushToTalkActive = false
        val heldForMs = SystemClock.uptimeMillis() - speechPushToTalkStartedAtMs
        if (heldForMs < MIN_PUSH_TO_TALK_HOLD_MS) {
            speechPushToTalkStopPending = false
            speechInputEngine.cancel()
            updateSpeechUiState(RemoteSpeechUiState.IDLE)
            return
        }
        if (speechUiState == RemoteSpeechUiState.LISTENING && speechInputEngine.stopListening()) {
            speechPushToTalkStopPending = true
            updateSpeechUiState(RemoteSpeechUiState.PROCESSING)
            showToast("Finishing speech")
        } else if (speechUiState == RemoteSpeechUiState.PROCESSING) {
            speechPushToTalkStopPending = true
        }
    }

    private fun startSpeechInput(startMessage: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!remoteHidState.connected) {
            updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
            showToast(remoteHidState.message ?: "Bluetooth host is not connected")
            return
        }
        updateSpeechUiState(RemoteSpeechUiState.LISTENING)
        when (
            val result = speechInputEngine.start(
                editorInfo = null,
                options = preferences.speechInputOptions(),
                onText = { text ->
                    commitSpeechTextToBluetoothRemote(text)
                    speechPushToTalkActive = false
                    speechPushToTalkStopPending = false
                    updateSpeechUiState(RemoteSpeechUiState.COMPLETE, resetAfter = true)
                    showToast("Speech sent")
                },
                onError = { message ->
                    speechPushToTalkActive = false
                    if (speechPushToTalkStopPending && message.isBenignPushToTalkStopError()) {
                        speechPushToTalkStopPending = false
                        updateSpeechUiState(RemoteSpeechUiState.IDLE)
                        showToast("No speech captured")
                        return@start
                    }
                    speechPushToTalkStopPending = false
                    updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
                    showToast(message)
                },
                onState = { state ->
                    when (state) {
                        SpeechInputEngineState.LISTENING -> updateSpeechUiState(RemoteSpeechUiState.LISTENING)
                        SpeechInputEngineState.PROCESSING -> updateSpeechUiState(RemoteSpeechUiState.PROCESSING)
                    }
                },
            )
        ) {
            SpeechStartResult.Started -> showToast(startMessage)
            SpeechStartResult.FeatureNotInstalled -> {
                speechPushToTalkActive = false
                speechPushToTalkStopPending = false
                updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
                showToast("No speech recognizer available")
            }
            is SpeechStartResult.Error -> {
                speechPushToTalkActive = false
                speechPushToTalkStopPending = false
                updateSpeechUiState(RemoteSpeechUiState.ERROR, resetAfter = true)
                showToast(result.message)
            }
        }
    }

    private fun commitSpeechTextToBluetoothRemote(text: String) {
        val insertion = formatSpeechInsertion(
            recognizedText = text,
            textBeforeCursor = remoteTextBeforeCursor(),
            options = preferences.speechTextAutomationOptions(),
        )
        if (insertion.text.isEmpty()) return
        sendSpeechInsertionToBluetoothRemote(insertion)
    }

    private fun sendSpeechInsertionToBluetoothRemote(insertion: SpeechInsertion) {
        val backspace = RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_DEL, KeyboardState(), HeldModifiers())
        val chords = RemoteHidKeyMapper.textChords(insertion.text, KeyboardState(), HeldModifiers())
        remoteSpeechSendJob?.cancel()
        remoteSpeechSendJob = activityScope.launch {
            bluetoothHidController.sendKeyboardReport(0, emptyList())
            delay(BLUETOOTH_SPEECH_CHORD_GAP_MS)
            repeat(insertion.deleteBeforeChars) {
                if (backspace != null) {
                    sendBluetoothChordPaced(backspace.modifiers, backspace.usage)
                    deleteRemoteTextContext(1)
                }
            }
            chords.forEach { chord ->
                sendBluetoothChordPaced(chord.modifiers, chord.usage)
            }
            bluetoothHidController.sendKeyboardReport(0, emptyList())
            appendRemoteTextContext(insertion.text)
        }
    }

    private suspend fun sendBluetoothChordPaced(
        modifier: Int,
        usage: Int,
    ) {
        bluetoothHidController.sendKeyboardReport(0, emptyList())
        delay(BLUETOOTH_SPEECH_KEY_UP_GUARD_MS)
        bluetoothHidController.sendKeyboardReport(modifier, listOf(usage))
        delay(BLUETOOTH_SPEECH_KEY_DOWN_MS)
        bluetoothHidController.sendKeyboardReport(0, emptyList())
        delay(BLUETOOTH_SPEECH_CHORD_GAP_MS)
    }

    private fun recordRemoteTextContext(
        action: KeyAction,
        stateBeforeAction: KeyboardState,
        heldModifiers: HeldModifiers,
    ) {
        when (action.type) {
            KeyActionType.COMMIT_TEXT -> {
                appendRemoteTextContext(
                    RemoteHidKeyMapper.outputText(action.text.orEmpty(), stateBeforeAction, heldModifiers),
                )
            }
            KeyActionType.SPACE -> appendRemoteTextContext(" ")
            KeyActionType.ENTER -> appendRemoteTextContext("\n")
            KeyActionType.TAB -> appendRemoteTextContext("\t")
            KeyActionType.DELETE -> {
                if (!heldModifiers.shift && !stateBeforeAction.modifiers.shift) {
                    deleteRemoteTextContext(1)
                }
            }
            else -> Unit
        }
    }

    private fun appendRemoteTextContext(text: String) {
        if (text.isEmpty()) return
        remoteTextContext.append(text)
        if (remoteTextContext.length > REMOTE_TEXT_CONTEXT_CHARS) {
            remoteTextContext.delete(0, remoteTextContext.length - REMOTE_TEXT_CONTEXT_CHARS)
        }
    }

    private fun deleteRemoteTextContext(count: Int) {
        if (count <= 0 || remoteTextContext.isEmpty()) return
        remoteTextContext.delete(
            (remoteTextContext.length - count).coerceAtLeast(0),
            remoteTextContext.length,
        )
    }

    private fun remoteTextBeforeCursor(): CharSequence {
        return remoteTextContext.takeLast(SPEECH_CONTEXT_CHARS)
    }

    private fun String.isBenignPushToTalkStopError(): Boolean {
        return this in PUSH_TO_TALK_BENIGN_STOP_ERRORS
    }

    private fun KeyboardLayout.withSpeechUiState(): KeyboardLayout {
        val secondaryLabel = when {
            !preferences.speechInputEnabled -> "Off"
            preferences.speechPushToTalkEnabled && speechUiState == RemoteSpeechUiState.IDLE -> "Hold"
            speechUiState == RemoteSpeechUiState.LISTENING -> "Stop"
            speechUiState == RemoteSpeechUiState.PROCESSING -> "..."
            speechUiState == RemoteSpeechUiState.COMPLETE -> "Done"
            speechUiState == RemoteSpeechUiState.ERROR -> "Error"
            else -> "Tap"
        }
        return copy(
            rows = rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        if (key.action.type == KeyActionType.MICROPHONE) {
                            key.copy(secondaryLabel = secondaryLabel)
                        } else {
                            key
                        }
                    },
                )
            },
        )
    }

    private fun KeyboardLayout.withFeatureUiState(): KeyboardLayout {
        val glideIcon = if (preferences.gestureTypingEnabled) KeyIcon.SWIPE else KeyIcon.SWIPE_OFF
        return copy(
            rows = rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        if (key.action.type == KeyActionType.SETTINGS) {
                            key.copy(secondaryIcon = glideIcon)
                        } else {
                            key
                        }
                    },
                )
            },
        )
    }

    private fun updateSpeechUiState(nextState: RemoteSpeechUiState, resetAfter: Boolean = false) {
        speechResetJob?.cancel()
        speechUiState = nextState
        renderRemoteSurface()
        if (resetAfter) {
            speechResetJob = activityScope.launch {
                delay(SPEECH_STATUS_RESET_MS)
                if (speechUiState == nextState) {
                    speechUiState = RemoteSpeechUiState.IDLE
                    renderRemoteSurface()
                }
            }
        }
    }

    private fun updateStatus(message: String? = null) {
        val label = targetName
            ?: remoteHidState.activeDeviceName
            ?: preferences.bluetoothActiveDeviceAddress
            ?: targetAddress
            ?: "No host"
        val stateText = message ?: remoteHidState.message ?: remoteHidState.status.displayLabel()
        statusLabel.text = "$label - $stateText"
        keyboardButton.text = if (remoteKeyboardVisible) "KB ON" else "KB OFF"
        padButton.text = if (preferences.bluetoothTrackpadEnabled) "PAD ON" else "PAD OFF"
    }

    private fun dp(value: Int): Int {
        return (resources.displayMetrics.density * value).toInt()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemBars() {
        val decorView = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun applyWindowTheme(theme: KeyboardTheme) {
        val backgroundColor = theme.colors.background
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))
        window.navigationBarColor = backgroundColor
        window.statusBarColor = backgroundColor
        rootLayout.setBackgroundColor(backgroundColor)
        headerLayout.setBackgroundColor(backgroundColor)
        statusLabel.setTextColor(theme.colors.keyText)
        styleHeaderButton(keyboardButton, theme, active = remoteKeyboardVisible)
        styleHeaderButton(padButton, theme, active = preferences.bluetoothTrackpadEnabled)
        styleHeaderButton(localButton, theme, active = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lightDecor = isLightColor(backgroundColor)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightDecor) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val lightDecor = isLightColor(backgroundColor)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightDecor) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    private fun styleHeaderButton(button: Button, theme: KeyboardTheme, active: Boolean) {
        button.setTextColor(theme.colors.keyText)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(2).toFloat()
            setColor(if (active) theme.colors.activeModifierFill else theme.colors.keyFill)
            setStroke(dp(1).coerceAtLeast(1), theme.colors.keyStroke)
        }
    }

    private fun registerPowerReceiver() {
        if (powerReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerStateReceiver, filter)
        }
        powerReceiverRegistered = true
    }

    private fun unregisterPowerReceiver() {
        if (!powerReceiverRegistered) return
        runCatching { unregisterReceiver(powerStateReceiver) }
        powerReceiverRegistered = false
    }

    private fun applyDisplayPowerPolicy() {
        if (shouldKeepScreenOn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (preferences.bluetoothTrackpadDimWhenInactiveEnabled) {
            scheduleInactiveDim()
        } else {
            inactiveDimJob?.cancel()
            restoreWindowBrightness()
        }
    }

    private fun shouldKeepScreenOn(): Boolean {
        return when (preferences.bluetoothTrackpadKeepScreenOnMode) {
            BluetoothTrackpadKeepScreenOnMode.OFF -> false
            BluetoothTrackpadKeepScreenOnMode.WHILE_CHARGING -> isDeviceCharging()
            BluetoothTrackpadKeepScreenOnMode.ALWAYS -> true
        }
    }

    private fun isDeviceCharging(): Boolean {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun markUserActivity() {
        restoreWindowBrightness()
        if (preferences.bluetoothTrackpadDimWhenInactiveEnabled) {
            scheduleInactiveDim()
        }
    }

    private fun scheduleInactiveDim() {
        inactiveDimJob?.cancel()
        if (!preferences.bluetoothTrackpadDimWhenInactiveEnabled) return
        inactiveDimJob = activityScope.launch {
            delay(TRACKPAD_INACTIVE_DIM_DELAY_MS)
            mainHandler.post { dimWindow() }
        }
    }

    private fun dimWindow() {
        if (!preferences.bluetoothTrackpadDimWhenInactiveEnabled) return
        val attributes = window.attributes
        attributes.screenBrightness = TRACKPAD_DIM_BRIGHTNESS
        window.attributes = attributes
    }

    private fun restoreWindowBrightness() {
        val attributes = window.attributes
        if (attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) return
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attributes
    }

    private fun isLightColor(color: Int): Boolean {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0
        val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return luminance > 0.55
    }

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }

    private fun RemoteHidStatus.displayLabel(): String {
        return name.lowercase().replace('_', ' ')
    }

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "org.leetboard.ime.extra.DEVICE_ADDRESS"
        const val EXTRA_DEVICE_NAME = "org.leetboard.ime.extra.DEVICE_NAME"
        private val QUICK_MODIFIER_BAR_LAYOUTS = setOf("qwerty4", "compact5")
        private const val SPEECH_STATUS_RESET_MS = 1600L
        private const val SPEECH_CONTEXT_CHARS = 80
        private const val REMOTE_TEXT_CONTEXT_CHARS = 240
        private const val REMOTE_GLIDE_CANDIDATE_LIMIT = 5
        private const val MIN_PUSH_TO_TALK_HOLD_MS = 300L
        private const val BLUETOOTH_SPEECH_KEY_UP_GUARD_MS = 6L
        private const val BLUETOOTH_SPEECH_KEY_DOWN_MS = 12L
        private const val BLUETOOTH_SPEECH_CHORD_GAP_MS = 18L
        private const val TRACKPAD_INACTIVE_DIM_DELAY_MS = 60_000L
        private const val TRACKPAD_DIM_BRIGHTNESS = 0.08f
        private val PUSH_TO_TALK_BENIGN_STOP_ERRORS = setOf(
            "Speech recognizer client error",
            "No speech recognized",
            "No speech heard",
        )
    }
}

private enum class RemoteSpeechUiState {
    IDLE,
    LISTENING,
    PROCESSING,
    COMPLETE,
    ERROR,
}
