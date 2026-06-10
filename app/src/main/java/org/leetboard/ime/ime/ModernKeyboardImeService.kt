package org.leetboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Toast
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
import org.leetboard.ime.engine.GlideCandidate
import org.leetboard.ime.engine.GlideDictionaryLoader
import org.leetboard.ime.engine.GlideCorrectionEntry
import org.leetboard.ime.engine.GlideGeometryScorer
import org.leetboard.ime.engine.GlidePredictionContext
import org.leetboard.ime.engine.GlideTouchTrace
import org.leetboard.ime.engine.GlideUserLanguageModel
import org.leetboard.ime.engine.GestureTypingEngine
import org.leetboard.ime.engine.KeyActionEngine
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.SpeechInputEngineState
import org.leetboard.ime.engine.SpeechInputEngine
import org.leetboard.ime.engine.SpeechInsertion
import org.leetboard.ime.engine.SpeechStartResult
import org.leetboard.ime.engine.TextContextPolicy
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.engine.TypedPredictionEngine
import org.leetboard.ime.engine.applyKeyboardCapitalization
import org.leetboard.ime.engine.findPendingGlideReplacementSpan
import org.leetboard.ime.engine.formatSpeechInsertion
import org.leetboard.ime.engine.normalizeWord
import org.leetboard.ime.engine.normalizeWordPrefix
import org.leetboard.ime.engine.pendingGlideCommitMatchesBeforeCursor
import org.leetboard.ime.engine.shouldInsertLeadingSpaceBeforeText
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.activeKeyIds
import org.leetboard.ime.model.clearTransientModifiers
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

class ModernKeyboardImeService : InputMethodService() {
    private val layoutEngine = LayoutEngine()
    private val customizationEngine = CustomizationEngine()
    private val textContextPolicy = TextContextPolicy()
    private val themeEngine = ThemeEngine()
    private val glideUserLanguageModel = GlideUserLanguageModel()

    private lateinit var gestureTypingEngine: GestureTypingEngine
    private lateinit var typedPredictionEngine: TypedPredictionEngine
    private lateinit var keyActionEngine: KeyActionEngine
    private lateinit var speechInputEngine: SpeechInputEngine
    private lateinit var preferenceRepository: PreferenceRepository
    private var bluetoothHidController: BluetoothHidController? = null
    private var remoteHidKeyRouter: RemoteHidKeyRouter? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keyboardInputView: KeyboardInputView? = null
    private var keyboardState = KeyboardState()
    private var preferences = KeyboardPreferences.defaults()
    private var contextHiddenKeyIds: Set<String> = emptySet()
    private var speechUiState = SpeechUiState.IDLE
    private var speechPushToTalkActive = false
    private var speechPushToTalkStartedAtMs = 0L
    private var speechPushToTalkStopPending = false
    private var speechResetJob: Job? = null
    private var glideSuggestionsJob: Job? = null
    private var glideCorrectionRecordJob: Job? = null
    private var pendingGlideUndo: PendingGlideUndo? = null
    private var pendingSuggestionCommit: PendingSuggestionCommit? = null
    private var pendingGlideCorrection: PendingGlideCorrection? = null
    private var glideLearningResetRevision: Int? = null
    private var glideSuggestions: List<String> = emptyList()
    private var suggestionMode = SuggestionMode.NONE
    private var remoteHidState = RemoteHidState(status = RemoteHidStatus.DISABLED)
    private val remoteTextContext = StringBuilder()
    private var remoteSpeechSendJob: Job? = null
    private var bluetoothRemoteLocalFocusIgnoreUntilMs = 0L

    override fun onCreate() {
        super.onCreate()
        val glideDictionaryLoader = GlideDictionaryLoader(this)
        serviceScope.launch(Dispatchers.IO) {
            glideUserLanguageModel.loadFrom(GlideUserLanguageModel.storageFile(filesDir))
        }
        gestureTypingEngine = GestureTypingEngine(
            textContextPolicy = textContextPolicy,
            predictionEngine = FrequencyContextGlidePredictionEngine(glideUserLanguageModel),
            wordsProvider = glideDictionaryLoader::loadWords,
        )
        typedPredictionEngine = TypedPredictionEngine(glideDictionaryLoader::loadWords, glideUserLanguageModel)
        keyActionEngine = KeyActionEngine(this)
        speechInputEngine = SpeechInputEngine(this, textContextPolicy)
        preferenceRepository = PreferenceRepository(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bluetoothHidController = BluetoothHidControllerRegistry.acquire(this)
            remoteHidKeyRouter = RemoteHidKeyRouter { chord ->
                bluetoothHidController?.sendKeyboardChord(chord.modifiers, chord.usage) == true
            }
            serviceScope.launch {
                bluetoothHidController?.state?.collectLatest { nextState ->
                    remoteHidState = nextState
                    renderKeyboard()
                }
            }
        } else {
            remoteHidState = RemoteHidState(status = RemoteHidStatus.API_TOO_OLD)
        }
        serviceScope.launch {
            preferenceRepository.preferences.collectLatest { nextPreferences ->
                val previousResetRevision = glideLearningResetRevision
                glideLearningResetRevision = nextPreferences.glideLearningResetRevision
                if (
                    previousResetRevision != null &&
                    previousResetRevision != nextPreferences.glideLearningResetRevision
                ) {
                    glideUserLanguageModel.clear()
                    persistGlideUserLanguageModel()
                }
                gestureTypingEngine.setCorrections(
                    if (nextPreferences.glideCorrectionLearningEnabled) {
                        nextPreferences.glideCorrections
                    } else {
                        emptyMap()
                    },
                )
                if (!nextPreferences.glideCorrectionLearningEnabled) clearPendingGlideCorrection()
                if (
                    nextPreferences.bluetoothRemoteEnabled &&
                    (
                        !preferences.bluetoothRemoteEnabled ||
                            nextPreferences.bluetoothActiveDeviceAddress != preferences.bluetoothActiveDeviceAddress
                    )
                ) {
                    markBluetoothRemoteUserActivation()
                }
                preferences = nextPreferences
                bluetoothHidController?.setEnabled(
                    enabled = nextPreferences.bluetoothRemoteEnabled,
                    activeDeviceAddress = nextPreferences.bluetoothActiveDeviceAddress,
                )
                if (!preferences.speechInputEnabled && speechUiState != SpeechUiState.IDLE) {
                    speechInputEngine.cancel()
                    speechUiState = SpeechUiState.IDLE
                    speechPushToTalkActive = false
                    speechPushToTalkStopPending = false
                }
                renderKeyboard()
            }
        }
    }

