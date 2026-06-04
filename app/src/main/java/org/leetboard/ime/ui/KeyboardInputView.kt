package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
import org.leetboard.ime.prefs.BluetoothTrackpadPlacement

class KeyboardInputView(context: Context) : ViewGroup(context) {
    val keyboardView = KeyboardSurfaceView(context)
    var onSuggestion: ((String) -> Unit)? = null
    var onQuickModifier: ((KeyAction) -> Unit)? = null
    var onRemotePointerReport: ((RemotePointerReport) -> Unit)? = null

    private val suggestionBar = GlideSuggestionBar(context)
    private val remoteTrackpad = RemoteTrackpadView(context)
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var suggestionBarEnabled: Boolean = false
    private var quickModifierBarEnabled: Boolean = false
    private var remoteTrackpadEnabled: Boolean = false
    private var remoteTrackpadPlacement: BluetoothTrackpadPlacement = BluetoothTrackpadPlacement.ABOVE_KEYBOARD
    private var remoteTrackpadHeightPercent: Float = 0f

    init {
        addView(suggestionBar)
        addView(remoteTrackpad)
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
        remoteTrackpadSensitivity: Float = 1f,
        remoteTrackpadScrollSensitivity: Float = 1f,
        remoteTrackpadTapToClickEnabled: Boolean = true,
    ) {
        this.theme = theme
        this.suggestionBarEnabled = suggestionBarEnabled
        this.quickModifierBarEnabled = quickModifierBarEnabled
        this.remoteTrackpadEnabled = remoteTrackpadEnabled
        this.remoteTrackpadPlacement = remoteTrackpadPlacement
        this.remoteTrackpadHeightPercent = remoteTrackpadHeightPercent
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
            tapToClickEnabled = remoteTrackpadTapToClickEnabled,
            onReport = { report -> onRemotePointerReport?.invoke(report) },
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
        val keyboardHeight = desiredKeyboardHeight.coerceAtMost(availableHeight)
        val trackpadHeight = desiredTrackpadHeight.coerceAtMost(
            (availableHeight - keyboardHeight).coerceAtLeast(0),
        )
        suggestionBar.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(suggestionHeight, MeasureSpec.EXACTLY),
        )
        remoteTrackpad.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
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
        val contentTop = (height - suggestionHeight - trackpadHeight - keyboardHeight).coerceAtLeast(0)
        suggestionBar.layout(0, contentTop, width, contentTop + suggestionHeight)
        if (remoteTrackpadPlacement == BluetoothTrackpadPlacement.ABOVE_KEYBOARD) {
            val trackpadTop = contentTop + suggestionHeight
            val keyboardTop = trackpadTop + trackpadHeight
            remoteTrackpad.layout(0, trackpadTop, width, keyboardTop)
            keyboardView.layout(0, keyboardTop, width, keyboardTop + keyboardHeight)
        } else {
            val keyboardTop = contentTop + suggestionHeight
            val trackpadTop = keyboardTop + keyboardHeight
            keyboardView.layout(0, keyboardTop, width, trackpadTop)
            remoteTrackpad.layout(0, trackpadTop, width, trackpadTop + trackpadHeight)
        }
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

    data class RemotePointerReport(
        val buttons: Int = 0,
        val dx: Int = 0,
        val dy: Int = 0,
        val wheel: Int = 0,
    )

    private class RemoteTrackpadView(context: Context) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val bounds = RectF()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var theme: KeyboardTheme = KeyboardTheme.leetGreen
        private var sensitivity = 1f
        private var scrollSensitivity = 1f
        private var tapToClickEnabled = true
        private var onReport: ((RemotePointerReport) -> Unit)? = null
        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var activeButton = 0

