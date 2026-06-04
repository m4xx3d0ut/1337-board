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