    override fun onDestroy() {
        speechResetJob?.cancel()
        glideSuggestionsJob?.cancel()
        glideCorrectionRecordJob?.cancel()
        remoteSpeechSendJob?.cancel()
        BluetoothHidControllerRegistry.release(bluetoothHidController)
        speechInputEngine.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onCreateInputView(): View {
        return KeyboardInputView(this).also { view ->
            keyboardInputView = view
            view.keyboardView.onKey = { action, heldModifiers ->
                handleKeyAction(action, heldModifiers)
                renderKeyboard()
            }
            view.keyboardView.onGlide = { path, touchTrace, heldModifiers ->
                handleGlide(path, touchTrace, heldModifiers)
                renderKeyboard()
            }
            view.keyboardView.onFnHoldChanged = { active ->
                keyboardState = keyboardState.copy(fnHold = active, fn = if (active) false else keyboardState.fn)
                renderKeyboard()
            }
            view.keyboardView.onQuickNavHoldChanged = { active ->
                keyboardState = keyboardState.copy(
                    quickNavHold = active,
                    numpad = if (active) false else keyboardState.numpad,
                )
                renderKeyboard()
            }
            view.keyboardView.onMicHoldChanged = { active ->
                handleSpeechPushToTalk(active)
                renderKeyboard()
            }
            view.onSuggestion = { word ->
                handleSuggestion(word)
                renderKeyboard()
            }
            view.onQuickModifier = { action ->
                handleKeyAction(action, HeldModifiers())
                renderKeyboard()
            }
            view.onRemotePointerReport = { report ->
                if (isBluetoothRemoteActive()) {
                    bluetoothHidController?.sendMouseReport(
                        buttons = report.buttons,
                        dx = report.dx,
                        dy = report.dy,
                        wheel = report.wheel,
                    )
                }
            }
            renderKeyboard()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        hideSystemImeSwitcher()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        switchBluetoothRemoteToLocalForAndroidFocus()
        val speechAllowed = speechInputEngine.isAvailable(attribute)
        contextHiddenKeyIds = buildSet {
            if (!speechAllowed) add("mic")
        }
        renderKeyboard()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            switchBluetoothRemoteToLocalForAndroidFocus()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        speechInputEngine.cancel()
        speechUiState = SpeechUiState.IDLE
        speechPushToTalkActive = false
        speechPushToTalkStopPending = false
        pendingGlideUndo = null
        pendingSuggestionCommit = null
        clearPendingGlideCorrection()
        clearGlideSuggestions()
        keyboardState = KeyboardState()
        renderKeyboard()
    }

    private fun renderKeyboard() {
        val orientation = resources.configuration.orientation
        val gestureTypingAllowed = preferences.gestureTypingEnabled && gestureTypingEngine.isEnabledFor(currentInputEditorInfo)
        val suggestionBarEnabled = gestureTypingAllowed || typedSuggestionsAllowed()
        val layoutState = keyboardState.copy(
            activeLayoutId = preferences.layoutIdForOrientation(orientation),
            keyPreviewEnabled = preferences.keyPreviewEnabled,
            stickyModifiersEnabled = preferences.stickyModifiersEnabled,
            shiftCapsLockEnabled = preferences.shiftCapsLockEnabled,
            edgeKeyWidthScale = preferences.edgeKeyWidthScale,
            compactBottomControlsRightHandEnabled = preferences.compactBottomControlsRightHandEnabled,
        )
        val quickModifierBarEnabled = keyboardState.quickNavHold &&
            layoutState.activeLayoutId in QUICK_MODIFIER_BAR_LAYOUTS
        val remoteTrackpadVisible = isBluetoothRemoteActive() && preferences.bluetoothTrackpadEnabled
        val layout = customizationEngine.apply(
            layout = layoutEngine.layoutFor(layoutState, orientation),
            customization = preferences.customizationState().let { customization ->
                customization.copy(
                    hiddenOptionalKeyIds = customization.hiddenOptionalKeyIds + contextHiddenKeyIds,
                )
            },
        ).withSpeechUiState().withFeatureUiState().withBluetoothUiState()
        keyboardInputView?.render(
            layout,
            themeEngine.resolve(
                preset = preferences.themePreset,
                portrait = preferences.portraitGeometry,
                landscape = preferences.landscapeGeometry,
                customTheme = preferences.customTheme,
            ),
            layoutState.activeKeyIds() + featureActiveKeyIds(layoutState, layout),
            preferences.keyPreviewEnabled,
            preferences.keyHapticsEnabled,
            preferences.stickyModifiersEnabled,
            preferences.keyLabelStyle,
            gestureTypingAllowed,
            preferences.swipeUpActionsEnabled,
            preferences.speechPushToTalkEnabled,
            preferences.keyLongPressDelayMs,
            preferences.specialLongPressDelayMs,
            suggestionBarEnabled,
            glideSuggestions,
            quickModifierBarEnabled,
            activeQuickModifierIds = activeQuickModifierIds(),
            quickFunctionRowEnabled = layoutState.activeLayoutId == "qwerty4",
            remoteTrackpadEnabled = remoteTrackpadVisible,
            remoteTrackpadPlacement = preferences.bluetoothTrackpadPlacement,
            remoteTrackpadHeightPercent = preferences.bluetoothTrackpadHeightPercent,
            remoteTrackpadSensitivity = preferences.bluetoothTrackpadSensitivity,
            remoteTrackpadScrollSensitivity = preferences.bluetoothTrackpadScrollSensitivity,
            remoteTrackpadInvertScrollEnabled = preferences.bluetoothTrackpadInvertScrollEnabled,
            remoteTrackpadTapToClickEnabled = preferences.bluetoothTrackpadTapToClickEnabled,
            remoteTrackpadDedicatedButtonsEnabled = preferences.bluetoothTrackpadDedicatedButtonsEnabled,
            remoteTrackpadButtonHeightPercent = preferences.bluetoothTrackpadButtonHeightPercent,
            remoteTrackpadDoubleTapTimeoutMs = preferences.bluetoothTrackpadDoubleTapTimeoutMs,
        )
        hideSystemImeSwitcher()
    }

    private fun hideSystemImeSwitcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.captionBar())
        }
    }

    private fun handleGlide(path: List<String>, touchTrace: GlideTouchTrace?, heldModifiers: HeldModifiers) {
        if (!preferences.gestureTypingEnabled || !gestureTypingEngine.isEnabledFor(currentInputEditorInfo)) return
        val predictionContext = glidePredictionContext()
        val candidates = gestureTypingEngine.candidates(
            pathLabels = path,
            options = preferences.glideTypingOptions(),
            context = predictionContext,
            touchTrace = touchTrace,
            limit = GLIDE_SUGGESTION_LIMIT,
        )
        val word = candidates.firstOrNull()?.word
        if (word == null) {
            persistGlideDebugSnapshot(
                path = path,
                touchTrace = touchTrace,
                candidates = candidates,
                committedWord = null,
                learningStatus = "not recorded: no candidate",
            )
            return
        }
        if (isBluetoothRemoteActive()) {
            val outputWord = formatGlideWord(word, heldModifiers)
            val action = KeyAction.text("$outputWord ")
            val stateBefore = keyboardState
            keyboardState = remoteHidKeyRouter?.handle(action, keyboardState, HeldModifiers()) ?: keyboardState
            appendRemoteTextContext(RemoteHidKeyMapper.outputText(action.text.orEmpty(), stateBefore, HeldModifiers()))
            val learningStatus = recordAcceptedGlideWord(word, predictionContext)
            persistGlideDebugSnapshot(
                path = path,
                touchTrace = touchTrace,
                candidates = candidates,
                committedWord = word,
                learningStatus = learningStatus,
            )
            return
        }
        if (pendingGlideCorrection?.buffer?.isNotEmpty() == true) {
            finalizePendingGlideCorrection()
        } else {
            recordPendingGlideCorrection(word)
        }
        val outputWord = formatGlideWord(word, heldModifiers)
        val committedText = formatGlideCommitText(outputWord)
        currentInputConnection?.commitText(committedText, 1)
        val learningStatus = recordAcceptedGlideWord(word, predictionContext)
        persistGlideDebugSnapshot(
            path = path,
            touchTrace = touchTrace,
            candidates = candidates,
            committedWord = word,
            learningStatus = learningStatus,
        )
        scheduleGlideSuggestions(candidates.drop(1).map { candidate -> candidate.word })
        pendingGlideUndo = PendingGlideUndo(
            path = path,
            word = word,
            committedText = committedText,
            predictionContext = predictionContext,
        )
        pendingSuggestionCommit = null
        keyboardState = keyboardState.clearTransientModifiers()
    }

    private fun formatGlideWord(word: String, heldModifiers: HeldModifiers): String {
        return word.applyKeyboardCapitalization(
            modifiers = keyboardState.modifiers,
            heldModifiers = heldModifiers,
            autoCapAfterPeriod = preferences.autoCapAfterPeriodEnabled,
            textBeforeCursor = currentInputConnection?.getTextBeforeCursor(AUTO_CAP_CONTEXT_CHARS, 0),
            allCapsOnShift = false,
        )
    }

    private fun formatGlideCommitText(word: String): String {
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(GLIDE_CONTEXT_CHARS, 0)
        val leadingSpace = if (shouldInsertLeadingSpaceBeforeText(beforeCursor, word)) " " else ""
        return "$leadingSpace$word "
    }

    private fun formatReplacementWord(word: String, previousCommittedText: String): String {
        return if (previousCommittedText.firstOrNull { it.isLetter() }?.isUpperCase() == true) {
            word.replaceFirstChar { it.uppercase() }
        } else {
            word
        }
    }

    private fun handleKeyAction(action: KeyAction, heldModifiers: HeldModifiers) {
        if (handleBluetoothControlAction(action)) return
        if (isBluetoothRemoteActive() && shouldRouteToBluetoothRemote(action)) {
            pendingGlideUndo = null
            pendingSuggestionCommit = null
            clearPendingGlideCorrection()
            clearGlideSuggestions()
            val stateBefore = keyboardState
            keyboardState = remoteHidKeyRouter?.handle(action, keyboardState, heldModifiers) ?: keyboardState
            recordRemoteTextContext(action, stateBefore, heldModifiers)
            return
        }
        if (action.type == KeyActionType.TAB && tryAcceptFirstSuggestionWithTab(heldModifiers)) return
        if (action.type == KeyActionType.DELETE && tryUndoLastGlide(heldModifiers)) return
        val actionToHandle = action.withPostPredictionPunctuationSpacing(heldModifiers)
        val boundaryToken = typedBoundaryToken(actionToHandle, heldModifiers)
        val boundaryContext = boundaryToken?.let(::typedPredictionContextBeforeToken)
        val autocorrected = if (boundaryToken != null && boundaryContext != null) {
            autocorrectTypedToken(boundaryToken, boundaryContext)
        } else {
            false
        }
        if (!autocorrected && boundaryToken != null && boundaryContext != null) {
            recordTypedAcceptedWord(boundaryToken, boundaryContext)
        }
        updatePendingGlideCorrection(actionToHandle, heldModifiers)
        if (actionToHandle.type != KeyActionType.DELETE) {
            pendingGlideUndo = null
            pendingSuggestionCommit = null
            clearGlideSuggestions()
        }
        when (actionToHandle.type) {
            KeyActionType.TOGGLE_SPEECH_INPUT -> {
                serviceScope.launch { preferenceRepository.setSpeechInputEnabled(!preferences.speechInputEnabled) }
            }
            KeyActionType.MICROPHONE -> {
                handleSpeechInput()
            }
            KeyActionType.TOGGLE_GESTURE_TYPING -> {
                serviceScope.launch { preferenceRepository.setGestureTypingEnabled(!preferences.gestureTypingEnabled) }
            }
            else -> {
                keyboardState = keyActionEngine.handle(
                    actionToHandle,
                    keyboardState.copy(
                        stickyModifiersEnabled = preferences.stickyModifiersEnabled,
                        shiftCapsLockEnabled = preferences.shiftCapsLockEnabled,
                    ),
                    heldModifiers,
                    autoCapAfterPeriod = preferences.autoCapAfterPeriodEnabled,
                )
            }
        }
        if (shouldRefreshTypedSuggestionsAfter(actionToHandle)) {
            refreshTypedSuggestions()
        } else if (suggestionMode == SuggestionMode.TYPED) {
            clearGlideSuggestions()
        }
    }

    private fun handleBluetoothControlAction(action: KeyAction): Boolean {
        when (action.type) {
            KeyActionType.TOGGLE_BLUETOOTH_REMOTE -> {
                val nextEnabled = !preferences.bluetoothRemoteEnabled
                if (nextEnabled) markBluetoothRemoteUserActivation()
                serviceScope.launch {
                    preferenceRepository.setBluetoothRemoteEnabled(nextEnabled)
                }
                showToast(if (preferences.bluetoothRemoteEnabled) "Bluetooth remote off" else "Bluetooth remote on")
                return true
            }
            KeyActionType.BLUETOOTH_DEVICE_NEXT -> {
                selectNextBluetoothDevice()
                return true
            }
            KeyActionType.BLUETOOTH_LOCAL_INPUT -> {
                serviceScope.launch {
                    preferenceRepository.setBluetoothRemoteEnabled(false)
                }
                showToast("Local Android input")
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
                serviceScope.launch {
                    preferenceRepository.setBluetoothTrackpadEnabled(!preferences.bluetoothTrackpadEnabled)
                }
                showToast(if (preferences.bluetoothTrackpadEnabled) "Trackpad off" else "Trackpad on")
                return true
            }
            else -> return false
        }
    }

    private fun selectNextBluetoothDevice() {
        val devices = configuredBluetoothSlotDevices()
        if (devices.isEmpty()) {
            showToast(
                if (BluetoothHidSupport.hasConnectPermission(this)) {
                    "Assign a BT hotkey slot in settings"
                } else {
                    "Grant Nearby devices in settings"
                },
            )
            return
        }
        val currentIndex = devices.indexOfFirst { device -> device.address == preferences.bluetoothActiveDeviceAddress }
        val next = devices[(currentIndex + 1).floorMod(devices.size)]
        markBluetoothRemoteUserActivation()
        serviceScope.launch {
            preferenceRepository.setBluetoothActiveDeviceAddress(next.address)
            preferenceRepository.setBluetoothRemoteEnabled(true)
        }
        bluetoothHidController?.selectDevice(next.address)
        showToast("Bluetooth host: ${next.name}")
    }

    private fun selectBluetoothDeviceSlot(slotIndex: Int) {
        val slotName = "BT${slotIndex + 1}"
        val address = preferences.bluetoothDeviceSlotAddress(slotIndex)
        if (address == null) {
            showToast("Assign $slotName in settings")
            return
        }
        val device = BluetoothHidSupport.bondedDevices(this).firstOrNull { it.address == address }
        if (device == null) {
            showToast(
                if (BluetoothHidSupport.hasConnectPermission(this)) {
                    "$slotName host is not paired"
                } else {
                    "Grant Nearby devices in settings"
                },
            )
            return
        }
        markBluetoothRemoteUserActivation()
        serviceScope.launch {
            preferenceRepository.setBluetoothActiveDeviceAddress(device.address)
            preferenceRepository.setBluetoothRemoteEnabled(true)
        }
        bluetoothHidController?.selectDevice(device.address)
        showToast("$slotName: ${device.name}")
    }

    private fun configuredBluetoothSlotDevices(): List<RemoteHidDevice> {
        val bondedByAddress = BluetoothHidSupport.bondedDevices(this).associateBy { device -> device.address }
        return preferences.configuredBluetoothSlotAddresses().mapNotNull { address -> bondedByAddress[address] }
    }

    private fun isBluetoothRemoteActive(): Boolean {
        return preferences.bluetoothRemoteEnabled && remoteHidState.connected
    }

    private fun markBluetoothRemoteUserActivation() {
        bluetoothRemoteLocalFocusIgnoreUntilMs = SystemClock.uptimeMillis() + BLUETOOTH_REMOTE_LOCAL_FOCUS_GRACE_MS
    }

    private fun switchBluetoothRemoteToLocalForAndroidFocus() {
        if (!preferences.bluetoothRemoteEnabled) return
        if (SystemClock.uptimeMillis() < bluetoothRemoteLocalFocusIgnoreUntilMs) return
        preferences = preferences.copy(bluetoothRemoteEnabled = false)
        bluetoothHidController?.setEnabled(false, preferences.bluetoothActiveDeviceAddress)
        serviceScope.launch {
            preferenceRepository.setBluetoothRemoteEnabled(false)
        }
        showToast("Local Android input")
        renderKeyboard()
    }

    private fun shouldRouteToBluetoothRemote(action: KeyAction): Boolean {
        return when (action.type) {
            KeyActionType.SETTINGS,
            KeyActionType.LANGUAGE_SWITCH,
            KeyActionType.MICROPHONE,
            KeyActionType.TOGGLE_SPEECH_INPUT,
            KeyActionType.TOGGLE_GESTURE_TYPING,
            KeyActionType.TOGGLE_BLUETOOTH_REMOTE,
            KeyActionType.BLUETOOTH_DEVICE_NEXT,
            KeyActionType.BLUETOOTH_LOCAL_INPUT,
            KeyActionType.BLUETOOTH_DEVICE_1,
            KeyActionType.BLUETOOTH_DEVICE_2,
            KeyActionType.BLUETOOTH_DEVICE_3,
            KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD -> false
            else -> true
        }
    }

    private fun handleSuggestion(word: String) {
        when (suggestionMode) {
            SuggestionMode.GLIDE -> handleGlideSuggestion(word)
            SuggestionMode.TYPED -> handleTypedSuggestion(word)
            SuggestionMode.NONE -> Unit
        }
    }

    private fun handleGlideSuggestion(word: String) {
        val undo = pendingGlideUndo ?: return
        val normalizedWord = normalizeWord(word) ?: return
        val pathSignature = gestureTypingEngine.pathSignature(undo.path) ?: return
        val predictionContext = glidePredictionContextBeforePending(undo.committedText)
        val replacementWord = formatReplacementWord(normalizedWord, undo.committedText)
        val committedText = formatReplacementCommitText(replacementWord, undo.committedText)
        if (!replacePendingGlideCommit(undo.committedText, committedText)) return
        rejectAcceptedGlideWord(undo.word, predictionContext, persist = false)
        recordAcceptedGlideWord(normalizedWord, predictionContext)
        recordGlideCorrection(
            pathSignature = pathSignature,
            replacementWord = normalizedWord,
            rejectedWord = undo.word,
        )
        pendingGlideUndo = undo.copy(
            word = normalizedWord,
            committedText = committedText,
        )
        pendingSuggestionCommit = null
        clearGlideSuggestions()
    }

    private fun formatReplacementCommitText(word: String, previousCommittedText: String): String {
        val leadingSpace = previousCommittedText.takeWhile { it.isWhitespace() }
        val trailingSpace = if (previousCommittedText.endsWith(" ")) " " else ""
        return "$leadingSpace$word$trailingSpace"
    }

    private fun handleTypedSuggestion(word: String) {
        if (!typedSuggestionsAllowed()) return
        val normalizedWord = normalizeWord(word) ?: return
        val token = currentTypedTokenBeforeCursor() ?: return
        val context = typedPredictionContextBeforeToken(token)
        val replacement = "${formatTypedReplacement(normalizedWord, token)} "
        if (!replaceTypedToken(token, replacement)) return
        typedPredictionEngine.recordAcceptedWord(normalizedWord, context)
        persistGlideUserLanguageModel()
        pendingGlideUndo = null
        pendingSuggestionCommit = PendingSuggestionCommit(committedText = replacement)
        clearGlideSuggestions()
    }

    private fun replacePendingGlideCommit(pendingCommittedText: String, replacementText: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        inputConnection.beginBatchEdit()
        return try {
            inputConnection.finishComposingText()
            val extractedText = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
            if (extractedText != null) {
                val span = findPendingGlideReplacementSpan(
                    text = extractedText.text,
                    selectionStart = extractedText.selectionStart,
                    selectionEnd = extractedText.selectionEnd,
                    pendingCommittedText = pendingCommittedText,
                )
                if (span != null) {
                    val absoluteStart = extractedText.startOffset + span.start
                    val absoluteEnd = extractedText.startOffset + span.end
                    if (absoluteStart >= 0 && absoluteEnd >= absoluteStart) {
                        if (inputConnection.setSelection(absoluteStart, absoluteEnd) &&
                            inputConnection.commitText(replacementText, 1)
                        ) {
                            return true
                        }
                        inputConnection.setSelection(absoluteEnd, absoluteEnd)
                    }
                }
            }

            if (isTermuxInput()) {
                return replaceTerminalGlideCommit(pendingCommittedText, replacementText)
            }
            val textBeforeCursor = inputConnection.getTextBeforeCursor(pendingCommittedText.length, 0)
            pendingGlideCommitMatchesBeforeCursor(textBeforeCursor, pendingCommittedText) &&
                inputConnection.deleteSurroundingText(pendingCommittedText.length, 0) &&
                inputConnection.commitText(replacementText, 1)
        } finally {
            inputConnection.endBatchEdit()
        }
    }

    private fun replaceTerminalGlideCommit(pendingCommittedText: String, replacementText: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (!isTermuxInput() || pendingCommittedText.isEmpty()) return false
        sendBackspaces(inputConnection, pendingCommittedText.length)
        return inputConnection.commitText(replacementText, 1)
    }

    private fun deletePendingGlideCommit(pendingCommittedText: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (isTermuxInput()) {
            sendBackspaces(inputConnection, pendingCommittedText.length)
            return true
        }
        return inputConnection.deleteSurroundingText(pendingCommittedText.length, 0)
    }

    private fun tryAcceptFirstSuggestionWithTab(heldModifiers: HeldModifiers): Boolean {
        val word = glideSuggestions.firstOrNull() ?: return false
        if (suggestionMode == SuggestionMode.NONE) return false
        if (heldModifiers.isActive()) return false
        if (keyboardState.modifiers.shift || keyboardState.modifiers.shiftLocked || keyboardState.modifiers.ctrl || keyboardState.modifiers.alt || keyboardState.modifiers.fn) {
            return false
        }
        handleSuggestion(word)
        return true
    }

    private fun KeyAction.withPostPredictionPunctuationSpacing(heldModifiers: HeldModifiers): KeyAction {
        val punctuation = punctuationTextOrNull(this, heldModifiers) ?: return this
        val trimmedPendingSpace = trimPendingSpaceBeforePunctuation(this, heldModifiers)
        return postPredictionPunctuationAction(this, punctuation, trimmedPendingSpace)
    }

    private fun trimPendingSpaceBeforePunctuation(action: KeyAction, heldModifiers: HeldModifiers): Boolean {
        if (punctuationTextOrNull(action, heldModifiers) == null) return false
        val undo = pendingGlideUndo
        return (undo != null && trimPendingGlideSpace(undo)) || trimPendingSuggestionSpace()
    }

    private fun trimPendingGlideSpace(undo: PendingGlideUndo): Boolean {
        if (!undo.committedText.endsWith(" ")) return false
        val trimmedCommit = undo.committedText.dropLast(1)
        if (!replacePendingGlideCommit(undo.committedText, trimmedCommit)) return false
        pendingGlideUndo = undo.copy(committedText = trimmedCommit)
        return true
    }

    private fun trimPendingSuggestionSpace(): Boolean {
        val pending = pendingSuggestionCommit ?: return false
        if (!pending.committedText.endsWith(" ")) return false
        val trimmedCommit = pending.committedText.dropLast(1)
        if (!replacePendingGlideCommit(pending.committedText, trimmedCommit)) return false
        pendingSuggestionCommit = pending.copy(committedText = trimmedCommit)
        return true
    }

    private fun typedBoundaryToken(action: KeyAction, heldModifiers: HeldModifiers): String? {
        if (!typedLearningAllowed() || !isTypedBoundaryAction(action, heldModifiers)) return null
        return currentTypedTokenBeforeCursor()?.takeIf { token -> normalizeWord(token) != null }
    }

    private fun isTypedBoundaryAction(action: KeyAction, heldModifiers: HeldModifiers): Boolean {
        if (hasTextMeta(heldModifiers)) return false
        return when (action.type) {
            KeyActionType.SPACE,
            KeyActionType.ENTER,
            KeyActionType.TAB -> true
            KeyActionType.COMMIT_TEXT -> punctuationTextOrNull(action, heldModifiers) != null
            else -> false
        }
    }

    private fun shouldRefreshTypedSuggestionsAfter(action: KeyAction): Boolean {
        return when (action.type) {
            KeyActionType.COMMIT_TEXT,
            KeyActionType.DELETE,
            KeyActionType.SPACE,
            KeyActionType.ENTER,
            KeyActionType.TAB -> true
            else -> false
        }
    }

    private fun punctuationTextOrNull(action: KeyAction, heldModifiers: HeldModifiers): String? {
        if (action.type != KeyActionType.COMMIT_TEXT || heldModifiers.ctrl || heldModifiers.alt || keyboardState.modifiers.ctrl || keyboardState.modifiers.alt) {
            return null
        }
        return action.text?.takeIf { text -> text in PUNCTUATION_THAT_TRIMS_GLIDE_SPACE }
    }

    private fun autocorrectTypedToken(token: String, context: GlidePredictionContext): Boolean {
        if (!preferences.typedAutocorrectEnabled || !typedPredictionAllowed()) return false
        val correction = typedPredictionEngine.autocorrect(token, context) ?: return false
        val replacement = formatTypedReplacement(correction, token)
        if (!replaceTypedToken(token, replacement)) return false
        typedPredictionEngine.recordAcceptedWord(correction, context)
        persistGlideUserLanguageModel()
        return true
    }

    private fun recordTypedAcceptedWord(token: String, context: GlidePredictionContext) {
        if (!typedLearningAllowed()) return
        val word = normalizeWord(token) ?: return
        typedPredictionEngine.recordAcceptedWord(word, context)
        persistGlideUserLanguageModel()
    }

    private fun refreshTypedSuggestions() {
        if (pendingGlideUndo != null && suggestionMode == SuggestionMode.GLIDE) return
        if (!typedSuggestionsAllowed()) {
            if (suggestionMode == SuggestionMode.TYPED) clearGlideSuggestions()
            return
        }
        val token = currentTypedTokenBeforeCursor()
        if (token == null) {
            if (suggestionMode == SuggestionMode.TYPED) clearGlideSuggestions()
            return
        }
        val suggestions = typedPredictionEngine.suggestions(
            token = token,
            context = typedPredictionContextBeforeToken(token),
            limit = GLIDE_SUGGESTION_LIMIT,
        )
        if (suggestions.isEmpty()) {
            if (suggestionMode == SuggestionMode.TYPED) clearGlideSuggestions()
            return
        }
        suggestionMode = SuggestionMode.TYPED
        glideSuggestions = suggestions
    }

    private fun replaceTypedToken(token: String, replacementText: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (token.isEmpty()) return false
        inputConnection.beginBatchEdit()
        return try {
            inputConnection.finishComposingText()
            inputConnection.deleteSurroundingText(token.length, 0) &&
                inputConnection.commitText(replacementText, 1)
        } finally {
            inputConnection.endBatchEdit()
        }
    }

    private fun currentTypedTokenBeforeCursor(): String? {
        val text = currentInputConnection?.getTextBeforeCursor(TYPED_CONTEXT_CHARS, 0)?.toString() ?: return null
        return typedTokenBeforeCursorText(text, MIN_TYPED_SUGGESTION_LENGTH)
    }

    private fun typedPredictionContextBeforeToken(token: String): GlidePredictionContext {
        val textBeforeCursor = currentInputConnection
            ?.getTextBeforeCursor(TYPED_CONTEXT_CHARS + token.length, 0)
            ?.toString()
        val contextText = if (textBeforeCursor?.endsWith(token) == true) {
            textBeforeCursor.dropLast(token.length)
        } else {
            textBeforeCursor
        }
        return GlidePredictionContext(textBeforeCursor = contextText)
    }

    private fun formatTypedReplacement(word: String, token: String): String {
        return when {
            token.all { char -> char.isUpperCase() } -> word.uppercase()
            token.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
    }

    private fun sendBackspaces(inputConnection: InputConnection, count: Int) {
        repeat(count) {
            sendBackspaceKey(inputConnection)
        }
    }

    private fun sendBackspaceKey(inputConnection: InputConnection) {
        val eventTime = System.currentTimeMillis()
        inputConnection.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0),
        )
        inputConnection.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0),
        )
    }

    private fun isTermuxInput(): Boolean {
        return currentInputEditorInfo?.packageName?.startsWith(TERMUX_PACKAGE_PREFIX) == true
    }

    private fun typedSuggestionsAllowed(): Boolean {
        return preferences.typedSuggestionsEnabled && typedPredictionAllowed()
    }

    private fun typedLearningAllowed(): Boolean {
        return (preferences.typedSuggestionsEnabled || preferences.typedAutocorrectEnabled) && typedPredictionAllowed()
    }

    private fun typedPredictionAllowed(): Boolean {
        return !isTermuxInput() && textContextPolicy.allowsLearning(currentInputEditorInfo)
    }

    private fun glidePredictionContext(): GlidePredictionContext {
        return GlidePredictionContext(
            textBeforeCursor = currentInputConnection?.getTextBeforeCursor(GLIDE_CONTEXT_CHARS, 0),
        )
    }

    private fun glidePredictionContextBeforePending(pendingCommittedText: String): GlidePredictionContext {
        val textBeforeCursor = currentInputConnection
            ?.getTextBeforeCursor(GLIDE_CONTEXT_CHARS + pendingCommittedText.length, 0)
            ?.toString()
        val contextText = if (textBeforeCursor?.endsWith(pendingCommittedText) == true) {
            textBeforeCursor.dropLast(pendingCommittedText.length)
        } else {
            textBeforeCursor
        }
        return GlidePredictionContext(textBeforeCursor = contextText)
    }

    private fun featureActiveKeyIds(layoutState: KeyboardState, layout: KeyboardLayout): Set<String> = buildSet {
        if (speechUiState == SpeechUiState.LISTENING || speechUiState == SpeechUiState.PROCESSING) {
            addAll(layout.keyIdsForAction(KeyActionType.MICROPHONE))
        }
        if (preferences.gestureTypingEnabled) addAll(layout.keyIdsForAction(KeyActionType.SETTINGS))
        if (preferences.bluetoothRemoteEnabled) addAll(layout.keyIdsForAction(KeyActionType.TOGGLE_BLUETOOTH_REMOTE))
        addAll(activeBluetoothTargetKeyIds(layout))
        if (isBluetoothRemoteActive() && preferences.bluetoothTrackpadEnabled) {
            addAll(layout.keyIdsForAction(KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD))
        }
        if (
            layoutState.activeLayoutId in QUICK_NAV_STICKY_INDICATOR_LAYOUTS &&
            (keyboardState.modifiers.ctrl || keyboardState.modifiers.alt || keyboardState.modifiers.fn)
        ) {
            add("num_toggle")
        }
    }

    private fun activeBluetoothTargetKeyIds(layout: KeyboardLayout): Set<String> {
        val slotAddresses = (0 until BLUETOOTH_DEVICE_SLOT_COUNT).map { index ->
            preferences.bluetoothDeviceSlotAddress(index)
        }
        if (!preferences.bluetoothRemoteEnabled) {
            return if (slotAddresses.any { it != null }) {
                layout.keyIdsForAction(KeyActionType.BLUETOOTH_LOCAL_INPUT)
            } else {
                emptySet()
            }
        }
        return when (slotAddresses.indexOf(preferences.bluetoothActiveDeviceAddress)) {
            0 -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_1)
            1 -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_2)
            2 -> layout.keyIdsForAction(KeyActionType.BLUETOOTH_DEVICE_3)
            else -> emptySet()
        }
    }

    private fun activeQuickModifierIds(): Set<String> = buildSet {
        if (keyboardState.modifiers.ctrl) add("ctrl")
        if (keyboardState.modifiers.alt) add("alt")
        if (keyboardState.modifiers.fn) add("quick_fn")
    }

    private fun handleSpeechInput() {
        if (!preferences.speechInputEnabled) {
            showToast("Enable mic input in settings")
            return
        }
        when (speechUiState) {
            SpeechUiState.LISTENING -> {
                if (speechInputEngine.stopListening()) {
                    updateSpeechUiState(SpeechUiState.PROCESSING)
                    showToast("Finishing speech")
                }
                return
            }
            SpeechUiState.PROCESSING -> {
                showToast("Processing speech")
                return
            }
            SpeechUiState.IDLE,
            SpeechUiState.COMPLETE,
            SpeechUiState.ERROR -> Unit
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
            if (speechUiState == SpeechUiState.LISTENING || speechUiState == SpeechUiState.PROCESSING) return
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
            updateSpeechUiState(SpeechUiState.IDLE)
            return
        }
        if (speechUiState == SpeechUiState.LISTENING && speechInputEngine.stopListening()) {
            speechPushToTalkStopPending = true
            updateSpeechUiState(SpeechUiState.PROCESSING)
            showToast("Finishing speech")
        } else if (speechUiState == SpeechUiState.PROCESSING) {
            speechPushToTalkStopPending = true
        }
    }

    private fun startSpeechInput(startMessage: String) {
        updateSpeechUiState(SpeechUiState.LISTENING)
        when (
            val result = speechInputEngine.start(
                editorInfo = currentInputEditorInfo,
                options = preferences.speechInputOptions(),
                onText = { text ->
                    commitSpeechText(text)
                    speechPushToTalkActive = false
                    speechPushToTalkStopPending = false
                    updateSpeechUiState(SpeechUiState.COMPLETE, resetAfter = true)
                    showToast("Speech inserted")
                },
                onError = { message ->
                    speechPushToTalkActive = false
                    if (speechPushToTalkStopPending && message.isBenignPushToTalkStopError()) {
                        speechPushToTalkStopPending = false
                        updateSpeechUiState(SpeechUiState.IDLE)
                        showToast("No speech captured")
                        return@start
                    }
                    speechPushToTalkStopPending = false
                    updateSpeechUiState(SpeechUiState.ERROR, resetAfter = true)
                    showToast(message)
                },
                onState = { state ->
                    when (state) {
                        SpeechInputEngineState.LISTENING -> updateSpeechUiState(SpeechUiState.LISTENING)
                        SpeechInputEngineState.PROCESSING -> updateSpeechUiState(SpeechUiState.PROCESSING)
                    }
                },
            )
        ) {
            SpeechStartResult.Started -> showToast(startMessage)
            SpeechStartResult.FeatureNotInstalled -> {
                speechPushToTalkActive = false
                speechPushToTalkStopPending = false
                updateSpeechUiState(SpeechUiState.ERROR, resetAfter = true)
                showToast("No speech recognizer available")
            }
            is SpeechStartResult.Error -> {
                speechPushToTalkActive = false
                speechPushToTalkStopPending = false
                updateSpeechUiState(SpeechUiState.ERROR, resetAfter = true)
                showToast(result.message)
            }
        }
    }

    private fun commitSpeechText(text: String) {
        val beforeCursor = if (isBluetoothRemoteActive()) {
            remoteTextBeforeCursor()
        } else {
            currentInputConnection?.getTextBeforeCursor(SPEECH_CONTEXT_CHARS, 0)
        }
        val insertion = formatSpeechInsertion(
            recognizedText = text,
            textBeforeCursor = beforeCursor,
            options = preferences.speechTextAutomationOptions(),
        )
        if (insertion.text.isNotEmpty()) {
            if (isBluetoothRemoteActive()) {
                commitSpeechTextToBluetoothRemote(insertion)
                return
            }
            val inputConnection = currentInputConnection ?: return
            if (insertion.deleteBeforeChars > 0) {
                inputConnection.deleteSurroundingText(insertion.deleteBeforeChars, 0)
            }
            inputConnection.commitText(insertion.text, 1)
        }
    }

    private fun commitSpeechTextToBluetoothRemote(insertion: SpeechInsertion) {
        val controller = bluetoothHidController ?: return
        val backspace = RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_DEL, KeyboardState(), HeldModifiers())
        val chords = RemoteHidKeyMapper.textChords(insertion.text, KeyboardState(), HeldModifiers())
        remoteSpeechSendJob?.cancel()
        remoteSpeechSendJob = serviceScope.launch {
            controller.sendKeyboardReport(0, emptyList())
            delay(BLUETOOTH_SPEECH_CHORD_GAP_MS)
            repeat(insertion.deleteBeforeChars) {
                if (backspace != null) {
                    sendBluetoothChordPaced(controller, backspace.modifiers, backspace.usage)
                    deleteRemoteTextContext(1)
                }
            }
            chords.forEach { chord ->
                sendBluetoothChordPaced(controller, chord.modifiers, chord.usage)
            }
            controller.sendKeyboardReport(0, emptyList())
            appendRemoteTextContext(insertion.text)
        }
    }

    private suspend fun sendBluetoothChordPaced(
        controller: BluetoothHidController,
        modifier: Int,
        usage: Int,
    ) {
        controller.sendKeyboardReport(0, emptyList())
        delay(BLUETOOTH_SPEECH_KEY_UP_GUARD_MS)
        controller.sendKeyboardReport(modifier, listOf(usage))
        delay(BLUETOOTH_SPEECH_KEY_DOWN_MS)
        controller.sendKeyboardReport(0, emptyList())
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
            preferences.speechPushToTalkEnabled && speechUiState == SpeechUiState.IDLE -> "Hold"
            speechUiState == SpeechUiState.LISTENING -> "Stop"
            speechUiState == SpeechUiState.PROCESSING -> "..."
            speechUiState == SpeechUiState.COMPLETE -> "Done"
            speechUiState == SpeechUiState.ERROR -> "Error"
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

    private fun KeyboardLayout.withBluetoothUiState(): KeyboardLayout {
        val bondedByAddress = BluetoothHidSupport.bondedDevices(this@ModernKeyboardImeService)
            .associateBy { device -> device.address }
        val remoteLabel = when {
            !preferences.bluetoothRemoteEnabled -> "Off"
            remoteHidState.status == RemoteHidStatus.PERMISSION_MISSING -> "Perm"
            remoteHidState.status == RemoteHidStatus.NO_DEVICE_SELECTED -> "Pick"
            remoteHidState.status == RemoteHidStatus.CONNECTED -> "On"
            remoteHidState.status == RemoteHidStatus.CONNECTING ||
                remoteHidState.status == RemoteHidStatus.REGISTERING ||
                remoteHidState.status == RemoteHidStatus.PROFILE_CONNECTING -> "..."
            remoteHidState.status == RemoteHidStatus.API_TOO_OLD -> "API"
            remoteHidState.status == RemoteHidStatus.BLUETOOTH_OFF -> "Off"
            remoteHidState.status == RemoteHidStatus.BLUETOOTH_UNAVAILABLE -> "No"
            remoteHidState.status == RemoteHidStatus.ERROR -> "Err"
            else -> "Ready"
        }
        val trackpadLabel = if (preferences.bluetoothTrackpadEnabled) "Pad" else "NoPad"
        val deviceLabel = remoteHidState.activeDeviceName?.shortBluetoothDeviceLabel() ?: "Next"
        return copy(
            rows = rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        when (key.action.type) {
                            KeyActionType.TOGGLE_BLUETOOTH_REMOTE -> key.copy(
                                secondaryLabel = remoteLabel,
                                secondaryIcon = null,
                            )
                            KeyActionType.BLUETOOTH_DEVICE_NEXT -> key.copy(
                                secondaryLabel = deviceLabel,
                                secondaryIcon = null,
                            )
                            KeyActionType.BLUETOOTH_LOCAL_INPUT -> key.copy(
                                label = "Local",
                                secondaryLabel = if (preferences.bluetoothRemoteEnabled) "Here" else "On",
                                secondaryIcon = null,
                            )
                            KeyActionType.BLUETOOTH_DEVICE_1 -> bluetoothDeviceSlotKey(key, 0, bondedByAddress)
                            KeyActionType.BLUETOOTH_DEVICE_2 -> bluetoothDeviceSlotKey(key, 1, bondedByAddress)
                            KeyActionType.BLUETOOTH_DEVICE_3 -> bluetoothDeviceSlotKey(key, 2, bondedByAddress)
                            KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD -> key.copy(
                                secondaryLabel = trackpadLabel,
                                secondaryIcon = null,
                            )
                            else -> key
                        }
                    },
                )
            },
        )
    }

    private fun bluetoothDeviceSlotKey(
        key: KeySpec,
        slotIndex: Int,
        bondedByAddress: Map<String, RemoteHidDevice>,
    ): KeySpec {
        val address = preferences.bluetoothDeviceSlotAddress(slotIndex)
        val device = address?.let { bondedByAddress[it] }
        return key.copy(
            label = "BT${slotIndex + 1}",
            secondaryLabel = when {
                device != null -> device.name.shortBluetoothDeviceLabel()
                address != null -> "Missing"
                else -> "Pair"
            },
            secondaryIcon = null,
        )
    }

    private fun String.shortBluetoothDeviceLabel(): String {
        val cleaned = trim()
        return if (cleaned.length <= BLUETOOTH_KEY_SECONDARY_LABEL_MAX_CHARS) {
            cleaned
        } else {
            cleaned.take(BLUETOOTH_KEY_SECONDARY_LABEL_MAX_CHARS)
        }
    }

    private fun updateSpeechUiState(nextState: SpeechUiState, resetAfter: Boolean = false) {
        speechResetJob?.cancel()
        speechUiState = nextState
        renderKeyboard()
        if (resetAfter) {
            speechResetJob = serviceScope.launch {
                delay(SPEECH_STATUS_RESET_MS)
                if (speechUiState == nextState) {
                    speechUiState = SpeechUiState.IDLE
                    renderKeyboard()
                }
            }
        }
    }

    private fun tryUndoLastGlide(heldModifiers: HeldModifiers): Boolean {
        val undo = pendingGlideUndo ?: return false
        val modifiers = keyboardState.modifiers
        if (heldModifiers.isActive() || modifiers.shift || modifiers.shiftLocked || modifiers.ctrl || modifiers.alt) return false
        deletePendingGlideCommit(undo.committedText)
        gestureTypingEngine.rejectCandidate(undo.path, undo.word)
        rejectAcceptedGlideWord(undo.word, undo.predictionContext)
        val pathSignature = gestureTypingEngine.pathSignature(undo.path)
        if (pathSignature != null) {
            serviceScope.launch {
                preferenceRepository.rejectGlideCorrection(pathSignature, undo.word)
            }
        }
        pendingGlideCorrection = if (canLearnFromCurrentInput() && preferences.glideCorrectionLearningEnabled) {
            pathSignature?.let {
                PendingGlideCorrection(pathSignature = pathSignature, rejectedWord = undo.word)
            }
        } else {
            null
        }
        pendingGlideUndo = null
        clearGlideSuggestions()
        keyboardState = keyboardState.clearTransientModifiers()
        glideCorrectionRecordJob?.cancel()
        glideCorrectionRecordJob = null
        return true
    }

    private fun scheduleGlideSuggestions(words: List<String>) {
        glideSuggestionsJob?.cancel()
        glideSuggestionsJob = null
        glideSuggestions = emptyList()
        suggestionMode = SuggestionMode.NONE
        val suggestions = words.distinct().take(GLIDE_SUGGESTION_LIMIT)
        if (suggestions.isEmpty()) return
        glideSuggestionsJob = serviceScope.launch {
            delay(GLIDE_SUGGESTION_DELAY_MS)
            if (pendingGlideUndo != null) {
                suggestionMode = SuggestionMode.GLIDE
                glideSuggestions = suggestions
                renderKeyboard()
            }
        }
    }

    private fun clearGlideSuggestions() {
        glideSuggestionsJob?.cancel()
        glideSuggestionsJob = null
        glideSuggestions = emptyList()
        suggestionMode = SuggestionMode.NONE
    }

    private fun updatePendingGlideCorrection(action: KeyAction, heldModifiers: HeldModifiers) {
        val correction = pendingGlideCorrection ?: return
        when (action.type) {
            KeyActionType.SHIFT -> return
            KeyActionType.COMMIT_TEXT -> {
                val letters = action.replacementLettersOrNull(heldModifiers)
                if (letters != null) {
                    val nextCorrection = correction.copy(buffer = correction.buffer + letters)
                    pendingGlideCorrection = nextCorrection
                    schedulePendingGlideCorrectionRecord(nextCorrection)
                } else {
                    finalizePendingGlideCorrection()
                }
            }
            KeyActionType.DELETE -> {
                if (correction.buffer.isEmpty()) {
                    clearPendingGlideCorrection()
                } else {
                    val nextCorrection = correction.copy(buffer = correction.buffer.dropLast(1))
                    pendingGlideCorrection = nextCorrection
                    if (nextCorrection.buffer.isEmpty()) {
                        clearPendingGlideCorrection()
                    } else {
                        schedulePendingGlideCorrectionRecord(nextCorrection)
                    }
                }
            }
            KeyActionType.SPACE,
            KeyActionType.ENTER,
            KeyActionType.TAB -> finalizePendingGlideCorrection()
            else -> {
                if (correction.buffer.isEmpty()) {
                    clearPendingGlideCorrection()
                } else {
                    finalizePendingGlideCorrection()
                }
            }
        }
    }

    private fun KeyAction.replacementLettersOrNull(heldModifiers: HeldModifiers): String? {
        if (type != KeyActionType.COMMIT_TEXT || hasTextMeta(heldModifiers)) return null
        val letters = text.orEmpty().lowercase().replace('\u2019', '\'')
        return letters.takeIf { value ->
            value.isNotEmpty() && value.all { char -> char in 'a'..'z' || char == '\'' }
        }
    }

    private fun hasTextMeta(heldModifiers: HeldModifiers): Boolean {
        return heldModifiers.ctrl || heldModifiers.alt || keyboardState.modifiers.ctrl || keyboardState.modifiers.alt
    }

    private fun finalizePendingGlideCorrection() {
        val correction = pendingGlideCorrection ?: return
        clearPendingGlideCorrection()
        recordGlideCorrection(correction.pathSignature, correction.buffer, correction.rejectedWord)
    }

    private fun recordPendingGlideCorrection(word: String) {
        val correction = pendingGlideCorrection ?: return
        clearPendingGlideCorrection()
        recordGlideCorrection(correction.pathSignature, word, correction.rejectedWord)
    }

    private fun schedulePendingGlideCorrectionRecord(correction: PendingGlideCorrection) {
        glideCorrectionRecordJob?.cancel()
        if (correction.buffer.length < MIN_GLIDE_MANUAL_CORRECTION_LENGTH) {
            glideCorrectionRecordJob = null
            return
        }
        glideCorrectionRecordJob = serviceScope.launch {
            delay(GLIDE_MANUAL_CORRECTION_RECORD_DELAY_MS)
            if (pendingGlideCorrection == correction) {
                recordGlideCorrection(correction.pathSignature, correction.buffer, correction.rejectedWord)
            }
        }
    }

    private fun clearPendingGlideCorrection() {
        glideCorrectionRecordJob?.cancel()
        glideCorrectionRecordJob = null
        pendingGlideCorrection = null
    }

    private fun recordAcceptedGlideWord(word: String, context: GlidePredictionContext): String {
        if (!preferences.glidePredictiveRankingEnabled) return "skipped: predictive glide ranking disabled"
        val skipReason = glideLearningSkipReason()
        if (skipReason != null) return "skipped: $skipReason"
        gestureTypingEngine.recordAcceptedWord(word, context)
        persistGlideUserLanguageModel()
        return "recorded"
    }

    private fun rejectAcceptedGlideWord(
        word: String,
        context: GlidePredictionContext,
        persist: Boolean = true,
    ) {
        if (!preferences.glidePredictiveRankingEnabled || glideLearningSkipReason() != null) return
        gestureTypingEngine.rejectAcceptedWord(word, context)
        if (persist) persistGlideUserLanguageModel()
    }

    private fun persistGlideUserLanguageModel() {
        serviceScope.launch(Dispatchers.IO) {
            glideUserLanguageModel.saveTo(GlideUserLanguageModel.storageFile(filesDir))
        }
    }

    private fun persistGlideDebugSnapshot(
        path: List<String>,
        touchTrace: GlideTouchTrace?,
        candidates: List<GlideCandidate>,
        committedWord: String?,
        learningStatus: String,
    ) {
        val pathSignature = gestureTypingEngine.pathSignature(path).orEmpty()
        val correctionSkipReason = glideLearningSkipReason()
        val traceProfile = GlideGeometryScorer.profile(touchTrace)
        val correctionStatus = when {
            !preferences.glideCorrectionLearningEnabled -> "disabled"
            correctionSkipReason != null -> "skipped: $correctionSkipReason"
            else -> "enabled"
        }
        val snapshot = buildString {
            appendLine("Last glide")
            appendLine("timestamp=${System.currentTimeMillis()}")
            appendLine("package=${currentInputEditorInfo?.packageName.orEmpty()}")
            appendLine("path=${path.joinToString(separator = " ")}")
            appendLine("signature=$pathSignature")
            appendLine("touchTrace=${touchTrace?.points?.size ?: 0} points, usable=${touchTrace?.isUsable() == true}")
            appendLine("dwell=${traceProfile?.keyDwellWeights?.debugDwellWeights().orEmpty()}")
            appendLine("cornerKeys=${traceProfile?.cornerKeys?.debugDwellWeights().orEmpty()}")
            appendLine("corners=${traceProfile?.cornerPoints?.size ?: 0}")
            appendLine("committed=${committedWord ?: "<none>"}")
            appendLine("learning=$learningStatus")
            appendLine("correctionLearning=$correctionStatus")
            appendLine("strictFirstLast=${preferences.glideStrictFirstLastLetter}")
            appendLine("pathTolerance=${preferences.glidePathTolerance}")
            appendLine("spatialPrecision=${preferences.glideSpatialPrecision}")
            appendLine("dwellSensitivity=${preferences.glideDwellSensitivity}")
            appendLine("dwellActivationThreshold=${"%.2f".format(preferences.glideDwellActivationThreshold)}")
            appendLine("predictiveRanking=${preferences.glidePredictiveRankingEnabled}")
            appendLine("importedWordCount=${preferences.glideImportedWordCount}")
            appendLine("candidates=${candidates.size}")
            candidates.forEachIndexed { index, candidate ->
                appendLine(
                    "${index + 1}. ${candidate.word}\t${candidate.source}\tscore=${candidate.score}\tpriority=${candidate.priority}",
                )
            }
        }
        serviceScope.launch(Dispatchers.IO) {
            filesDir.resolve(GLIDE_DEBUG_SNAPSHOT_FILE).writeText(snapshot)
        }
    }

    private fun recordGlideCorrection(pathSignature: String, replacementWord: String, rejectedWord: String) {
        if (!preferences.glideCorrectionLearningEnabled || !canLearnFromCurrentInput()) return
        val normalizedWord = normalizeWord(replacementWord) ?: return
        val normalizedRejectedWord = normalizeWord(rejectedWord) ?: return
        if (normalizedWord == normalizedRejectedWord) return
        if (!replacementLooksRelated(pathSignature, normalizedWord)) return
        val nextCorrections = preferences.glideCorrections + (
            pathSignature to (
                preferences.glideCorrections[pathSignature]
                    ?.copy(word = normalizedWord)
                    ?.accepted(System.currentTimeMillis())
                    ?: GlideCorrectionEntry.fromWord(normalizedWord, System.currentTimeMillis())
                    ?: return
                )
            )
        gestureTypingEngine.setCorrections(nextCorrections)
        serviceScope.launch {
            preferenceRepository.recordGlideCorrection(pathSignature, normalizedWord)
        }
    }

    private fun replacementLooksRelated(pathSignature: String, word: String): Boolean {
        if (word.length !in MIN_GLIDE_MANUAL_CORRECTION_LENGTH..MAX_GLIDE_MANUAL_CORRECTION_LENGTH) return false
        val lengthDelta = kotlin.math.abs(pathSignature.length - word.length)
        val maximumDelta = maxOf(MAX_GLIDE_CORRECTION_LENGTH_DELTA, word.length)
        return lengthDelta <= maximumDelta
    }

    private fun canLearnFromCurrentInput(): Boolean {
        return glideLearningSkipReason() == null
    }

    private fun glideLearningSkipReason(): String? {
        return when {
            isTermuxInput() -> "Termux input"
            !textContextPolicy.allowsLearning(currentInputEditorInfo) -> "field blocks personalized learning"
            else -> null
        }
    }

    private fun Map<Char, Float>.debugDwellWeights(): String {
        return entries
            .sortedByDescending { (_, weight) -> weight }
            .take(8)
            .joinToString(separator = " ") { (key, weight) -> "$key=${"%.2f".format(weight)}" }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }

    private enum class SpeechUiState {
        IDLE,
        LISTENING,
        PROCESSING,
        COMPLETE,
        ERROR,
    }

    private enum class SuggestionMode {
        NONE,
        GLIDE,
        TYPED,
    }

    private data class PendingGlideUndo(
        val path: List<String>,
        val word: String,
        val committedText: String,
        val predictionContext: GlidePredictionContext,
    )

    private data class PendingSuggestionCommit(
        val committedText: String,
    )

    private data class PendingGlideCorrection(
        val pathSignature: String,
        val rejectedWord: String,
        val buffer: String = "",
    )

    private companion object {
        const val SPEECH_STATUS_RESET_MS = 1600L
        const val SPEECH_CONTEXT_CHARS = 80
        const val REMOTE_TEXT_CONTEXT_CHARS = 240
        const val MIN_PUSH_TO_TALK_HOLD_MS = 300L
        const val BLUETOOTH_SPEECH_KEY_UP_GUARD_MS = 6L
        const val BLUETOOTH_SPEECH_KEY_DOWN_MS = 12L
        const val BLUETOOTH_SPEECH_CHORD_GAP_MS = 18L
        const val AUTO_CAP_CONTEXT_CHARS = 8
        const val GLIDE_CONTEXT_CHARS = 160
        const val TYPED_CONTEXT_CHARS = 160
        const val GLIDE_SUGGESTION_LIMIT = 5
        const val GLIDE_SUGGESTION_DELAY_MS = 450L
        const val GLIDE_MANUAL_CORRECTION_RECORD_DELAY_MS = 700L
        const val MIN_GLIDE_MANUAL_CORRECTION_LENGTH = 2
        const val MAX_GLIDE_MANUAL_CORRECTION_LENGTH = 24
        const val MAX_GLIDE_CORRECTION_LENGTH_DELTA = 4
        const val MIN_TYPED_SUGGESTION_LENGTH = 2
        const val BLUETOOTH_KEY_SECONDARY_LABEL_MAX_CHARS = 6
        const val BLUETOOTH_DEVICE_SLOT_COUNT = 3
        const val BLUETOOTH_REMOTE_LOCAL_FOCUS_GRACE_MS = 800L
        const val GLIDE_DEBUG_SNAPSHOT_FILE = "glide_debug_snapshot.txt"
        const val TERMUX_PACKAGE_PREFIX = "com.termux"
        val PUNCTUATION_THAT_TRIMS_GLIDE_SPACE = setOf(".", ",", "!", "?", ";", ":")
        val PUSH_TO_TALK_BENIGN_STOP_ERRORS = setOf(
            "Speech recognizer client error",
            "No speech recognized",
            "No speech heard",
        )
        val QUICK_NAV_STICKY_INDICATOR_LAYOUTS = setOf("qwerty4", "compact5")
        val QUICK_MODIFIER_BAR_LAYOUTS = setOf("qwerty4", "compact5")
    }
}

internal fun postPredictionPunctuationAction(
    action: KeyAction,
    punctuation: String,
    trimmedPendingSpace: Boolean,
): KeyAction {
    return if (trimmedPendingSpace) KeyAction.text("$punctuation ") else action
}

private fun Char.isAsciiLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

private fun Char.isWordApostrophe(): Boolean {
    return this == '\'' || this == '\u2019'
}

internal fun typedTokenBeforeCursorText(text: String, minLength: Int = 2): String? {
    var index = text.length - 1
    if (index < 0 || !text[index].isAsciiLetter()) return null
    val end = index + 1
    while (index >= 0) {
        val char = text[index]
        when {
            char.isAsciiLetter() -> index--
            char.isWordApostrophe() &&
                index > 0 &&
                index + 1 < end &&
                text[index - 1].isAsciiLetter() &&
                text[index + 1].isAsciiLetter() -> index--
            else -> break
        }
    }
    val token = text.substring(index + 1, end)
    val normalized = normalizeWordPrefix(token) ?: return null
    return token.takeIf { normalized.length >= minLength }
}
