package org.leetboard.ime.ui

import android.content.Context
import android.view.ViewGroup
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme

class HardwareCompanionView(context: Context) : ViewGroup(context) {
    val keyboardView = KeyboardSurfaceView(context)
    private var desiredHeightDp = 56f

    init {
        addView(keyboardView)
    }

    fun render(
        layout: KeyboardLayout,
        theme: KeyboardTheme,
        activeKeyIds: Set<String>,
        keyLabelStyle: KeyLabelStyle,
        keyHapticsEnabled: Boolean,
        stickyModifiersEnabled: Boolean,
        desiredHeightDp: Float,
    ) {
        this.desiredHeightDp = desiredHeightDp
        keyboardView.render(
            layout = layout,
            theme = theme,
            activeKeyIds = activeKeyIds,
            keyPreviewEnabled = false,
            keyHapticsEnabled = keyHapticsEnabled,
            stickyModifiersEnabled = stickyModifiersEnabled,
            keyLabelStyle = keyLabelStyle,
            glideTypingEnabled = false,
            swipeUpActionsEnabled = false,
            speechPushToTalkEnabled = false,
            specialLongPressDelayMs = Int.MAX_VALUE,
            touchDiagnosticsEnabled = false,
        )
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = keyboardView.currentRowCount().coerceAtLeast(1)
        val desiredHeight = (desiredHeightDp * rowCount * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        keyboardView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        keyboardView.layout(0, 0, width, height)
    }
}