        fun render(
            theme: KeyboardTheme,
            enabled: Boolean,
            sensitivity: Float,
            scrollSensitivity: Float,
            tapToClickEnabled: Boolean,
            onReport: (RemotePointerReport) -> Unit,
        ) {
            this.theme = theme
            this.sensitivity = sensitivity
            this.scrollSensitivity = scrollSensitivity
            this.tapToClickEnabled = tapToClickEnabled
            this.onReport = onReport
            visibility = if (enabled) VISIBLE else GONE
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bounds.set(
                resources.displayMetrics.density * TRACKPAD_MARGIN_DP,
                resources.displayMetrics.density * TRACKPAD_MARGIN_DP,
                width - resources.displayMetrics.density * TRACKPAD_MARGIN_DP,
                height - resources.displayMetrics.density * TRACKPAD_MARGIN_DP,
            )
            fillPaint.color = theme.colors.keyFill
            strokePaint.color = theme.colors.keyStroke
            strokePaint.strokeWidth = resources.displayMetrics.density * 1.2f
            linePaint.color = theme.colors.keyText.withCombinedAlpha(110)
            linePaint.strokeWidth = resources.displayMetrics.density * 1f
            val radius = resources.displayMetrics.density * 10f
            canvas.drawRoundRect(bounds, radius, radius, fillPaint)
            canvas.drawRoundRect(bounds, radius, radius, strokePaint)
            val buttonTop = bounds.bottom - bounds.height() * 0.23f
            canvas.drawLine(bounds.left, buttonTop, bounds.right, buttonTop, linePaint)
            canvas.drawLine(bounds.centerX(), buttonTop, bounds.centerX(), bounds.bottom, linePaint)
            val cursorSize = bounds.height() * 0.18f
            canvas.drawLine(
                bounds.centerX() - cursorSize,
                bounds.centerY(),
                bounds.centerX() + cursorSize,
                bounds.centerY(),
                linePaint,
            )
            canvas.drawLine(
                bounds.centerX(),
                bounds.centerY() - cursorSize,
                bounds.centerX(),
                bounds.centerY() + cursorSize,
                linePaint,
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (visibility != VISIBLE) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    downX = event.x
                    downY = event.y
                    lastX = event.x
                    lastY = event.y
                    moved = false
                    activeButton = buttonFor(event.x, event.y)
                    if (activeButton != 0) onReport?.invoke(RemotePointerReport(buttons = activeButton))
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastX = event.averageX()
                    lastY = event.averageY()
                    moved = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentX = event.averageX()
                    val currentY = event.averageY()
                    val dx = currentX - lastX
                    val dy = currentY - lastY
                    if (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop) {
                        moved = true
                    }
                    if (activeButton == 0) {
                        if (event.pointerCount >= 2) {
                            val wheel = (-dy * scrollSensitivity / SCROLL_DIVISOR).toInt()
                            if (wheel != 0) onReport?.invoke(RemotePointerReport(wheel = wheel))
                        } else {
                            val reportDx = (dx * sensitivity).toInt().coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX)
                            val reportDy = (dy * sensitivity).toInt().coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX)
                            if (reportDx != 0 || reportDy != 0) {
                                onReport?.invoke(RemotePointerReport(dx = reportDx, dy = reportDy))
                            }
                        }
                    }
                    lastX = currentX
                    lastY = currentY
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (activeButton != 0) {
                        onReport?.invoke(RemotePointerReport(buttons = 0))
                    } else if (tapToClickEnabled && !moved) {
                        onReport?.invoke(RemotePointerReport(buttons = LEFT_BUTTON))
                        onReport?.invoke(RemotePointerReport(buttons = 0))
                    }
                    activeButton = 0
                    parent.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (activeButton != 0) onReport?.invoke(RemotePointerReport(buttons = 0))
                    activeButton = 0
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun buttonFor(x: Float, y: Float): Int {
            if (y < height * BUTTON_ZONE_TOP_FRACTION) return 0
            return if (x < width / 2f) LEFT_BUTTON else RIGHT_BUTTON
        }

        private fun MotionEvent.averageX(): Float {
            return (0 until pointerCount).sumOf { index -> getX(index).toDouble() }.toFloat() / pointerCount
        }

        private fun MotionEvent.averageY(): Float {
            return (0 until pointerCount).sumOf { index -> getY(index).toDouble() }.toFloat() / pointerCount
        }
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
        private const val TRACKPAD_MARGIN_DP = 6f
        private const val BUTTON_ZONE_TOP_FRACTION = 0.78f
        private const val LEFT_BUTTON = 0x01
        private const val RIGHT_BUTTON = 0x02
        private const val MOUSE_AXIS_MIN = -127
        private const val MOUSE_AXIS_MAX = 127
        private const val SCROLL_DIVISOR = 18f
    }
}

private fun Int.withCombinedAlpha(alpha: Int): Int {
    val boundedAlpha = alpha.coerceIn(0, 255)
    val originalAlpha = ushr(24)
    val combinedAlpha = (originalAlpha * boundedAlpha) / 255
    return (this and 0x00FFFFFF) or (combinedAlpha shl 24)
}
