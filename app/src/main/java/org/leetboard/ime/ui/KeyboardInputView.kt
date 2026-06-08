package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.prefs.BluetoothTrackpadMacroKey
import org.leetboard.ime.prefs.BluetoothTrackpadMacroPlacement
import org.leetboard.ime.prefs.BluetoothTrackpadMacroSide
import org.leetboard.ime.prefs.BluetoothTrackpadPlacement
import org.leetboard.ime.prefs.forSide
import org.leetboard.ime.prefs.shows

class KeyboardInputView(context: Context) : ViewGroup(context) {
    val keyboardView = KeyboardSurfaceView(context)
    var onSuggestion: ((String) -> Unit)? = null
    var onQuickModifier: ((KeyAction) -> Unit)? = null
    var onRemotePointerReport: ((RemotePointerReport) -> Unit)? = null
    var onRemoteMacro: ((BluetoothTrackpadMacroKey) -> Unit)? = null

    private val suggestionBar = GlideSuggestionBar(context)
    private val leftMacroStack = MacroStackView(context)
    private val remoteTrackpad = RemoteTrackpadView(context)
    private val rightMacroStack = MacroStackView(context)
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var suggestionBarEnabled: Boolean = false
    private var quickModifierBarEnabled: Boolean = false
    private var remoteTrackpadEnabled: Boolean = false
    private var remoteTrackpadPlacement: BluetoothTrackpadPlacement = BluetoothTrackpadPlacement.ABOVE_KEYBOARD
    private var remoteTrackpadHeightPercent: Float = 0f
    private var remoteTrackpadFillRemaining: Boolean = false
    private var remoteTrackpadMacroPlacement: BluetoothTrackpadMacroPlacement = BluetoothTrackpadMacroPlacement.OFF
    private var remoteTrackpadMacros: List<BluetoothTrackpadMacroKey> = emptyList()
    private var keyboardSurfaceVisible: Boolean = true

    init {
        addView(suggestionBar)
        addView(leftMacroStack)
        addView(remoteTrackpad)
        addView(rightMacroStack)
        addView(keyboardView)
    }

