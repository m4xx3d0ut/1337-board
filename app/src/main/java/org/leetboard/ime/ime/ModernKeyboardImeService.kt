package org.leetboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import org.leetboard.ime.engine.CustomizationEngine
import org.leetboard.ime.engine.GestureTypingEngine
import org.leetboard.ime.engine.KeyActionEngine
import org.leetboard.ime.engine.LayoutEngine
import org.leetboard.ime.engine.SpeechInputEngine
import org.leetboard.ime.engine.TextContextPolicy
import org.leetboard.ime.engine.ThemeEngine
import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.ThemePreset
import org.leetboard.ime.ui.KeyboardSurfaceView

class ModernKeyboardImeService : InputMethodService() {
    private val layoutEngine = LayoutEngine()
    private val customizationEngine = CustomizationEngine()
    private val textContextPolicy = TextContextPolicy()
    private val themeEngine = ThemeEngine()
    private val gestureTypingEngine = GestureTypingEngine(textContextPolicy)
    private val speechInputEngine = SpeechInputEngine(textContextPolicy)

    private lateinit var keyActionEngine: KeyActionEngine
    private var keyboardView: KeyboardSurfaceView? = null
    private var keyboardState = KeyboardState()
    private var customizationState = CustomizationState()

    override fun onCreate() {
        super.onCreate()
        keyActionEngine = KeyActionEngine(this)
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
        customizationState = if (gesturesAllowed || speechAllowed) {
            CustomizationState()
        } else {
            CustomizationState(hiddenOptionalKeyIds = setOf("mic"))
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
        val layout = customizationEngine.apply(
            layout = layoutEngine.layoutFor(keyboardState, orientation),
            customization = customizationState,
        )
        keyboardView?.render(layout, themeEngine.resolve(ThemePreset.LEET_GREEN))
    }
}

