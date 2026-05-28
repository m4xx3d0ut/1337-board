package org.leetboard.ime.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.leetboard.ime.engine.GlideGeometryScorer
import org.leetboard.ime.engine.GlidePoint
import org.leetboard.ime.engine.GlideTouchTrace
import org.leetboard.ime.engine.RolloverTap
import org.leetboard.ime.engine.TouchRolloverCoordinator
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyLabelStyle
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.RowAlignment
import org.leetboard.ime.model.displayLabel
import org.leetboard.ime.model.isModifierKey
import org.leetboard.ime.model.resolvedDisplay

class KeyboardSurfaceView(context: Context) : View(context) {
    var onKey: ((KeyAction, HeldModifiers) -> Unit)? = null
    var onGlide: ((List<String>, GlideTouchTrace?, HeldModifiers) -> Unit)? = null
    var onFnHoldChanged: ((Boolean) -> Unit)? = null
    var onQuickNavHoldChanged: ((Boolean) -> Unit)? = null
    var onMicHoldChanged: ((Boolean) -> Unit)? = null

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme = KeyboardTheme.leetGreen
    private var activeKeyIds: Set<String> = emptySet()
    private var keyPreviewEnabled: Boolean = true
    private var stickyModifiersEnabled: Boolean = true
    private var keyLabelStyle: KeyLabelStyle = KeyLabelStyle()
    private var glideTypingEnabled: Boolean = false
    private var swipeUpActionsEnabled: Boolean = true
    private var speechPushToTalkEnabled: Boolean = false
    private var keyLongPressDelayMs: Int = LONG_PRESS_DELAY_MS.toInt()
    private var specialLongPressDelayMs: Int = LONG_PRESS_DELAY_MS.toInt()
    private var backgroundImageUri: String? = null
    private var backgroundImageBitmap: Bitmap? = null
    private var pressedKeyIds: Set<String> = emptySet()
    private var previewKey: KeySpec? = null
    private val pointerPresses = mutableMapOf<Int, PointerPress>()
    private var repeatPointerId: Int? = null
    private var longPressPointerId: Int? = null
    private var fnHoldPointerId: Int? = null
    private var quickNavHoldPointerId: Int? = null
    private var micHoldPointerId: Int? = null
    private var nextDownOrder: Long = 0L
    private val hitKeys = mutableListOf<HitKey>()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlopSquared = ViewConfiguration.get(context).scaledTouchSlop.toFloat().let { it * it }
    private val rolloverCoordinator = TouchRolloverCoordinator<QueuedKeyTap>(ROLLOVER_WINDOW_MS)
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val pointerId = repeatPointerId ?: return
            val key = pressedKey(pointerId) ?: return
            val press = pointerPresses[pointerId] ?: return
            if (key.repeatable && !press.longPressConsumed) {
                onKey?.invoke(key.action, activeHeldModifiers(excludingPointerId = pointerId))
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }
    private val longPressRunnable = Runnable {
        val pointerId = longPressPointerId ?: return@Runnable
        val key = pressedKey(pointerId) ?: return@Runnable
        val action = key.longPressAction ?: return@Runnable
        pointerPresses[pointerId] = pointerPresses.getValue(pointerId).copy(longPressConsumed = true)
        previewKey = key.copy(label = action.displayLabel())
        onKey?.invoke(action, activeHeldModifiers(excludingPointerId = pointerId))
        invalidate()
    }
    private val fnHoldRunnable = Runnable {
        val pointerId = fnHoldPointerId ?: return@Runnable
        val key = pressedKey(pointerId) ?: return@Runnable
        if (key.action.type != KeyActionType.SWITCH_FN) return@Runnable
        pointerPresses[pointerId] = pointerPresses.getValue(pointerId).copy(
            longPressConsumed = true,
            fnHoldActive = true,
        )
        previewKey = key
        onFnHoldChanged?.invoke(true)
        invalidate()
    }
    private val quickNavHoldRunnable = Runnable {
        val pointerId = quickNavHoldPointerId ?: return@Runnable
        val key = pressedKey(pointerId) ?: return@Runnable
        if (!supportsQuickNavHold(key)) return@Runnable
        pointerPresses[pointerId] = pointerPresses.getValue(pointerId).copy(
            longPressConsumed = true,
            quickNavHoldActive = true,
        )
        previewKey = key.copy(label = "Fn")
        onQuickNavHoldChanged?.invoke(true)
        invalidate()
    }
    private val micHoldRunnable = Runnable {
        val pointerId = micHoldPointerId ?: return@Runnable
        val key = pressedKey(pointerId) ?: return@Runnable
        if (!supportsMicHold(key)) return@Runnable
        pointerPresses[pointerId] = pointerPresses.getValue(pointerId).copy(
            longPressConsumed = true,
            micHoldActive = true,
        )
        previewKey = key.copy(label = "Talk")
        onMicHoldChanged?.invoke(true)
        invalidate()
    }
    private val tapDispatchRunnable = Runnable {
        flushQueuedTaps()
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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(
        layout: KeyboardLayout,
        theme: KeyboardTheme,
        activeKeyIds: Set<String> = emptySet(),
        keyPreviewEnabled: Boolean = true,
        stickyModifiersEnabled: Boolean = true,
        keyLabelStyle: KeyLabelStyle = KeyLabelStyle(),
        glideTypingEnabled: Boolean = false,
        swipeUpActionsEnabled: Boolean = true,
        speechPushToTalkEnabled: Boolean = false,
        keyLongPressDelayMs: Int = LONG_PRESS_DELAY_MS.toInt(),
        specialLongPressDelayMs: Int = LONG_PRESS_DELAY_MS.toInt(),
    ) {
        this.layout = layout
        this.theme = theme
        this.activeKeyIds = activeKeyIds
        this.keyPreviewEnabled = keyPreviewEnabled
        this.stickyModifiersEnabled = stickyModifiersEnabled
        this.keyLabelStyle = keyLabelStyle
        this.glideTypingEnabled = glideTypingEnabled
        this.swipeUpActionsEnabled = swipeUpActionsEnabled
        this.speechPushToTalkEnabled = speechPushToTalkEnabled
        this.keyLongPressDelayMs = keyLongPressDelayMs
        this.specialLongPressDelayMs = specialLongPressDelayMs
        loadBackgroundImageIfNeeded(theme.backgroundImageUri)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val geometry = currentGeometry()
        val desiredHeight = (resources.displayMetrics.heightPixels * (geometry.keyboardHeightPercent / 100f))
            .toInt()
            .coerceAtLeast((resources.displayMetrics.density * MIN_HEIGHT_DP).toInt())
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val currentLayout = layout ?: return
        val geometry = currentGeometry()
        val geometryPx = geometry.toPx(resources.displayMetrics.density)
        canvas.drawColor(theme.colors.background)
        hitKeys.clear()

        val rows = currentLayout.rows
        if (rows.isEmpty()) return
        val keyboardHeight = height.toFloat()
        val rowHeight = (keyboardHeight - geometryPx.topMargin - geometryPx.bottomMargin - geometryPx.rowGap * (rows.size - 1)) / rows.size
        var top = geometryPx.topMargin
        drawBackgroundImage(canvas)
        rows.forEach { row ->
            val occupiedWeight = row.startInsetWeight + row.endInsetWeight +
                row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val layoutWeight = row.layoutWeight?.coerceAtLeast(occupiedWeight) ?: occupiedWeight
            val slackWeight = layoutWeight - occupiedWeight
            val alignmentInsetWeight = when (row.alignment) {
                RowAlignment.START -> 0f
                RowAlignment.CENTER -> slackWeight / 2f
                RowAlignment.END -> slackWeight
            }
            val gapCount = (row.keys.size - 1).coerceAtLeast(0)
            val availableWidth = width - geometryPx.horizontalMargin * 2 - geometryPx.keyGap * gapCount
            val widthUnit = availableWidth / layoutWeight
            var left = geometryPx.horizontalMargin + widthUnit * (alignmentInsetWeight + row.startInsetWeight)
            row.keys.forEach { key ->
                val keyWidth = widthUnit * key.weight
                val rect = RectF(left, top, left + keyWidth, top + rowHeight)
                if (!key.isSpacer) {
                    drawKey(canvas, rect, key, geometryPx)
                    hitKeys += HitKey(key, rect)
                }
                left += keyWidth + geometryPx.keyGap
            }
            top += rowHeight + geometryPx.rowGap
        }
        previewKey?.takeIf { keyPreviewEnabled }?.let { key ->
            hitKeys.firstOrNull { it.key.id == key.id }?.let { hitKey ->
                drawPreview(canvas, hitKey.rect, key.label, geometryPx.keyRadius)
            }
        }
        drawGlideTrace(canvas)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(fnHoldRunnable)
        handler.removeCallbacks(quickNavHoldRunnable)
        handler.removeCallbacks(micHoldRunnable)
        handler.removeCallbacks(tapDispatchRunnable)
        clearQuickNavHoldIfActive()
        clearMicHoldIfActive()
        backgroundImageBitmap?.recycle()
        backgroundImageBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                handlePointerDown(event)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handlePointerMove(event)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(event)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatRunnable)
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(fnHoldRunnable)
                handler.removeCallbacks(quickNavHoldRunnable)
                handler.removeCallbacks(micHoldRunnable)
                handler.removeCallbacks(tapDispatchRunnable)
                rolloverCoordinator.clear()
                clearFnHoldIfActive()
                clearQuickNavHoldIfActive()
                clearMicHoldIfActive()
                pointerPresses.clear()
                pressedKeyIds = emptySet()
                previewKey = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun handlePointerDown(event: MotionEvent) {
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        if (pointerPresses.size >= MAX_ROLLOVER_POINTERS && pointerId !in pointerPresses) {
            updatePressedKeyIds()
            return
        }
        val hitKey = findKey(event.getX(index), event.getY(index))
        if (hitKey == null) {
            updatePressedKeyIds()
            return
        }
        pointerPresses[pointerId] = PointerPress(
            keyId = hitKey.key.id,
            downOrder = nextDownOrder++,
            downX = event.getX(index),
            downY = event.getY(index),
            glidePath = alphaKeyLabel(hitKey.key)?.let(::listOf).orEmpty(),
            glidePoints = listOf(PointF(event.getX(index), event.getY(index))),
            glidePointTimes = listOf(event.eventTime),
        )
        pressedKeyIds = pressedKeyIds + hitKey.key.id
        previewKey = hitKey.key.takeIf { keyPreviewEnabled }
        if (hitKey.key.repeatable) {
            repeatPointerId = pointerId
            handler.removeCallbacks(repeatRunnable)
            handler.postDelayed(repeatRunnable, INITIAL_REPEAT_DELAY_MS)
        }
        if (hitKey.key.longPressAction != null) {
            longPressPointerId = pointerId
            handler.removeCallbacks(longPressRunnable)
            handler.postDelayed(longPressRunnable, longPressDelayMsFor(hitKey.key).toLong())
        }
        if (hitKey.key.action.type == KeyActionType.SWITCH_FN) {
            fnHoldPointerId = pointerId
            handler.removeCallbacks(fnHoldRunnable)
            handler.postDelayed(fnHoldRunnable, specialLongPressDelayMs.toLong())
        }
        if (supportsQuickNavHold(hitKey.key)) {
            quickNavHoldPointerId = pointerId
            handler.removeCallbacks(quickNavHoldRunnable)
            handler.postDelayed(quickNavHoldRunnable, specialLongPressDelayMs.toLong())
        }
        if (supportsMicHold(hitKey.key)) {
            micHoldPointerId = pointerId
            handler.removeCallbacks(micHoldRunnable)
            handler.postDelayed(micHoldRunnable, specialLongPressDelayMs.toLong())
        }
    }

    private fun handlePointerMove(event: MotionEvent) {
        repeat(event.pointerCount) { index ->
            val pointerId = event.getPointerId(index)
            val press = pointerPresses[pointerId] ?: return@repeat
            if (!glideTypingEnabled || press.longPressConsumed || press.glidePath.isEmpty()) return@repeat

            val x = event.getX(index)
            val y = event.getY(index)
            val samples = buildList {
                repeat(event.historySize) { historyIndex ->
                    add(
                        TimedPoint(
                            point = PointF(
                                event.getHistoricalX(index, historyIndex),
                                event.getHistoricalY(index, historyIndex),
                            ),
                            timeMs = event.getHistoricalEventTime(historyIndex),
                        ),
                    )
                }
                add(TimedPoint(PointF(x, y), event.eventTime))
            }
            val nextPath = samples.fold(press.glidePath) { path, sample ->
                val label = alphaKeyLabel(findKey(sample.point.x, sample.point.y)?.key) ?: return@fold path
                if (path.lastOrNull() == label) path else path + label
            }
            val nextPoints = press.glidePoints + samples.map { sample -> sample.point }
            val nextPointTimes = press.glidePointTimes + samples.map { sample -> sample.timeMs }
            val movedEnough = distanceSquared(press.downX, press.downY, x, y) >= glideStartThresholdSquared()
            val crossesLetters = nextPath.distinct().size >= MIN_GLIDE_KEYS
            val isGliding = press.gliding || (movedEnough && crossesLetters)

            if (isGliding) {
                if (repeatPointerId == pointerId) {
                    handler.removeCallbacks(repeatRunnable)
                    repeatPointerId = null
                }
                if (longPressPointerId == pointerId) {
                    handler.removeCallbacks(longPressRunnable)
                    longPressPointerId = null
                }
                if (fnHoldPointerId == pointerId) {
                    handler.removeCallbacks(fnHoldRunnable)
                    fnHoldPointerId = null
                }
                if (quickNavHoldPointerId == pointerId) {
                    handler.removeCallbacks(quickNavHoldRunnable)
                    quickNavHoldPointerId = null
                }
                if (micHoldPointerId == pointerId) {
                    handler.removeCallbacks(micHoldRunnable)
                    micHoldPointerId = null
                }
                previewKey = null
            }
            pointerPresses[pointerId] = press.copy(
                glidePath = nextPath,
                glidePoints = nextPoints,
                glidePointTimes = nextPointTimes,
                gliding = isGliding,
            )
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val press = pointerPresses[pointerId] ?: return
        val releaseX = event.getX(index)
        val releaseY = event.getY(index)
        val hitKey = findKey(releaseX, releaseY)
        val pressWithReleasePoint = press.copy(
            glidePoints = press.glidePoints + PointF(releaseX, releaseY),
            glidePointTimes = press.glidePointTimes + event.eventTime,
        )
        val releasePress = alphaKeyLabel(hitKey?.key)?.let { label ->
            if (press.glidePath.lastOrNull() == label) {
                pressWithReleasePoint
            } else {
                pressWithReleasePoint.copy(glidePath = press.glidePath + label)
            }
        } ?: pressWithReleasePoint
        val pressedHitKey = hitKeys.firstOrNull { it.key.id == press.keyId }
        if (repeatPointerId == pointerId) {
            handler.removeCallbacks(repeatRunnable)
            repeatPointerId = null
        }
        if (longPressPointerId == pointerId) {
            handler.removeCallbacks(longPressRunnable)
            longPressPointerId = null
        }
        if (fnHoldPointerId == pointerId) {
            handler.removeCallbacks(fnHoldRunnable)
            fnHoldPointerId = null
        }
        if (quickNavHoldPointerId == pointerId) {
            handler.removeCallbacks(quickNavHoldRunnable)
            quickNavHoldPointerId = null
        }
        if (micHoldPointerId == pointerId) {
            handler.removeCallbacks(micHoldRunnable)
            micHoldPointerId = null
        }
        val heldModifiers = activeHeldModifiers(excludingPointerId = pointerId)
        pointerPresses.remove(pointerId)
        updatePressedKeyIds()
        previewKey = null
        if (press.fnHoldActive) {
            onFnHoldChanged?.invoke(false)
            flushQueuedTaps()
            return
        }
        if (press.quickNavHoldActive) {
            onQuickNavHoldChanged?.invoke(false)
            flushQueuedTaps()
            return
        }
        if (press.micHoldActive) {
            onMicHoldChanged?.invoke(false)
            flushQueuedTaps()
            return
        }
        val releaseGliding = releasePress.gliding ||
            (
                glideTypingEnabled &&
                    releasePress.glidePath.distinct().size >= MIN_GLIDE_KEYS &&
                    distanceSquared(press.downX, press.downY, releaseX, releaseY) >= glideStartThresholdSquared()
                )
        if (releaseGliding && releasePress.glidePath.size >= MIN_GLIDE_KEYS) {
            if (heldModifiers.isActive()) markActiveModifiersUsedInCombo()
            onGlide?.invoke(
                releasePress.glidePath,
                buildGlideTouchTrace(releasePress.glidePoints, releasePress.glidePointTimes),
                heldModifiers,
            )
            return
        }
        if (press.longPressConsumed || pressedHitKey == null) return

        val releasedOnPressedKey = hitKey?.key?.id == press.keyId
        val releasedNearPressedKey = distanceSquared(press.downX, press.downY, releaseX, releaseY) <= touchSlopSquared
        if (pressedHitKey.key.isModifierKey()) {
            if (!releasedOnPressedKey && !releasedNearPressedKey) return
            if (!press.usedInCombo && stickyModifiersEnabled) {
                performClick()
                onKey?.invoke(pressedHitKey.key.action, HeldModifiers())
            }
            return
        }

        val swipeUpAction = swipeUpActionForRelease(pressedHitKey.key, event.getY(index), press.downY)
        if (swipeUpAction == null && !releasedOnPressedKey && !releasedNearPressedKey) return

        if (heldModifiers.isActive()) markActiveModifiersUsedInCombo()
        performClick()
        val action = swipeUpAction ?: pressedHitKey.key.action
        if (swipeUpAction == null && shouldQueueRolloverTap(pressedHitKey.key, press)) {
            queueRolloverTap(press.downOrder, action, heldModifiers)
        } else {
            flushQueuedTaps()
            onKey?.invoke(action, heldModifiers)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun queueRolloverTap(downOrder: Long, action: KeyAction, heldModifiers: HeldModifiers) {
        val now = SystemClock.uptimeMillis()
        val ready = rolloverCoordinator.enqueue(
            RolloverTap(
                downOrder = downOrder,
                releaseTimeMs = now,
                value = QueuedKeyTap(action, heldModifiers),
            ),
            activeRolloverDownOrders(),
            now,
        )
        dispatchQueuedTaps(ready)
        scheduleRolloverFlushIfNeeded()
    }

    private fun flushQueuedTaps() {
        val ready = rolloverCoordinator.flush(activeRolloverDownOrders(), SystemClock.uptimeMillis())
        dispatchQueuedTaps(ready)
        scheduleRolloverFlushIfNeeded()
    }

    private fun dispatchQueuedTaps(taps: List<RolloverTap<QueuedKeyTap>>) {
        taps.forEach { tap ->
            onKey?.invoke(tap.value.action, tap.value.heldModifiers)
        }
    }

    private fun scheduleRolloverFlushIfNeeded() {
        handler.removeCallbacks(tapDispatchRunnable)
        if (rolloverCoordinator.hasPending()) {
            handler.postDelayed(tapDispatchRunnable, ROLLOVER_WINDOW_MS)
        }
    }

    private fun activeRolloverDownOrders(): Set<Long> {
        return pointerPresses.values.mapNotNull { press ->
            val key = hitKeys.firstOrNull { it.key.id == press.keyId }?.key ?: return@mapNotNull null
            press.downOrder.takeIf { shouldQueueRolloverTap(key, press) }
        }.toSet()
    }

    private fun shouldQueueRolloverTap(key: KeySpec, press: PointerPress): Boolean {
        if (press.longPressConsumed || press.gliding || key.repeatable || key.isModifierKey()) return false
        return when (key.action.type) {
            KeyActionType.COMMIT_TEXT,
            KeyActionType.SPACE,
            KeyActionType.ENTER,
            KeyActionType.TAB,
            KeyActionType.ESCAPE -> true
            else -> false
        }
    }

    private fun clearFnHoldIfActive() {
        val wasActive = pointerPresses.values.any { press -> press.fnHoldActive }
        if (wasActive) onFnHoldChanged?.invoke(false)
    }

    private fun clearQuickNavHoldIfActive() {
        val wasActive = pointerPresses.values.any { press -> press.quickNavHoldActive }
        if (wasActive) onQuickNavHoldChanged?.invoke(false)
    }

    private fun clearMicHoldIfActive() {
        val wasActive = pointerPresses.values.any { press -> press.micHoldActive }
        if (wasActive) onMicHoldChanged?.invoke(false)
    }

    private fun supportsQuickNavHold(key: KeySpec): Boolean {
        val layoutId = layout?.id ?: return false
        return key.id == "num_toggle" && layoutId in QUICK_NAV_HOLD_LAYOUTS
    }

    private fun supportsMicHold(key: KeySpec): Boolean {
        return speechPushToTalkEnabled && key.action.type == KeyActionType.MICROPHONE
    }

    private fun longPressDelayMsFor(key: KeySpec): Int {
        return if (key.isSpecialLongPressKey()) specialLongPressDelayMs else keyLongPressDelayMs
    }

    private fun KeySpec.isSpecialLongPressKey(): Boolean {
        return action.type in SPECIAL_LONG_PRESS_ACTION_TYPES ||
            longPressAction?.type in SPECIAL_LONG_PRESS_ACTION_TYPES
    }

    private fun drawKey(canvas: Canvas, rect: RectF, key: KeySpec, geometry: KeyboardGeometryPx) {
        val isPressed = key.id in pressedKeyIds
        val isActive = key.id in activeKeyIds
        fillPaint.color = when {
            isPressed -> theme.colors.pressedFill
            isActive -> theme.colors.activeModifierFill
            else -> theme.colors.keyFill
        }
        strokePaint.color = theme.colors.keyStroke
        strokePaint.strokeWidth = geometry.borderWidth
        val textAlpha = (keyLabelStyle.labelOpacity.coerceIn(0f, 1f) * 255).toInt()
        textPaint.color = theme.colors.keyText.withCombinedAlpha(textAlpha)
        textPaint.isFakeBoldText = keyLabelStyle.fontWeight >= BOLD_WEIGHT
        val display = key.resolvedDisplay(
            shiftActive = "shift" in activeKeyIds || "shift_right" in activeKeyIds,
            labelStyle = keyLabelStyle,
        )
        textPaint.textSize = fittedTextSizePx(
            label = display.label,
            baseSp = if (display.label.length > 4) keyLabelStyle.primaryTextSizeSp - 2f else keyLabelStyle.primaryTextSizeSp,
            maxWidthPx = rect.width() * 0.82f,
            maxHeightPx = rect.height() * 0.62f,
            minSp = MIN_PRIMARY_TEXT_SIZE_SP,
        )

        canvas.drawRoundRect(rect, geometry.keyRadius, geometry.keyRadius, fillPaint)
        canvas.drawRoundRect(rect, geometry.keyRadius, geometry.keyRadius, strokePaint)

        if (display.icon != null) {
            drawIcon(canvas, rect, display.icon, primary = true)
        } else {
            val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(display.label, rect.centerX(), baseline, textPaint)
        }
        drawSecondaryDisplay(canvas, rect, display.secondaryLabel, display.secondaryIcon)
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

    private fun drawGlideTrace(canvas: Canvas) {
        val points = pointerPresses.values.firstOrNull { it.gliding }?.glidePoints.orEmpty()
        if (points.size < 2) return
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
        }
        strokePaint.color = theme.colors.keyText.withCombinedAlpha(GLIDE_TRACE_ALPHA)
        strokePaint.strokeWidth = resources.displayMetrics.density * GLIDE_TRACE_WIDTH_DP
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, strokePaint)
        strokePaint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBackgroundImage(canvas: Canvas) {
        val bitmap = backgroundImageBitmap ?: return
        val opacity = theme.backgroundImageOpacity
        if (opacity <= 0f || width <= 0 || height <= 0) return
        val source = centerCropSource(bitmap.width, bitmap.height, width, height)
        val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
        bitmapPaint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(bitmap, source, destination, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun drawSecondaryDisplay(canvas: Canvas, rect: RectF, label: String?, icon: KeyIcon?) {
        if (label.isNullOrBlank() && icon == null) return
        val inset = resources.displayMetrics.density * 5f
        val size = (rect.height() * 0.26f).coerceAtMost(rect.width() * 0.24f)
        val secondaryWidth = if (!label.isNullOrBlank() && icon == null) {
            (rect.width() * 0.42f).coerceAtLeast(size)
        } else {
            size
        }
        val secondaryRect = RectF(
            rect.right - inset - secondaryWidth,
            rect.top + inset,
            rect.right - inset,
            rect.top + inset + size,
        )
        textPaint.isFakeBoldText = keyLabelStyle.fontWeight >= BOLD_WEIGHT
        if (icon != null) {
            drawIcon(canvas, secondaryRect, icon, primary = false)
        } else if (label != null) {
            textPaint.textSize = fittedTextSizePx(
                label = label,
                baseSp = keyLabelStyle.secondaryTextSizeSp,
                maxWidthPx = secondaryRect.width() * 0.92f,
                maxHeightPx = secondaryRect.height() * 0.82f,
                minSp = MIN_SECONDARY_TEXT_SIZE_SP,
            )
            val baseline = secondaryRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, secondaryRect.centerX(), baseline, textPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, rect: RectF, icon: KeyIcon, primary: Boolean) {
        val primaryScale = if (icon == KeyIcon.ENTER) 0.58f else 0.38f
        val iconSize = if (primary) rect.height() * primaryScale else rect.height()
        val iconRect = if (primary) {
            RectF(
                rect.centerX() - iconSize / 2,
                rect.centerY() - iconSize / 2,
                rect.centerX() + iconSize / 2,
                rect.centerY() + iconSize / 2,
            )
        } else {
            rect
        }
        strokePaint.color = textPaint.color
        strokePaint.strokeWidth = (resources.displayMetrics.density * if (primary) 2f else 1.3f).coerceAtLeast(1f)
        strokePaint.style = Paint.Style.STROKE
        fillPaint.color = textPaint.color

        when (icon) {
            KeyIcon.GEAR -> {
                canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconRect.width() * 0.36f, strokePaint)
                canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconRect.width() * 0.12f, strokePaint)
            }
            KeyIcon.SPACE_BAR -> {
                val y = iconRect.centerY() + iconRect.height() * 0.18f
                canvas.drawLine(iconRect.left, y, iconRect.right, y, strokePaint)
                canvas.drawLine(iconRect.left, y, iconRect.left, y - iconRect.height() * 0.28f, strokePaint)
                canvas.drawLine(iconRect.right, y, iconRect.right, y - iconRect.height() * 0.28f, strokePaint)
            }
            KeyIcon.BACKSPACE -> drawBackspaceIcon(canvas, iconRect, forward = true)
            KeyIcon.FORWARD_DELETE -> drawBackspaceIcon(canvas, iconRect, forward = false)
            KeyIcon.SHIFT -> drawShiftIcon(canvas, iconRect)
            KeyIcon.MIC -> drawMicIcon(canvas, iconRect)
            KeyIcon.SWIPE -> drawSwipeIcon(canvas, iconRect)
            KeyIcon.SWIPE_OFF -> drawSwipeOffIcon(canvas, iconRect)
            KeyIcon.NUMPAD -> drawNumpadIcon(canvas, iconRect)
            KeyIcon.QUICK_NAV -> drawQuickNavIcon(canvas, iconRect)
            KeyIcon.EMOJI -> drawEmojiIcon(canvas, iconRect)
            KeyIcon.ENTER -> drawEnterIcon(canvas, iconRect)
            KeyIcon.TAB -> drawTextIcon(canvas, iconRect, "⇥", primary)
            KeyIcon.SYMBOLS -> drawTextIcon(canvas, iconRect, "#+", primary)
            KeyIcon.ESC -> drawTextIcon(canvas, iconRect, "Esc", primary)
            KeyIcon.CTRL -> drawTextIcon(canvas, iconRect, "Ctrl", primary)
            KeyIcon.ALT -> drawTextIcon(canvas, iconRect, "Alt", primary)
            KeyIcon.FN -> drawTextIcon(canvas, iconRect, "Fn", primary)
            KeyIcon.ARROW_LEFT -> drawTextIcon(canvas, iconRect, "◀", primary)
            KeyIcon.ARROW_RIGHT -> drawTextIcon(canvas, iconRect, "▶", primary)
            KeyIcon.ARROW_UP -> drawTextIcon(canvas, iconRect, "▲", primary)
            KeyIcon.ARROW_DOWN -> drawTextIcon(canvas, iconRect, "▼", primary)
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, rect: RectF, forward: Boolean) {
        val path = Path()
        if (forward) {
            path.moveTo(rect.left, rect.centerY())
            path.lineTo(rect.left + rect.width() * 0.3f, rect.top)
            path.lineTo(rect.right, rect.top)
            path.lineTo(rect.right, rect.bottom)
            path.lineTo(rect.left + rect.width() * 0.3f, rect.bottom)
        } else {
            path.moveTo(rect.right, rect.centerY())
            path.lineTo(rect.right - rect.width() * 0.3f, rect.top)
            path.lineTo(rect.left, rect.top)
            path.lineTo(rect.left, rect.bottom)
            path.lineTo(rect.right - rect.width() * 0.3f, rect.bottom)
        }
        path.close()
        canvas.drawPath(path, strokePaint)
        canvas.drawLine(rect.centerX() - rect.width() * 0.16f, rect.centerY() - rect.height() * 0.16f, rect.centerX() + rect.width() * 0.16f, rect.centerY() + rect.height() * 0.16f, strokePaint)
        canvas.drawLine(rect.centerX() + rect.width() * 0.16f, rect.centerY() - rect.height() * 0.16f, rect.centerX() - rect.width() * 0.16f, rect.centerY() + rect.height() * 0.16f, strokePaint)
    }

    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val path = Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.centerY())
            lineTo(rect.right - rect.width() * 0.25f, rect.centerY())
            lineTo(rect.right - rect.width() * 0.25f, rect.bottom)
            lineTo(rect.left + rect.width() * 0.25f, rect.bottom)
            lineTo(rect.left + rect.width() * 0.25f, rect.centerY())
            lineTo(rect.left, rect.centerY())
            close()
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawMicIcon(canvas: Canvas, rect: RectF) {
        val micRect = RectF(rect.centerX() - rect.width() * 0.18f, rect.top, rect.centerX() + rect.width() * 0.18f, rect.centerY() + rect.height() * 0.1f)
        canvas.drawRoundRect(micRect, rect.width() * 0.18f, rect.width() * 0.18f, strokePaint)
        canvas.drawLine(rect.centerX(), micRect.bottom, rect.centerX(), rect.bottom, strokePaint)
        canvas.drawLine(rect.left + rect.width() * 0.28f, rect.bottom, rect.right - rect.width() * 0.28f, rect.bottom, strokePaint)
    }

    private fun drawSwipeIcon(canvas: Canvas, rect: RectF) {
        val path = Path().apply {
            moveTo(rect.left, rect.bottom)
            cubicTo(rect.left + rect.width() * 0.25f, rect.top, rect.right - rect.width() * 0.2f, rect.bottom, rect.right, rect.top)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawSwipeOffIcon(canvas: Canvas, rect: RectF) {
        drawSwipeIcon(canvas, rect)
        canvas.drawLine(
            rect.left + rect.width() * 0.16f,
            rect.bottom - rect.height() * 0.16f,
            rect.right - rect.width() * 0.16f,
            rect.top + rect.height() * 0.16f,
            strokePaint,
        )
    }

    private fun drawNumpadIcon(canvas: Canvas, rect: RectF) {
        val radius = rect.width() * 0.07f
        val stepX = rect.width() / 2f
        val stepY = rect.height() / 2f
        repeat(3) { row ->
            repeat(3) { column ->
                canvas.drawCircle(rect.left + column * stepX, rect.top + row * stepY, radius, fillPaint)
            }
        }
    }

    private fun drawQuickNavIcon(canvas: Canvas, rect: RectF) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val arm = rect.width().coerceAtMost(rect.height()) * 0.34f
        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, strokePaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, strokePaint)
        drawChevron(canvas, centerX, centerY - arm, 0f, -1f, rect)
        drawChevron(canvas, centerX + arm, centerY, 1f, 0f, rect)
        drawChevron(canvas, centerX, centerY + arm, 0f, 1f, rect)
        drawChevron(canvas, centerX - arm, centerY, -1f, 0f, rect)
    }

    private fun drawChevron(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, rect: RectF) {
        val size = rect.width().coerceAtMost(rect.height()) * 0.12f
        val sideX = if (dx == 0f) 1f else 0f
        val sideY = if (dy == 0f) 1f else 0f
        canvas.drawLine(x, y, x - dx * size - sideX * size, y - dy * size - sideY * size, strokePaint)
        canvas.drawLine(x, y, x - dx * size + sideX * size, y - dy * size + sideY * size, strokePaint)
    }

    private fun drawEmojiIcon(canvas: Canvas, rect: RectF) {
        val size = rect.width().coerceAtMost(rect.height())
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val radius = size * 0.42f
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        val eyeRadius = size * 0.045f
        val eyeOffsetX = size * 0.15f
        val eyeY = centerY - size * 0.1f
        canvas.drawCircle(centerX - eyeOffsetX, eyeY, eyeRadius, fillPaint)
        canvas.drawCircle(centerX + eyeOffsetX, eyeY, eyeRadius, fillPaint)

        val smileRect = RectF(
            centerX - size * 0.2f,
            centerY - size * 0.02f,
            centerX + size * 0.2f,
            centerY + size * 0.22f,
        )
        canvas.drawArc(smileRect, 20f, 140f, false, strokePaint)
    }

    private fun drawEnterIcon(canvas: Canvas, rect: RectF) {
        val y = rect.centerY()
        val right = rect.right - rect.width() * 0.08f
        val left = rect.left + rect.width() * 0.16f
        val verticalTop = rect.top + rect.height() * 0.16f
        canvas.drawLine(right, verticalTop, right, y, strokePaint)
        canvas.drawLine(right, y, left, y, strokePaint)
        canvas.drawLine(left, y, left + rect.width() * 0.24f, y - rect.height() * 0.2f, strokePaint)
        canvas.drawLine(left, y, left + rect.width() * 0.24f, y + rect.height() * 0.2f, strokePaint)
    }

    private fun drawTextIcon(canvas: Canvas, rect: RectF, label: String, primary: Boolean) {
        textPaint.textSize = fittedTextSizePx(
            label = label,
            baseSp = if (primary && label.length > 2) keyLabelStyle.primaryTextSizeSp - 3f else keyLabelStyle.primaryTextSizeSp,
            maxWidthPx = rect.width() * 0.9f,
            maxHeightPx = rect.height() * 0.84f,
            minSp = if (primary) MIN_PRIMARY_TEXT_SIZE_SP else MIN_SECONDARY_TEXT_SIZE_SP,
        )
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    private fun fittedTextSizePx(
        label: String,
        baseSp: Float,
        maxWidthPx: Float,
        maxHeightPx: Float,
        minSp: Float,
    ): Float {
        val density = resources.displayMetrics.density
        val scale = deviceLabelScale()
        var low = minSp * density
        var high = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            baseSp * scale,
            resources.displayMetrics,
        ).coerceAtLeast(low)
        repeat(TEXT_FIT_ITERATIONS) {
            val mid = (low + high) / 2f
            if (textFits(label, mid, maxWidthPx, maxHeightPx)) {
                low = mid
            } else {
                high = mid
            }
        }
        return low
    }

    private fun textFits(label: String, textSizePx: Float, maxWidthPx: Float, maxHeightPx: Float): Boolean {
        textPaint.textSize = textSizePx
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        return textPaint.measureText(label) <= maxWidthPx && textHeight <= maxHeightPx
    }

    private fun deviceLabelScale(): Float {
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp
        return if (smallestWidthDp in 1 until TABLET_SMALLEST_WIDTH_DP) PHONE_LABEL_SCALE else 1f
    }

    private fun loadBackgroundImageIfNeeded(uriString: String?) {
        if (backgroundImageUri == uriString) return
        backgroundImageUri = uriString
        backgroundImageBitmap?.recycle()
        backgroundImageBitmap = null
        if (uriString.isNullOrBlank()) return

        backgroundImageBitmap = try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sampleSize = imageSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels / 2,
            )
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun imageSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= targetWidth && sourceHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun centerCropSource(bitmapWidth: Int, bitmapHeight: Int, targetWidth: Int, targetHeight: Int): Rect {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return Rect(0, 0, bitmapWidth.coerceAtLeast(0), bitmapHeight.coerceAtLeast(0))
        }
        val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        return if (bitmapRatio > targetRatio) {
            val cropWidth = (bitmapHeight * targetRatio).toInt().coerceAtLeast(1)
            val left = (bitmapWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmapHeight)
        } else {
            val cropHeight = (bitmapWidth / targetRatio).toInt().coerceAtLeast(1)
            val top = (bitmapHeight - cropHeight) / 2
            Rect(0, top, bitmapWidth, top + cropHeight)
        }
    }

    private fun findKey(x: Float, y: Float): HitKey? {
        return hitKeys.firstOrNull { it.rect.contains(x, y) }
    }

    private fun pressedKey(pointerId: Int): KeySpec? {
        val id = pointerPresses[pointerId]?.keyId ?: return null
        return hitKeys.firstOrNull { it.key.id == id }?.key
    }

    private fun swipeUpActionForRelease(key: KeySpec, upY: Float, downY: Float): KeyAction? {
        return key.swipeUpAction?.takeIf { swipeUpActionsEnabled && downY - upY > SWIPE_UP_THRESHOLD_PX }
    }

    private fun alphaKeyLabel(key: KeySpec?): String? {
        val text = key?.action?.text?.lowercase() ?: return null
        return text.takeIf { it.length == 1 && it.first() in 'a'..'z' }
    }

    private fun buildGlideTouchTrace(points: List<PointF>, pointTimes: List<Long>): GlideTouchTrace? {
        val alphaKeys = hitKeys.mapNotNull { hitKey ->
            val label = alphaKeyLabel(hitKey.key)?.firstOrNull() ?: return@mapNotNull null
            label to hitKey.rect
        }
        if (alphaKeys.isEmpty() || points.size < MIN_GLIDE_KEYS) return null
        val unitWidth = alphaKeys.map { (_, rect) -> rect.width() }.average().toFloat().coerceAtLeast(1f)
        val unitHeight = alphaKeys.map { (_, rect) -> rect.height() }.average().toFloat().coerceAtLeast(1f)
        val originX = alphaKeys.minOf { (_, rect) -> rect.centerX() }
        val originY = alphaKeys.minOf { (_, rect) -> rect.centerY() }
        val keyCenters = alphaKeys.associate { (label, rect) ->
            label to GlidePoint(
                x = (rect.centerX() - originX) / unitWidth,
                y = (rect.centerY() - originY) / unitHeight,
            )
        }
        val tracePoints = points.mapIndexed { index, point ->
            GlidePoint(
                x = (point.x - originX) / unitWidth,
                y = (point.y - originY) / unitHeight,
                timeMs = pointTimes.getOrNull(index),
            )
        }
        return GlideTouchTrace(
            points = tracePoints,
            keyCenters = keyCenters,
            keyDwellWeights = GlideGeometryScorer.keyDwellWeights(tracePoints, keyCenters),
        )
    }

    private fun distanceSquared(startX: Float, startY: Float, endX: Float, endY: Float): Float {
        val dx = endX - startX
        val dy = endY - startY
        return dx * dx + dy * dy
    }

    private fun glideStartThresholdSquared(): Float {
        val threshold = resources.displayMetrics.density * GLIDE_START_THRESHOLD_DP
        return threshold * threshold
    }

    private fun activeHeldModifiers(excludingPointerId: Int? = null): HeldModifiers {
        val activeKeys = pointerPresses
            .filterKeys { it != excludingPointerId }
            .values
            .mapNotNull { press -> hitKeys.firstOrNull { it.key.id == press.keyId }?.key }
        return HeldModifiers(
            shift = activeKeys.any { it.action.type == KeyActionType.SHIFT },
            ctrl = activeKeys.any { it.action.type == KeyActionType.CTRL },
            alt = activeKeys.any { it.action.type == KeyActionType.ALT },
            fn = activeKeys.any { it.action.type == KeyActionType.SWITCH_FN || it.action.type == KeyActionType.FN_MODIFIER },
        )
    }

    private fun markActiveModifiersUsedInCombo() {
        pointerPresses.replaceAll { _, press ->
            val key = hitKeys.firstOrNull { it.key.id == press.keyId }?.key
            if (key?.isModifierKey() == true) press.copy(usedInCombo = true) else press
        }
    }

    private fun updatePressedKeyIds() {
        pressedKeyIds = pointerPresses.values.map { it.keyId }.toSet()
    }

    private fun currentGeometry() = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        theme.landscape
    } else {
        theme.portrait
    }

    private data class HitKey(
        val key: KeySpec,
        val rect: RectF,
    )

    private data class TimedPoint(
        val point: PointF,
        val timeMs: Long,
    )

    private data class QueuedKeyTap(
        val action: KeyAction,
        val heldModifiers: HeldModifiers,
    )

    private data class PointerPress(
        val keyId: String,
        val downOrder: Long,
        val downX: Float,
        val downY: Float,
        val usedInCombo: Boolean = false,
        val longPressConsumed: Boolean = false,
        val fnHoldActive: Boolean = false,
        val quickNavHoldActive: Boolean = false,
        val micHoldActive: Boolean = false,
        val gliding: Boolean = false,
        val glidePath: List<String> = emptyList(),
        val glidePoints: List<PointF> = emptyList(),
        val glidePointTimes: List<Long> = emptyList(),
    )

    data class KeyboardGeometryPx(
        val keyRadius: Float,
        val borderWidth: Float,
        val keyGap: Float,
        val horizontalMargin: Float,
        val topMargin: Float,
        val bottomMargin: Float,
        val rowGap: Float,
    )

    companion object {
        const val INITIAL_REPEAT_DELAY_MS = 420L
        const val REPEAT_INTERVAL_MS = 65L
        const val LONG_PRESS_DELAY_MS = 520L
        const val ROLLOVER_WINDOW_MS = 45L
        const val SWIPE_UP_THRESHOLD_PX = 44f
        const val GLIDE_START_THRESHOLD_DP = 18f
        const val GLIDE_TRACE_WIDTH_DP = 4f
        const val GLIDE_TRACE_ALPHA = 190
        const val MIN_GLIDE_KEYS = 2
        const val MIN_HEIGHT_DP = 160f
        const val TOP_MARGIN_DP = 4f
        const val BOLD_WEIGHT = 600f
        const val MAX_ROLLOVER_POINTERS = 10
        const val MIN_PRIMARY_TEXT_SIZE_SP = 7f
        const val MIN_SECONDARY_TEXT_SIZE_SP = 5f
        const val TABLET_SMALLEST_WIDTH_DP = 600
        const val PHONE_LABEL_SCALE = 0.86f
        const val TEXT_FIT_ITERATIONS = 7
        private val QUICK_NAV_HOLD_LAYOUTS = setOf("qwerty4", "compact5", "qwerty5")
        private val SPECIAL_LONG_PRESS_ACTION_TYPES = setOf(
            KeyActionType.SWITCH_FN,
            KeyActionType.NUMPAD_TOGGLE,
            KeyActionType.SWITCH_EMOJI,
            KeyActionType.TOGGLE_GESTURE_TYPING,
        )
    }
}

private fun KeyboardGeometry.toPx(density: Float): KeyboardSurfaceView.KeyboardGeometryPx {
    return KeyboardSurfaceView.KeyboardGeometryPx(
        keyRadius = keyRadiusDp * density,
        borderWidth = borderWidthDp * density,
        keyGap = keyGapDp * density,
        horizontalMargin = horizontalMarginDp * density,
        topMargin = KeyboardSurfaceView.TOP_MARGIN_DP * density,
        bottomMargin = bottomMarginDp * density,
        rowGap = rowGapDp * density,
    )
}

private fun Int.withCombinedAlpha(alpha: Int): Int {
    val baseAlpha = (this ushr 24) and 0xFF
    val nextAlpha = (baseAlpha * (alpha.coerceIn(0, 255) / 255f)).toInt()
    return (this and 0x00FFFFFF) or (nextAlpha shl 24)
}
