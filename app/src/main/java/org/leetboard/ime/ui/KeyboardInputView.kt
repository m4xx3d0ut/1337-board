package org.leetboard.ime.ui

import android.content.Context
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
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme

class KeyboardInputView(context: Context) : ViewGroup(context) {
    val keyboardView = KeyboardSurfaceView(context)
    var onSuggestion: ((String) -> Unit)? = null

    private val suggestionBar = GlideSuggestionBar(context)
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var glideTypingEnabled: Boolean = false

    init {
        addView(suggestionBar)
        addView(keyboardView)
    }

    fun render(
        layout: KeyboardLayout,
        theme: KeyboardTheme,
        activeKeyIds: Set<String> = emptySet(),
        keyPreviewEnabled: Boolean = true,
        stickyModifiersEnabled: Boolean = true,
        keyLabelStyle: KeyLabelStyle = KeyLabelStyle(),
        glideTypingEnabled: Boolean = false,
        swipeUpActionsEnabled: Boolean = true,
        glideSuggestions: List<String> = emptyList(),
    ) {
        this.theme = theme
        this.glideTypingEnabled = glideTypingEnabled
        suggestionBar.render(
            theme = theme,
            enabled = glideTypingEnabled,
            suggestions = glideSuggestions.take(MAX_SUGGESTIONS),
            onSuggestion = { word -> onSuggestion?.invoke(word) },
        )
        keyboardView.render(
            layout = layout,
            theme = theme,
            activeKeyIds = activeKeyIds,
            keyPreviewEnabled = keyPreviewEnabled,
            stickyModifiersEnabled = stickyModifiersEnabled,
            keyLabelStyle = keyLabelStyle,
            glideTypingEnabled = glideTypingEnabled,
            swipeUpActionsEnabled = swipeUpActionsEnabled,
        )
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = desiredKeyboardHeight()
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val suggestionHeight = suggestionHeightPx()
        suggestionBar.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(suggestionHeight, MeasureSpec.EXACTLY),
        )
        keyboardView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((measuredHeight - suggestionHeight).coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val suggestionHeight = suggestionHeightPx()
        suggestionBar.layout(0, 0, width, suggestionHeight)
        keyboardView.layout(0, suggestionHeight, width, height)
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
        return if (glideTypingEnabled) {
            (resources.displayMetrics.density * SUGGESTION_STRIP_HEIGHT_DP).toInt()
        } else {
            0
        }
    }

    private class GlideSuggestionBar(context: Context) : LinearLayout(context) {
        private val cells: List<TextView>
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var currentSuggestions: List<String> = emptyList()
        private var onSuggestion: ((String) -> Unit)? = null
        private var downX = 0f
        private var downY = 0f

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            cells = List(MAX_SUGGESTIONS) {
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
            onSuggestion: (String) -> Unit,
        ) {
            currentSuggestions = suggestions
            this.onSuggestion = onSuggestion
            visibility = if (enabled) VISIBLE else GONE
            setBackgroundColor(theme.colors.background)
            cells.forEachIndexed { index, cell ->
                val word = suggestions.getOrNull(index)
                val margin = (resources.displayMetrics.density * SUGGESTION_GAP_DP / 2f).toInt()
                (cell.layoutParams as MarginLayoutParams).setMargins(
                    if (index == 0) margin * 2 else margin,
                    (resources.displayMetrics.density * SUGGESTION_VERTICAL_PADDING_DP).toInt(),
                    if (index == cells.lastIndex) margin * 2 else margin,
                    (resources.displayMetrics.density * SUGGESTION_VERTICAL_PADDING_DP).toInt(),
                )
                if (word == null) {
                    cell.text = ""
                    cell.visibility = INVISIBLE
                    cell.isEnabled = false
                    return@forEachIndexed
                }

                cell.visibility = VISIBLE
                cell.isEnabled = true
                cell.text = word
                cell.setTextColor(theme.colors.keyText)
                cell.typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                cell.background = suggestionBackground(
                    fillColor = if (index == 0) theme.colors.activeModifierFill else theme.colors.keyFill,
                    strokeColor = theme.colors.keyStroke,
                )
            }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            return currentSuggestions.isNotEmpty()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (currentSuggestions.isEmpty()) return false
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
                        suggestionAt(event.x, event.y)?.let { word ->
                            performClick()
                            onSuggestion?.invoke(word)
                        }
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

        private fun suggestionAt(x: Float, y: Float): String? {
            return cells.firstOrNull { cell ->
                cell.visibility == VISIBLE &&
                    x >= cell.left &&
                    x <= cell.right &&
                    y >= cell.top &&
                    y <= cell.bottom
            }?.let { cell ->
                currentSuggestions.getOrNull(cells.indexOf(cell))
            }
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
    }

    companion object {
        const val SUGGESTION_STRIP_HEIGHT_DP = 42f
        const val SUGGESTION_VERTICAL_PADDING_DP = 5f
        const val SUGGESTION_GAP_DP = 6f
        const val SUGGESTION_TEXT_SP = 15f
        const val MAX_SUGGESTIONS = 5
        const val MIN_HEIGHT_DP = 160f
    }
}
