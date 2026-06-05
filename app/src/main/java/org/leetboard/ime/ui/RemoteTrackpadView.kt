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
    private var onReport: ((RemotePointerReport) -> Unit)? = null
    private var lastX = 0f
    private var lastY = 0f
    private var tapAnchorX = 0f
    private var tapAnchorY = 0f
    private var maxPointerCount = 0
    private var scrollAccumulator = 0f
    private var moved = false
    private var activeButton = 0

    fun render(
        theme: KeyboardTheme,
        enabled: Boolean,
        sensitivity: Float,
        scrollSensitivity: Float,
        invertScrollEnabled: Boolean,
        tapToClickEnabled: Boolean,
        dedicatedButtonsEnabled: Boolean,
        onReport: (RemotePointerReport) -> Unit,
    ) {
        this.theme = theme
        this.sensitivity = sensitivity
        this.scrollSensitivity = scrollSensitivity
        this.invertScrollEnabled = invertScrollEnabled
        this.tapToClickEnabled = tapToClickEnabled
        this.dedicatedButtonsEnabled = dedicatedButtonsEnabled
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
        if (dedicatedButtonsEnabled) {
            val buttonTop = bounds.bottom - bounds.height() * 0.23f
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
                if (activeButton != 0) onReport?.invoke(RemotePointerReport(buttons = activeButton))
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
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
                    if (event.pointerCount >= 2) {
                        val scrollDirection = if (invertScrollEnabled) 1f else -1f
                        scrollAccumulator += dy * scrollDirection * scrollSensitivity / SCROLL_DIVISOR
                        val wheel = scrollAccumulator.toInt()
                        if (wheel != 0) {
                            scrollAccumulator -= wheel
                            moved = true
                            onReport?.invoke(RemotePointerReport(wheel = wheel))
                        }
                    } else if (maxPointerCount == 1) {
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
                    val tapButton = when {
                        !dedicatedButtonsEnabled && maxPointerCount >= 2 -> RIGHT_BUTTON
                        maxPointerCount == 1 -> LEFT_BUTTON
                        else -> 0
                    }
                    if (tapButton != 0) {
                        onReport?.invoke(RemotePointerReport(buttons = tapButton))
                        onReport?.invoke(RemotePointerReport(buttons = 0))
                    }
                }
                resetGesture()
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activeButton != 0) onReport?.invoke(RemotePointerReport(buttons = 0))
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
        if (y < height * BUTTON_ZONE_TOP_FRACTION) return 0
        return if (x < width / 2f) LEFT_BUTTON else RIGHT_BUTTON
    }

    private fun resetGesture() {
        activeButton = 0
        maxPointerCount = 0
        scrollAccumulator = 0f
        moved = false
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