    fun render(
        layout: KeyboardLayout,
        theme: KeyboardTheme,
        activeKeyIds: Set<String> = emptySet(),
        keyPreviewEnabled: Boolean = true,
        keyHapticsEnabled: Boolean = true,
        stickyModifiersEnabled: Boolean = true,
        keyLabelStyle: KeyLabelStyle = KeyLabelStyle(),
        glideTypingEnabled: Boolean = false,
        swipeUpActionsEnabled: Boolean = true,
        speechPushToTalkEnabled: Boolean = false,
        keyLongPressDelayMs: Int = KeyboardSurfaceView.LONG_PRESS_DELAY_MS.toInt(),
        specialLongPressDelayMs: Int = KeyboardSurfaceView.LONG_PRESS_DELAY_MS.toInt(),
        suggestionBarEnabled: Boolean = glideTypingEnabled,
        suggestions: List<String> = emptyList(),
        quickModifierBarEnabled: Boolean = false,
        activeQuickModifierIds: Set<String> = emptySet(),
        quickFunctionRowEnabled: Boolean = false,
        remoteTrackpadEnabled: Boolean = false,
        remoteTrackpadPlacement: BluetoothTrackpadPlacement = BluetoothTrackpadPlacement.ABOVE_KEYBOARD,
        remoteTrackpadHeightPercent: Float = 0f,
        remoteTrackpadFillRemaining: Boolean = false,
        remoteTrackpadSensitivity: Float = 1f,
        remoteTrackpadScrollSensitivity: Float = 1f,
        remoteTrackpadInvertScrollEnabled: Boolean = false,
        remoteTrackpadTapToClickEnabled: Boolean = true,
        remoteTrackpadDedicatedButtonsEnabled: Boolean = true,
        keyboardSurfaceVisible: Boolean = true,
        remoteTrackpadMacroPlacement: BluetoothTrackpadMacroPlacement = BluetoothTrackpadMacroPlacement.OFF,
        remoteTrackpadMacros: List<BluetoothTrackpadMacroKey> = emptyList(),
    ) {
        this.theme = theme
        this.suggestionBarEnabled = suggestionBarEnabled
        this.quickModifierBarEnabled = quickModifierBarEnabled
        this.remoteTrackpadEnabled = remoteTrackpadEnabled
        this.remoteTrackpadPlacement = remoteTrackpadPlacement
        this.remoteTrackpadHeightPercent = remoteTrackpadHeightPercent
        this.remoteTrackpadFillRemaining = remoteTrackpadFillRemaining
        this.keyboardSurfaceVisible = keyboardSurfaceVisible
        this.remoteTrackpadMacroPlacement = remoteTrackpadMacroPlacement
        this.remoteTrackpadMacros = remoteTrackpadMacros
        suggestionBar.render(
            theme = theme,
            enabled = suggestionBarEnabled || quickModifierBarEnabled,
            suggestions = suggestions.take(MAX_SUGGESTIONS),
            quickModifierBarEnabled = quickModifierBarEnabled,
            activeQuickModifierIds = activeQuickModifierIds,
            quickFunctionRowEnabled = quickFunctionRowEnabled,
            onSuggestion = { word -> onSuggestion?.invoke(word) },
            onQuickModifier = { action -> onQuickModifier?.invoke(action) },
        )
        remoteTrackpad.render(
            theme = theme,
            enabled = remoteTrackpadEnabled,
            sensitivity = remoteTrackpadSensitivity,
            scrollSensitivity = remoteTrackpadScrollSensitivity,
            invertScrollEnabled = remoteTrackpadInvertScrollEnabled,
            tapToClickEnabled = remoteTrackpadTapToClickEnabled,
            dedicatedButtonsEnabled = remoteTrackpadDedicatedButtonsEnabled,
            onReport = { report -> onRemotePointerReport?.invoke(report) },
        )
        leftMacroStack.render(
            theme = theme,
            macros = visibleMacrosFor(BluetoothTrackpadMacroSide.LEFT),
            onMacro = { macro -> onRemoteMacro?.invoke(macro) },
        )
        rightMacroStack.render(
            theme = theme,
            macros = visibleMacrosFor(BluetoothTrackpadMacroSide.RIGHT),
            onMacro = { macro -> onRemoteMacro?.invoke(macro) },
        )
        keyboardView.render(
            layout = layout,
            theme = theme,
            activeKeyIds = activeKeyIds,
            keyPreviewEnabled = keyPreviewEnabled,
            keyHapticsEnabled = keyHapticsEnabled,
            stickyModifiersEnabled = stickyModifiersEnabled,
            keyLabelStyle = keyLabelStyle,
            glideTypingEnabled = glideTypingEnabled,
            swipeUpActionsEnabled = swipeUpActionsEnabled,
            speechPushToTalkEnabled = speechPushToTalkEnabled,
            keyLongPressDelayMs = keyLongPressDelayMs,
            specialLongPressDelayMs = specialLongPressDelayMs,
        )
        keyboardView.visibility = if (keyboardSurfaceVisible) VISIBLE else GONE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredKeyboardHeight = desiredKeyboardHeight()
        val suggestionHeight = suggestionHeightPx()
        val desiredTrackpadHeight = desiredTrackpadHeightPx(desiredKeyboardHeight)
        val desiredHeight = desiredKeyboardHeight + suggestionHeight + desiredTrackpadHeight
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val availableHeight = (measuredHeight - suggestionHeight).coerceAtLeast(0)
        val keyboardHeight = if (keyboardSurfaceVisible) {
            desiredKeyboardHeight.coerceAtMost(availableHeight)
        } else {
            0
        }
        val fillTrackpadSpace = remoteTrackpadEnabled && remoteTrackpadFillRemaining
        val trackpadHeight = if (fillTrackpadSpace) {
            (availableHeight - keyboardHeight).coerceAtLeast(0)
        } else {
            desiredTrackpadHeight.coerceAtMost(
                (availableHeight - keyboardHeight).coerceAtLeast(0),
            )
        }
        val leftMacroWidth = macroStackWidthPx(BluetoothTrackpadMacroSide.LEFT, trackpadHeight)
        val rightMacroWidth = macroStackWidthPx(BluetoothTrackpadMacroSide.RIGHT, trackpadHeight)
        suggestionBar.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(suggestionHeight, MeasureSpec.EXACTLY),
        )
        leftMacroStack.measure(
            MeasureSpec.makeMeasureSpec(leftMacroWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(trackpadHeight, MeasureSpec.EXACTLY),
        )
        remoteTrackpad.measure(
            MeasureSpec.makeMeasureSpec((width - leftMacroWidth - rightMacroWidth).coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(trackpadHeight, MeasureSpec.EXACTLY),
        )
        rightMacroStack.measure(
            MeasureSpec.makeMeasureSpec(rightMacroWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(trackpadHeight, MeasureSpec.EXACTLY),
        )
        keyboardView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(keyboardHeight, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val suggestionHeight = suggestionBar.measuredHeight
        val trackpadHeight = remoteTrackpad.measuredHeight
        val keyboardHeight = keyboardView.measuredHeight
        val leftMacroWidth = leftMacroStack.measuredWidth
        val rightMacroWidth = rightMacroStack.measuredWidth
        val fillTrackpadSpace = remoteTrackpadEnabled && remoteTrackpadFillRemaining
        val contentTop = if (fillTrackpadSpace) {
            0
        } else {
            (height - suggestionHeight - trackpadHeight - keyboardHeight).coerceAtLeast(0)
        }
        suggestionBar.layout(0, contentTop, width, contentTop + suggestionHeight)
        if (remoteTrackpadPlacement == BluetoothTrackpadPlacement.ABOVE_KEYBOARD) {
            val trackpadTop = contentTop + suggestionHeight
            val keyboardTop = trackpadTop + trackpadHeight
            layoutTrackpadRow(trackpadTop, keyboardTop, leftMacroWidth, rightMacroWidth)
            keyboardView.layout(0, keyboardTop, width, keyboardTop + keyboardHeight)
        } else {
            val keyboardTop = contentTop + suggestionHeight
            val trackpadTop = keyboardTop + keyboardHeight
            keyboardView.layout(0, keyboardTop, width, trackpadTop)
            layoutTrackpadRow(trackpadTop, trackpadTop + trackpadHeight, leftMacroWidth, rightMacroWidth)
        }
    }

    private fun layoutTrackpadRow(
        top: Int,
        bottom: Int,
        leftMacroWidth: Int,
        rightMacroWidth: Int,
    ) {
        leftMacroStack.layout(0, top, leftMacroWidth, bottom)
        remoteTrackpad.layout(leftMacroWidth, top, width - rightMacroWidth, bottom)
        rightMacroStack.layout(width - rightMacroWidth, top, width, bottom)
    }

    private fun desiredKeyboardHeight(): Int {
        val geometry = currentGeometry()
        return (resources.displayMetrics.heightPixels * (geometry.keyboardHeightPercent / 100f))
            .toInt()
            .coerceAtLeast((resources.displayMetrics.density * MIN_HEIGHT_DP).toInt())
    }

    private fun currentGeometry(): KeyboardGeometry {
        return if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            theme.landscape
        } else {
            theme.portrait
        }
    }

    private fun suggestionHeightPx(): Int {
        return if (suggestionBarEnabled || quickModifierBarEnabled) {
            (resources.displayMetrics.density * SUGGESTION_STRIP_HEIGHT_DP).toInt()
        } else {
            0
        }
    }

    private fun desiredTrackpadHeightPx(keyboardHeight: Int): Int {
        if (!remoteTrackpadEnabled) return 0
        return (keyboardHeight * (remoteTrackpadHeightPercent.coerceIn(18f, 55f) / 100f))
            .toInt()
            .coerceAtLeast((resources.displayMetrics.density * MIN_TRACKPAD_HEIGHT_DP).toInt())
            .coerceAtMost((keyboardHeight * MAX_TRACKPAD_TO_KEYBOARD_FRACTION).toInt())
    }

    private fun macroStackWidthPx(side: BluetoothTrackpadMacroSide, trackpadHeight: Int): Int {
        if (trackpadHeight <= 0 || visibleMacrosFor(side).isEmpty()) return 0
        return (resources.displayMetrics.density * MACRO_STACK_WIDTH_DP).toInt()
    }

    private fun visibleMacrosFor(side: BluetoothTrackpadMacroSide): List<BluetoothTrackpadMacroKey> {
        if (!remoteTrackpadEnabled || !remoteTrackpadMacroPlacement.shows(side)) return emptyList()
        return remoteTrackpadMacros.forSide(side)
    }

    private class GlideSuggestionBar(context: Context) : LinearLayout(context) {
        private val cells: List<TextView>
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var currentSuggestions: List<String> = emptyList()
        private var currentQuickModifiers: List<QuickModifierCell> = emptyList()
        private var onSuggestion: ((String) -> Unit)? = null
        private var onQuickModifier: ((KeyAction) -> Unit)? = null
        private var downX = 0f
        private var downY = 0f

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            cells = List(MAX_BAR_CELLS) {
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    isClickable = false
                    isFocusable = false
                    includeFontPadding = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, SUGGESTION_TEXT_SP)
                }.also { cell ->
                    addView(
                        cell,
                        LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
                    )
                }
            }
        }

