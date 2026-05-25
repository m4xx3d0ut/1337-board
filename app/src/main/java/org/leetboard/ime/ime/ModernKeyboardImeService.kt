package org.leetboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.leetboard.ime.engine.CustomizationEngine
import org.leetboard.ime.engine.GestureTypingEngine
import org.leetboard.ime.engine.KeyActionEngine
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.SpeechInputEngine
import org.leetboard.ime.engine.TextContextPolicy
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.activeKeyIds
import org.leetboard.ime.prefs.KeyboardPreferences
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.ui.KeyboardSurfaceView

class ModernKeyboardImeService : InputMethodService() {
    private val layoutEngine = LayoutEngine()
    private val customizationEngine = CustomizationEngine()
    private val textContextPolicy = TextContextPolicy()
    private val themeEngine = ThemeEngine()
    private val gestureTypingEngine = GestureTypingEngine(textContextPolicy)
    private val speechInputEngine = SpeechInputEngine(textContextPolicy)

    private lateinit var keyActionEngine: KeyActionEngine
    private lateinit var preferenceRepository: PreferenceRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keyboardView: KeyboardSurfaceView? = null
    private var keyboardState = KeyboardState()
    private var preferences = KeyboardPreferences.defaults()
    private var contextHiddenKeyIds: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        keyActionEngine = KeyActionEngine(this)
        preferenceRepository = PreferenceRepository(this)
        serviceScope.launch {
            preferenceRepository.preferences.collectLatest { nextPreferences ->
                preferences = nextPreferences
                renderKeyboard()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return KeyboardSurfaceView(this).also { view ->
            keyboardView = view
            view.onKey = { action ->
                keyboardState = keyActionEngine.handle(action, keyboardState)
                renderKeyboard()
            }
            renderKeyboard()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val gesturesAllowed = gestureTypingEngine.isEnabledFor(attribute)
        val speechAllowed = speechInputEngine.isAvailable(attribute)
        contextHiddenKeyIds = buildSet {
            if (!gesturesAllowed || !preferences.gestureTypingEnabled) add("gesture")
            if (!speechAllowed || !preferences.speechInputEnabled) add("mic")
        }
        renderKeyboard()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        keyboardState = KeyboardState()
        renderKeyboard()
    }

    private fun renderKeyboard() {
        val orientation = resources.configuration.orientation
        val layoutState = keyboardState.copy(
            activeLayoutId = preferences.layoutId,
            keyPreviewEnabled = preferences.keyPreviewEnabled,
        )
        val layout = customizationEngine.apply(
            layout = layoutEngine.layoutFor(layoutState, orientation),
            customization = preferences.customizationState().let { customization ->
                customization.copy(
                    hiddenOptionalKeyIds = customization.hiddenOptionalKeyIds + contextHiddenKeyIds,
                )
            },
        )
        keyboardView?.render(
            layout,
            themeEngine.resolve(
                preset = preferences.themePreset,
                portrait = preferences.portraitGeometry,
                landscape = preferences.landscapeGeometry,
            ),
            layoutState.activeKeyIds(),
            preferences.keyPreviewEnabled,
        )
    }
}
