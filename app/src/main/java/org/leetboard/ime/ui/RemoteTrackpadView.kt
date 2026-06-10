package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.leetboard.ime.model.KeyboardTheme

data class RemotePointerReport(
    val buttons: Int = 0,
    val dx: Int = 0,
    val dy: Int = 0,
    val wheel: Int = 0,
)

class RemoteTrackpadView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bounds = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var sensitivity = 1f
    private var scrollSensitivity = 1f
    private var invertScrollEnabled = false
    private var tapToClickEnabled = true
    private var dedicatedButtonsEnabled = true
    private var buttonHeightFraction = DEFAULT_BUTTON_HEIGHT_FRACTION
    private var onReport: ((RemotePointerReport) -> Unit)? = null
    private var lastX = 0f
    private var lastY = 0f
    private var tapAnchorX = 0f
    private var tapAnchorY = 0f
    private var maxPointerCount = 0
    private var scrollAccumulator = 0f
    private var moved = false
    private var activeButton = 0
    private var dragButtonHeld = false
    private var lastTapUpTimeMs = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val doubleTapSlopSquared = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat().let { it * it }

    fun render(
        theme: KeyboardTheme,
        enabled: Boolean,
        sensitivity: Float,
        scrollSensitivity: Float,
        invertScrollEnabled: Boolean,
        tapToClickEnabled: Boolean,
        dedicatedButtonsEnabled: Boolean,
        buttonHeightPercent: Float,
        doubleTapTimeoutMs: Int,
        onReport: (RemotePointerReport) -> Unit,
    ) {
        this.onReport = onReport
        if (!enabled || !tapToClickEnabled || dedicatedButtonsEnabled) {
            releaseDragButtonIfNeeded()
            clearDoubleTapCandidate()
        }
        this.theme = theme
        this.sensitivity = sensitivity
        this.scrollSensitivity = scrollSensitivity
        this.invertScrollEnabled = invertScrollEnabled
        this.tapToClickEnabled = tapToClickEnabled
        this.dedicatedButtonsEnabled = dedicatedButtonsEnabled
        this.buttonHeightFraction = (buttonHeightPercent / 100f).coerceIn(
            MIN_BUTTON_HEIGHT_FRACTION,
            MAX_BUTTON_HEIGHT_FRACTION,
        )
        this.doubleTapTimeoutMs = doubleTapTimeoutMs.toLong().coerceAtLeast(0L)
        visibility = if (enabled) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateBounds()
        fillPaint.color = theme.colors.keyFill
        strokePaint.color = if (dragButtonHeld) theme.colors.activeModifierFill else theme.colors.keyStroke
        strokePaint.strokeWidth = resources.displayMetrics.density * (if (dragButtonHeld) {
            2.2f
        } else {
            1.2f
        })
        linePaint.color = theme.colors.keyText.withCombinedAlpha(110)
        linePaint.strokeWidth = resources.displayMetrics.density * 1f
        val radius = resources.displayMetrics.density * 10f
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        canvas.drawRoundRect(bounds, radius, radius, strokePaint)
        if (dedicatedButtonsEnabled) {
            val buttonTop = buttonTop()
            canvas.drawLine(bounds.left, buttonTop, bounds.right, buttonTop, linePaint)
            canvas.drawLine(bounds.centerX(), buttonTop, bounds.centerX(), bounds.bottom, linePaint)
        }
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
                lastX = event.x
                lastY = event.y
                tapAnchorX = event.x
                tapAnchorY = event.y
                maxPointerCount = 1
                scrollAccumulator = 0f
                moved = false
                activeButton = buttonFor(event.x, event.y)
                dragButtonHeld = isDoubleTapDragStart(event) && activeButton == 0
                if (dragButtonHeld) {
                    clearDoubleTapCandidate()
                    onReport?.invoke(RemotePointerReport(buttons = LEFT_BUTTON))
                    invalidate()
                } else if (activeButton != 0) {
                    onReport?.invoke(RemotePointerReport(buttons = activeButton))
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                releaseDragButtonIfNeeded()
                lastX = event.averageX()
                lastY = event.averageY()
                tapAnchorX = lastX
                tapAnchorY = lastY
                maxPointerCount = maxOf(maxPointerCount, event.pointerCount)
                scrollAccumulator = 0f
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                maxPointerCount = maxOf(maxPointerCount, event.pointerCount)
                lastX = event.averageX(excludingIndex = event.actionIndex)
                lastY = event.averageY(excludingIndex = event.actionIndex)
                tapAnchorX = lastX
                tapAnchorY = lastY
                scrollAccumulator = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = event.averageX()
                val currentY = event.averageY()
                val dx = currentX - lastX
                val dy = currentY - lastY
                if (kotlin.math.abs(currentX - tapAnchorX) > touchSlop ||
                    kotlin.math.abs(currentY - tapAnchorY) > touchSlop
                ) {
                    moved = true
                }
                if (activeButton == 0) {
                    if (dragButtonHeld && event.pointerCount == 1 && maxPointerCount == 1) {
                        sendPointerMove(dx, dy, buttons = LEFT_BUTTON)
                    } else if (event.pointerCount >= 2) {
                        val scrollDirection = if (invertScrollEnabled) 1f else -1f
                        scrollAccumulator += dy * scrollDirection * scrollSensitivity / SCROLL_DIVISOR
                        val wheel = scrollAccumulator.toInt()
                        if (wheel != 0) {
                            scrollAccumulator -= wheel
                            moved = true
                            onReport?.invoke(RemotePointerReport(wheel = wheel))
                        }
                    } else if (maxPointerCount == 1) {
                        sendPointerMove(dx, dy)
                    }
                }
                lastX = currentX
                lastY = currentY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragButtonHeld) {
                    releaseDragButtonIfNeeded()
                    clearDoubleTapCandidate()
                } else if (activeButton != 0) {
                    onReport?.invoke(RemotePointerReport(buttons = 0))
                } else if (tapToClickEnabled && !moved) {
                    val tapButton = when {
                        !dedicatedButtonsEnabled && maxPointerCount >= 2 -> RIGHT_BUTTON
                        maxPointerCount == 1 -> LEFT_BUTTON
                        else -> 0
                    }
                    if (tapButton != 0) {
                        onReport?.invoke(RemotePointerReport(buttons = tapButton))
                        onReport?.invoke(RemotePointerReport(buttons = 0))
                    }
                    if (!dedicatedButtonsEnabled && maxPointerCount == 1) {
                        rememberTapCandidate(event)
                    } else {
                        clearDoubleTapCandidate()
                    }
                } else {
                    clearDoubleTapCandidate()
                }
                resetGesture()
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activeButton != 0) onReport?.invoke(RemotePointerReport(buttons = 0))
                releaseDragButtonIfNeeded()
                clearDoubleTapCandidate()
                resetGesture()
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
        if (!dedicatedButtonsEnabled) return 0
        updateBounds()
        if (y < buttonTop()) return 0
        return if (x < bounds.centerX()) LEFT_BUTTON else RIGHT_BUTTON
    }

    private fun buttonTop(): Float {
        return bounds.bottom - bounds.height() * buttonHeightFraction
    }

    private fun updateBounds() {
        val margin = resources.displayMetrics.density * TRACKPAD_MARGIN_DP
        bounds.set(margin, margin, width - margin, height - margin)
    }

    private fun resetGesture() {
        activeButton = 0
        dragButtonHeld = false
        maxPointerCount = 0
        scrollAccumulator = 0f
        moved = false
        invalidate()
    }

    private fun sendPointerMove(dx: Float, dy: Float, buttons: Int = 0) {
        val reportDx = (dx * sensitivity).toInt().coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX)
        val reportDy = (dy * sensitivity).toInt().coerceIn(MOUSE_AXIS_MIN, MOUSE_AXIS_MAX)
        if (reportDx != 0 || reportDy != 0) {
            onReport?.invoke(RemotePointerReport(buttons = buttons, dx = reportDx, dy = reportDy))
        }
    }

    private fun isDoubleTapDragStart(event: MotionEvent): Boolean {
        if (dedicatedButtonsEnabled || !tapToClickEnabled || event.pointerCount != 1) return false
        if (lastTapUpTimeMs <= 0L) return false
        val elapsedMs = event.eventTime - lastTapUpTimeMs
        if (elapsedMs !in 0L..doubleTapTimeoutMs) return false
        val dx = event.x - lastTapX
        val dy = event.y - lastTapY
        return dx * dx + dy * dy <= doubleTapSlopSquared
    }

    private fun rememberTapCandidate(event: MotionEvent) {
        lastTapUpTimeMs = event.eventTime
        lastTapX = event.x
        lastTapY = event.y
    }

    private fun clearDoubleTapCandidate() {
        lastTapUpTimeMs = 0L
    }

    private fun releaseDragButtonIfNeeded() {
        if (!dragButtonHeld) return
        onReport?.invoke(RemotePointerReport(buttons = 0))
        dragButtonHeld = false
        invalidate()
    }

    private fun MotionEvent.averageX(excludingIndex: Int = -1): Float {
        var total = 0f
        var count = 0
        for (index in 0 until pointerCount) {
            if (index == excludingIndex) continue
            total += getX(index)
            count += 1
        }
        return if (count == 0) x else total / count
    }

    private fun MotionEvent.averageY(excludingIndex: Int = -1): Float {
        var total = 0f
        var count = 0
        for (index in 0 until pointerCount) {
            if (index == excludingIndex) continue
            total += getY(index)
            count += 1
        }
        return if (count == 0) y else total / count
    }

    companion object {
        private const val TRACKPAD_MARGIN_DP = 6f
        private const val DEFAULT_BUTTON_HEIGHT_FRACTION = 0.23f
        private const val MIN_BUTTON_HEIGHT_FRACTION = 0.05f
        private const val MAX_BUTTON_HEIGHT_FRACTION = 0.34f
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
