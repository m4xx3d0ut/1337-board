package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme

class KeyboardSurfaceView(context: Context) : View(context) {
    var onKey: ((KeyAction) -> Unit)? = null

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var pressedKeyId: String? = null
    private val hitKeys = mutableListOf<HitKey>()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun render(layout: KeyboardLayout, theme: KeyboardTheme) {
        this.layout = layout
        this.theme = theme
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKeyId = findKey(event.x, event.y)?.key?.id
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hitKey = findKey(event.x, event.y)
                val downKeyId = pressedKeyId
                pressedKeyId = null
                invalidate()
                if (hitKey != null && hitKey.key.id == downKeyId) {
                    performClick()
                    onKey?.invoke(hitKey.key.action)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKeyId = null
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
        val isModifier = key.action.type in modifierActions
        fillPaint.color = when {
            isPressed -> theme.colors.pressedFill
            isModifier -> theme.colors.keyFill
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

    private fun findKey(x: Float, y: Float): HitKey? {
        return hitKeys.firstOrNull { it.rect.contains(x, y) }
    }

    private data class HitKey(
        val key: KeySpec,
        val rect: RectF,
    )

    private companion object {
        val modifierActions = setOf(
            KeyActionType.SHIFT,
            KeyActionType.CTRL,
            KeyActionType.ALT,
        )
    }
}
