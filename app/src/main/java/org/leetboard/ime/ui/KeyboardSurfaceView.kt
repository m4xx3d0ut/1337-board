package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme

class KeyboardSurfaceView(context: Context) : View(context) {
    var onKey: ((KeyAction) -> Unit)? = null

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var activeKeyIds: Set<String> = emptySet()
    private var keyPreviewEnabled: Boolean = true
    private var pressedKeyId: String? = null
    private var previewKey: KeySpec? = null
    private var longPressConsumed = false
    private var downY = 0f
    private val hitKeys = mutableListOf<HitKey>()
    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = pressedKey() ?: return
            if (key.repeatable && !longPressConsumed) {
                onKey?.invoke(key.action)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }
    private val longPressRunnable = Runnable {
        val key = pressedKey() ?: return@Runnable
        val action = key.longPressAction ?: return@Runnable
        longPressConsumed = true
        previewKey = key.copy(label = action.text ?: key.label)
        onKey?.invoke(action)
        invalidate()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun render(
        layout: KeyboardLayout,
        theme: KeyboardTheme,
        activeKeyIds: Set<String> = emptySet(),
        keyPreviewEnabled: Boolean = true,
    ) {
        this.layout = layout
        this.theme = theme
        this.activeKeyIds = activeKeyIds
        this.keyPreviewEnabled = keyPreviewEnabled
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (resources.displayMetrics.density * 260).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val currentLayout = layout ?: return
        val geometry = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            theme.landscape
        } else {
            theme.portrait
        }
        canvas.drawColor(theme.colors.background)
        hitKeys.clear()

        val rows = currentLayout.rows
        if (rows.isEmpty()) return
        val rowHeight = (height - geometry.outerMarginPx * 2 - geometry.rowGapPx * (rows.size - 1)) / rows.size
        var top = geometry.outerMarginPx
        rows.forEach { row ->
            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val availableWidth = width - geometry.outerMarginPx * 2 - geometry.keyGapPx * (row.keys.size - 1)
            var left = geometry.outerMarginPx
            row.keys.forEach { key ->
                val keyWidth = availableWidth * (key.weight / totalWeight)
                val rect = RectF(left, top, left + keyWidth, top + rowHeight)
                drawKey(canvas, rect, key, geometry.keyRadiusPx, geometry.borderWidthPx)
                hitKeys += HitKey(key, rect)
                left += keyWidth + geometry.keyGapPx
            }
            top += rowHeight + geometry.rowGapPx
        }
        previewKey?.takeIf { keyPreviewEnabled }?.let { key ->
            hitKeys.firstOrNull { it.key.id == key.id }?.let { hitKey ->
                drawPreview(canvas, hitKey.rect, key.label, geometry.keyRadiusPx)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitKey = findKey(event.x, event.y)
                pressedKeyId = hitKey?.key?.id
                previewKey = hitKey?.key?.takeIf { keyPreviewEnabled }
                longPressConsumed = false
                downY = event.y
                handler.removeCallbacks(repeatRunnable)
                handler.removeCallbacks(longPressRunnable)
                hitKey?.key?.let { key ->
                    if (key.repeatable) {
                        handler.postDelayed(repeatRunnable, INITIAL_REPEAT_DELAY_MS)
                    }
                    if (key.longPressAction != null) {
                        handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeatRunnable)
                handler.removeCallbacks(longPressRunnable)
                val hitKey = findKey(event.x, event.y)
                val downKeyId = pressedKeyId
                pressedKeyId = null
                previewKey = null
                invalidate()
                if (!longPressConsumed && hitKey != null && hitKey.key.id == downKeyId) {
                    performClick()
                    onKey?.invoke(actionForRelease(hitKey.key, event.y))
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatRunnable)
                handler.removeCallbacks(longPressRunnable)
                pressedKeyId = null
                previewKey = null
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawKey(canvas: Canvas, rect: RectF, key: KeySpec, radius: Float, borderWidth: Float) {
        val isPressed = key.id == pressedKeyId
        val isActive = key.id in activeKeyIds
        fillPaint.color = when {
            isPressed -> theme.colors.pressedFill
            isActive -> theme.colors.activeModifierFill
            else -> theme.colors.keyFill
        }
        strokePaint.color = theme.colors.keyStroke
        strokePaint.strokeWidth = borderWidth
        textPaint.color = theme.colors.keyText
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            if (key.label.length > 4) 13f else 16f,
            resources.displayMetrics,
        )

        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(key.label, rect.centerX(), baseline, textPaint)
    }

    private fun drawPreview(canvas: Canvas, keyRect: RectF, label: String, radius: Float) {
        val previewHeight = keyRect.height() * 0.9f
        val previewWidth = (keyRect.width() * 1.35f).coerceAtLeast(previewHeight)
        val previewRect = RectF(
            keyRect.centerX() - previewWidth / 2,
            (keyRect.top - previewHeight - resources.displayMetrics.density * 8).coerceAtLeast(0f),
            keyRect.centerX() + previewWidth / 2,
            (keyRect.top - resources.displayMetrics.density * 8).coerceAtLeast(previewHeight),
        )
        fillPaint.color = theme.colors.keyFill
        strokePaint.color = theme.colors.keyStroke
        canvas.drawRoundRect(previewRect, radius, radius, fillPaint)
        canvas.drawRoundRect(previewRect, radius, radius, strokePaint)
        textPaint.color = theme.colors.keyText
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
        val baseline = previewRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, previewRect.centerX(), baseline, textPaint)
    }

    private fun findKey(x: Float, y: Float): HitKey? {
        return hitKeys.firstOrNull { it.rect.contains(x, y) }
    }

    private fun pressedKey(): KeySpec? {
        val id = pressedKeyId ?: return null
        return hitKeys.firstOrNull { it.key.id == id }?.key
    }

    private fun actionForRelease(key: KeySpec, upY: Float): KeyAction {
        return if (downY - upY > SWIPE_UP_THRESHOLD_PX) {
            key.swipeUpAction ?: key.action
        } else {
            key.action
        }
    }

    private data class HitKey(
        val key: KeySpec,
        val rect: RectF,
    )

    companion object {
        const val INITIAL_REPEAT_DELAY_MS = 420L
        const val REPEAT_INTERVAL_MS = 65L
        const val LONG_PRESS_DELAY_MS = 520L
        const val SWIPE_UP_THRESHOLD_PX = 44f
    }
}
