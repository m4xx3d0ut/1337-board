package org.leetboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.widget.Toast
import android.view.View
import android.view.inputmethod.EditorInfo
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
import org.leetboard.ime.engine.normalizeWord
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.activeKeyIds
import org.leetboard.ime.model.clearTransientModifiers
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.ui.KeyboardSurfaceView

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
    private var keyboardView: KeyboardSurfaceView? = null
    private var keyboardState = KeyboardState()
    private var preferences = KeyboardPreferences.defaults()
    private var contextHiddenKeyIds: Set<String> = emptySet()
    private var speechUiState = SpeechUiState.IDLE
    private var speechResetJob: Job? = null
    private var glideSuggestionsJob: Job? = null
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
                if (!nextPreferences.glideCorrectionLearningEnabled) pendingGlideCorrection = null
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
        speechInputEngine.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return KeyboardSurfaceView(this).also { view ->
            keyboardView = view
            view.onKey = { action, heldModifiers ->
                handleKeyAction(action, heldModifiers)
                renderKeyboard()
            }
            view.onGlide = { path, heldModifiers ->
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
        pendingGlideCorrection = null
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
        keyboardView?.render(
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
        glideSuggestionsJob?.cancel()
        glideSuggestionsJob = null
        val replacementWord = formatReplacementWord(normalizedWord, undo.committedText)
        val committedText = "$replacementWord "
        currentInputConnection?.deleteSurroundingText(undo.committedText.length, 0)
        currentInputConnection?.commitText(committedText, 1)
        recordGlideCorrection(
            pathSignature = pathSignature,
            replacementWord = normalizedWord,
            rejectedWord = undo.word,
        )
        pendingGlideUndo = undo.copy(
            word = normalizedWord,
            committedText = committedText,
        )
        glideSuggestions = glideSuggestions.filterNot { suggestion -> suggestion == normalizedWord }
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
        currentInputConnection?.deleteSurroundingText(undo.committedText.length, 0)
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
                    pendingGlideCorrection = correction.copy(buffer = correction.buffer + letters)
                } else {
                    finalizePendingGlideCorrection()
                }
            }
            KeyActionType.DELETE -> {
                pendingGlideCorrection = if (correction.buffer.isEmpty()) {
                    null
                } else {
                    correction.copy(buffer = correction.buffer.dropLast(1))
                }
            }
            KeyActionType.SPACE,
            KeyActionType.ENTER,
            KeyActionType.TAB -> finalizePendingGlideCorrection()
            else -> {
                if (correction.buffer.isEmpty()) {
                    pendingGlideCorrection = null
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
        recordGlideCorrection(correction.pathSignature, correction.buffer, correction.rejectedWord)
    }

    private fun recordPendingGlideCorrection(word: String) {
        val correction = pendingGlideCorrection ?: return
        pendingGlideCorrection = null
        recordGlideCorrection(correction.pathSignature, word, correction.rejectedWord)
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
    }
}