        fun render(
            theme: KeyboardTheme,
            enabled: Boolean,
            suggestions: List<String>,
            quickModifierBarEnabled: Boolean,
            activeQuickModifierIds: Set<String>,
            quickFunctionRowEnabled: Boolean,
            onSuggestion: (String) -> Unit,
            onQuickModifier: (KeyAction) -> Unit,
        ) {
            currentQuickModifiers = when {
                !quickModifierBarEnabled -> emptyList()
                quickFunctionRowEnabled -> fourRowQuickActionCells
                else -> compactQuickActionCells
            }
            currentSuggestions = if (quickModifierBarEnabled) emptyList() else suggestions
            this.onSuggestion = onSuggestion
            this.onQuickModifier = onQuickModifier
            visibility = if (enabled) VISIBLE else GONE
            setBackgroundColor(theme.colors.background)
            cells.forEachIndexed { index, cell ->
                val quickModifier = currentQuickModifiers.getOrNull(index)
                val word = currentSuggestions.getOrNull(index)
                val margin = (resources.displayMetrics.density * SUGGESTION_GAP_DP / 2f).toInt()
                (cell.layoutParams as MarginLayoutParams).setMargins(
                    if (index == 0) margin * 2 else margin,
                    (resources.displayMetrics.density * SUGGESTION_VERTICAL_PADDING_DP).toInt(),
                    if (index == cells.lastIndex) margin * 2 else margin,
                    (resources.displayMetrics.density * SUGGESTION_VERTICAL_PADDING_DP).toInt(),
                )
                if (quickModifier == null && word == null) {
                    cell.text = ""
                    cell.visibility = GONE
                    cell.isEnabled = false
                    return@forEachIndexed
                }

                cell.visibility = VISIBLE
                cell.isEnabled = true
                cell.text = quickModifier?.label ?: word
                cell.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (currentQuickModifiers.size > MAX_SUGGESTIONS) QUICK_ACTION_TEXT_SP else SUGGESTION_TEXT_SP,
                )
                cell.setTextColor(theme.colors.keyText)
                val active = quickModifier?.id?.let { id -> id in activeQuickModifierIds } == true
                val emphasized = (quickModifier == null && index == 0) || active
                cell.typeface = if (emphasized) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }
                cell.background = suggestionBackground(
                    fillColor = if (emphasized) theme.colors.activeModifierFill else theme.colors.keyFill,
                    strokeColor = theme.colors.keyStroke,
                )
            }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            return currentSuggestions.isNotEmpty() || currentQuickModifiers.isNotEmpty()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (currentSuggestions.isEmpty() && currentQuickModifiers.isEmpty()) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isPressed = true
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isPressed = false
                    val movedX = kotlin.math.abs(event.x - downX)
                    val movedY = kotlin.math.abs(event.y - downY)
                    if (movedX <= touchSlop && movedY <= touchSlop) {
                        performCellClick(event.x, event.y)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun performCellClick(x: Float, y: Float) {
            val index = cells.indexOfFirst { cell ->
                cell.visibility == VISIBLE &&
                    x >= cell.left &&
                    x <= cell.right &&
                    y >= cell.top &&
                    y <= cell.bottom
            }
            if (index < 0) return
            val quickModifier = currentQuickModifiers.getOrNull(index)
            if (quickModifier != null) {
                performClick()
                onQuickModifier?.invoke(quickModifier.action)
                return
            }
            val word = currentSuggestions.getOrNull(index) ?: return
            performClick()
            onSuggestion?.invoke(word)
        }

        private fun suggestionBackground(fillColor: Int, strokeColor: Int): GradientDrawable {
            val density = resources.displayMetrics.density
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = density * 8f
                setColor(fillColor)
                setStroke(density.toInt().coerceAtLeast(1), strokeColor)
            }
        }

        private data class QuickModifierCell(
            val id: String,
            val label: String,
            val action: KeyAction,
        )

        companion object {
            private val compactQuickActionCells = listOf(
                QuickModifierCell("ctrl", "Ctrl", KeyAction(KeyActionType.CTRL)),
                QuickModifierCell("alt", "Alt", KeyAction(KeyActionType.ALT)),
            )
            private val fourRowQuickActionCells = compactQuickActionCells + (1..12).map { index ->
                QuickModifierCell(
                    id = "f$index",
                    label = "F$index",
                    action = KeyAction.keyEvent(android.view.KeyEvent.KEYCODE_F1 + index - 1, "F$index"),
                )
            }
        }
    }

    private class MacroStackView(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = false
        }

        fun render(
            theme: KeyboardTheme,
            macros: List<BluetoothTrackpadMacroKey>,
            onMacro: (BluetoothTrackpadMacroKey) -> Unit,
        ) {
            removeAllViews()
            visibility = if (macros.isEmpty()) GONE else VISIBLE
            setBackgroundColor(theme.colors.background)
            macros.forEach { macro ->
                addView(
                    TextView(context).apply {
                        text = macro.label
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, MACRO_TEXT_SP)
                        setTextColor(theme.colors.keyText)
                        typeface = Typeface.DEFAULT_BOLD
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onMacro(macro) }
                        background = macroBackground(theme)
                    },
                    LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        val margin = (resources.displayMetrics.density * MACRO_GAP_DP / 2f).toInt()
                        setMargins(margin, margin, margin, margin)
                    },
                )
            }
        }

        private fun macroBackground(theme: KeyboardTheme): StateListDrawable {
            return StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    macroBackground(theme, theme.colors.pressedFill),
                )
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    macroBackground(theme, theme.colors.activeModifierFill),
                )
                addState(intArrayOf(), macroBackground(theme, theme.colors.keyFill))
            }
        }

        private fun macroBackground(theme: KeyboardTheme, fillColor: Int): GradientDrawable {
            val density = resources.displayMetrics.density
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = density * 6f
                setColor(fillColor)
                setStroke(density.toInt().coerceAtLeast(1), theme.colors.keyStroke)
            }
        }
    }

    companion object {
        const val SUGGESTION_STRIP_HEIGHT_DP = 42f
        const val SUGGESTION_VERTICAL_PADDING_DP = 5f
        const val SUGGESTION_GAP_DP = 6f
        const val SUGGESTION_TEXT_SP = 15f
        const val QUICK_ACTION_TEXT_SP = 11.5f
        const val MAX_SUGGESTIONS = 5
        const val MAX_BAR_CELLS = 14
        const val MIN_HEIGHT_DP = 160f
        const val MIN_TRACKPAD_HEIGHT_DP = 76f
        const val MAX_TRACKPAD_TO_KEYBOARD_FRACTION = 0.55f
        const val MACRO_STACK_WIDTH_DP = 84f
        const val MACRO_GAP_DP = 6f
        const val MACRO_TEXT_SP = 12f
    }
}
