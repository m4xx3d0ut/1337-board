package org.leetboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
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
import org.leetboard.ime.engine.GlideDictionaryLoader
import org.leetboard.ime.engine.GestureTypingEngine
import org.leetboard.ime.engine.KeyActionEngine
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.SpeechInputEngineState
import org.leetboard.ime.engine.SpeechInputEngine
import org.leetboard.ime.engine.SpeechStartResult
import org.leetboard.ime.engine.TextContextPolicy
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.engine.applyKeyboardCapitalization
import org.leetboard.ime.engine.findPendingGlideReplacementSpan
import org.leetboard.ime.engine.normalizeWord
import org.leetboard.ime.engine.pendingGlideCommitMatchesBeforeCursor
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.activeKeyIds
import org.leetboard.ime.model.clearTransientModifiers
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.ui.KeyboardInputView

class ModernKeyboardImeService : InputMethodService() {
    private val layoutEngine = LayoutEngine()
    private val customizationEngine = CustomizationEngine()
    private val textContextPolicy = TextContextPolicy()
    private val themeEngine = ThemeEngine()

    private lateinit var gestureTypingEngine: GestureTypingEngine
    private lateinit var keyActionEngine: KeyActionEngine
    private lateinit var speechInputEngine: SpeechInputEngine
    private lateinit var preferenceRepository: PreferenceRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keyboardInputView: KeyboardInputView? = null
    private var keyboardState = KeyboardState()
    private var preferences = KeyboardPreferences.defaults()
    private var contextHiddenKeyIds: Set<String> = emptySet()
    private var speechUiState = SpeechUiState.IDLE
    private var speechResetJob: Job? = null
    private var glideSuggestionsJob: Job? = null
    private var glideCorrectionRecordJob: Job? = null
    private var pendingGlideUndo: PendingGlideUndo? = null
    private var pendingGlideCorrection: PendingGlideCorrection? = null
    private var glideSuggestions: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val glideDictionaryLoader = GlideDictionaryLoader(this)
        gestureTypingEngine = GestureTypingEngine(textContextPolicy, glideDictionaryLoader::loadWords)
        keyActionEngine = KeyActionEngine(this)
        speechInputEngine = SpeechInputEngine(this, textContextPolicy)
        preferenceRepository = PreferenceRepository(this)
        serviceScope.launch {
            preferenceRepository.preferences.collectLatest { nextPreferences ->
                gestureTypingEngine.setCorrections(
                    if (nextPreferences.glideCorrectionLearningEnabled) {
                        nextPreferences.glideCorrections
                    } else {
                        emptyMap()
                    },
                )
                if (!nextPreferences.glideCorrectionLearningEnabled) clearPendingGlideCorrection()
                preferences = nextPreferences
                if (!preferences.speechInputEnabled && speechUiState != SpeechUiState.IDLE) {
                    speechInputEngine.cancel()
                    speechUiState = SpeechUiState.IDLE
                }
                renderKeyboard()
            }
        }
    }

    override fun onDestroy() {
        speechResetJob?.cancel()
        glideSuggestionsJob?.cancel()
        glideCorrectionRecordJob?.cancel()
        speechInputEngine.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return KeyboardInputView(this).also { view ->
            keyboardInputView = view
            view.keyboardView.onKey = { action, heldModifiers ->
                handleKeyAction(action, heldModifiers)
                renderKeyboard()
            }
            view.keyboardView.onGlide = { path, heldModifiers ->
                handleGlide(path, heldModifiers)
                renderKeyboard()
            }
            view.onSuggestion = { word ->
                handleGlideSuggestion(word)
                renderKeyboard()
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
        val speechAllowed = speechInputEngine.isAvailable(attribute)
        contextHiddenKeyIds = buildSet {
            if (!speechAllowed) add("mic")
        }
        renderKeyboard()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        speechInputEngine.cancel()
        speechUiState = SpeechUiState.IDLE
        pendingGlideUndo = null
        clearPendingGlideCorrection()
        clearGlideSuggestions()
        keyboardState = KeyboardState()
        renderKeyboard()
    }

    private fun renderKeyboard() {
        val orientation = resources.configuration.orientation
        val layoutState = keyboardState.copy(
            activeLayoutId = preferences.layoutId,
            keyPreviewEnabled = preferences.keyPreviewEnabled,
            stickyModifiersEnabled = preferences.stickyModifiersEnabled,
            shiftCapsLockEnabled = preferences.shiftCapsLockEnabled,
            edgeKeyWidthScale = preferences.edgeKeyWidthScale,
        )
        val layout = customizationEngine.apply(
            layout = layoutEngine.layoutFor(layoutState, orientation),
            customization = preferences.customizationState().let { customization ->
                customization.copy(
                    hiddenOptionalKeyIds = customization.hiddenOptionalKeyIds + contextHiddenKeyIds,
                )
            },
        ).withSpeechUiState()
        keyboardInputView?.render(
            layout,
            themeEngine.resolve(
                preset = preferences.themePreset,
                portrait = preferences.portraitGeometry,
                landscape = preferences.landscapeGeometry,
            ),
            layoutState.activeKeyIds() + featureActiveKeyIds(),
            preferences.keyPreviewEnabled,
            preferences.stickyModifiersEnabled,
            preferences.keyLabelStyle,
            preferences.gestureTypingEnabled && gestureTypingEngine.isEnabledFor(currentInputEditorInfo),
            preferences.swipeUpActionsEnabled,
            glideSuggestions,
        )
        hideSystemImeSwitcher()
    }

    private fun hideSystemImeSwitcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.captionBar())
        }
    }

    private fun handleGlide(path: List<String>, heldModifiers: HeldModifiers) {
        if (!preferences.gestureTypingEnabled || !gestureTypingEngine.isEnabledFor(currentInputEditorInfo)) return
        val candidates = gestureTypingEngine.candidates(
            pathLabels = path,
            options = preferences.glideTypingOptions(),
            limit = GLIDE_SUGGESTION_LIMIT,
        )
        val word = candidates.firstOrNull()?.word ?: return
        if (pendingGlideCorrection?.buffer?.isNotEmpty() == true) {
            finalizePendingGlideCorrection()
        } else {
            recordPendingGlideCorrection(word)
        }
        val outputWord = formatGlideWord(word, heldModifiers)
        val committedText = "$outputWord "
        currentInputConnection?.commitText(committedText, 1)
        scheduleGlideSuggestions(candidates.drop(1).map { candidate -> candidate.word })
        pendingGlideUndo = PendingGlideUndo(
            path = path,
            word = word,
            committedText = committedText,
        )
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

    private fun formatReplacementWord(word: String, previousCommittedText: String): String {
        return if (previousCommittedText.firstOrNull()?.isUpperCase() == true) {
            word.replaceFirstChar { it.uppercase() }
        } else {
            word
        }
    }

    private fun handleKeyAction(action: KeyAction, heldModifiers: HeldModifiers) {
        if (action.type == KeyActionType.DELETE && tryUndoLastGlide(heldModifiers)) return
        updatePendingGlideCorrection(action, heldModifiers)
        if (action.type != KeyActionType.DELETE) {
            pendingGlideUndo = null
            clearGlideSuggestions()
        }
        when (action.type) {
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
                    action,
                    keyboardState.copy(
                        stickyModifiersEnabled = preferences.stickyModifiersEnabled,
                        shiftCapsLockEnabled = preferences.shiftCapsLockEnabled,
                    ),
                    heldModifiers,
                    autoCapAfterPeriod = preferences.autoCapAfterPeriodEnabled,
                )
            }
        }
    }

    private fun handleGlideSuggestion(word: String) {
        val undo = pendingGlideUndo ?: return
        val normalizedWord = normalizeWord(word) ?: return
        val pathSignature = gestureTypingEngine.pathSignature(undo.path) ?: return
        val replacementWord = formatReplacementWord(normalizedWord, undo.committedText)
        val committedText = "$replacementWord "
        if (!replacePendingGlideCommit(undo.committedText, committedText)) return
        recordGlideCorrection(
            pathSignature = pathSignature,
            replacementWord = normalizedWord,
            rejectedWord = undo.word,
        )
        pendingGlideUndo = undo.copy(
            word = normalizedWord,
            committedText = committedText,
        )
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

    private fun featureActiveKeyIds(): Set<String> = buildSet {
        if (speechUiState == SpeechUiState.LISTENING || speechUiState == SpeechUiState.PROCESSING) add("mic")
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
        updateSpeechUiState(SpeechUiState.LISTENING)
        when (
            val result = speechInputEngine.start(
                editorInfo = currentInputEditorInfo,
                onText = { text ->
                    currentInputConnection?.commitText(text, 1)
                    updateSpeechUiState(SpeechUiState.COMPLETE, resetAfter = true)
                    showToast("Speech inserted")
                },
                onError = { message ->
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
            SpeechStartResult.Started -> showToast("Listening")
            SpeechStartResult.FeatureNotInstalled -> {
                updateSpeechUiState(SpeechUiState.ERROR, resetAfter = true)
                showToast("No speech recognizer available")
            }
            is SpeechStartResult.Error -> {
                updateSpeechUiState(SpeechUiState.ERROR, resetAfter = true)
                showToast(result.message)
            }
        }
    }

    private fun KeyboardLayout.withSpeechUiState(): KeyboardLayout {
        val secondaryLabel = when {
            !preferences.speechInputEnabled -> "Off"
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
                        if (key.id == "mic") key.copy(secondaryLabel = secondaryLabel) else key
                    },
                )
            },
        )
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
        pendingGlideCorrection = if (preferences.glideCorrectionLearningEnabled) {
            gestureTypingEngine.pathSignature(undo.path)?.let { pathSignature ->
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
        val suggestions = words.distinct().take(GLIDE_SUGGESTION_LIMIT)
        if (suggestions.isEmpty()) return
        glideSuggestionsJob = serviceScope.launch {
            delay(GLIDE_SUGGESTION_DELAY_MS)
            if (pendingGlideUndo != null) {
                glideSuggestions = suggestions
                renderKeyboard()
            }
        }
    }

    private fun clearGlideSuggestions() {
        glideSuggestionsJob?.cancel()
        glideSuggestionsJob = null
        glideSuggestions = emptyList()
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
        val letters = text.orEmpty().lowercase()
        return letters.takeIf { value ->
            value.isNotEmpty() && value.all { char -> char in 'a'..'z' }
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

    private fun recordGlideCorrection(pathSignature: String, replacementWord: String, rejectedWord: String) {
        if (!preferences.glideCorrectionLearningEnabled) return
        val normalizedWord = normalizeWord(replacementWord) ?: return
        if (normalizedWord == normalizeWord(rejectedWord)) return
        val nextCorrections = preferences.glideCorrections + (pathSignature to normalizedWord)
        gestureTypingEngine.setCorrections(nextCorrections)
        serviceScope.launch {
            preferenceRepository.recordGlideCorrection(pathSignature, normalizedWord)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private enum class SpeechUiState {
        IDLE,
        LISTENING,
        PROCESSING,
        COMPLETE,
        ERROR,
    }

    private data class PendingGlideUndo(
        val path: List<String>,
        val word: String,
        val committedText: String,
    )

    private data class PendingGlideCorrection(
        val pathSignature: String,
        val rejectedWord: String,
        val buffer: String = "",
    )

    private companion object {
        const val SPEECH_STATUS_RESET_MS = 1600L
        const val AUTO_CAP_CONTEXT_CHARS = 8
        const val GLIDE_SUGGESTION_LIMIT = 5
        const val GLIDE_SUGGESTION_DELAY_MS = 450L
        const val GLIDE_MANUAL_CORRECTION_RECORD_DELAY_MS = 700L
        const val MIN_GLIDE_MANUAL_CORRECTION_LENGTH = 2
        const val TERMUX_PACKAGE_PREFIX = "com.termux"
    }
}
