package org.leetboard.ime.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

class ImeInputHostView(context: Context) : ViewGroup(context) {
    val keyboardInputView = KeyboardInputView(context)
    val hardwareCompanionView = HardwareCompanionView(context)

    private var companionMode = false

    init {
        addView(keyboardInputView)
        addView(hardwareCompanionView)
        updateChildVisibility()
    }

    fun setCompanionMode(enabled: Boolean) {
        if (companionMode == enabled) return
        companionMode = enabled
        updateChildVisibility()
        requestLayout()
    }

    private fun updateChildVisibility() {
        keyboardInputView.visibility = if (companionMode) View.GONE else View.VISIBLE
        hardwareCompanionView.visibility = if (companionMode) View.VISIBLE else View.GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = if (companionMode) hardwareCompanionView else keyboardInputView
        child.measure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(child.measuredWidth, child.measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = if (companionMode) hardwareCompanionView else keyboardInputView
        child.layout(0, 0, width, height)
    }
}
