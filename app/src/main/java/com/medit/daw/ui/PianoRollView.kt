package com.medit.daw.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.medit.daw.data.EditorPrefs
import com.medit.daw.data.ViewportState
import com.medit.daw.editor.HoverInfo
import com.medit.daw.editor.HoverZone
import com.medit.daw.editor.RollHost
import com.medit.daw.model.GridUnit
import com.medit.daw.model.MeterMap
import com.medit.daw.model.Note
import com.medit.daw.model.Project
import com.medit.daw.model.TempoPoint
import com.medit.daw.model.isBlackKey
import com.medit.daw.model.noteName
import com.medit.daw.model.snapTick
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.medit.daw.R

/**
 * Tablet-first piano roll.
 *
 * The view deliberately owns its gestures and drawing instead of delegating to
 * Compose: stylus hover, pointer-level hysteresis and one-undo-per-gesture
 * editing do not map cleanly onto stock gesture detectors. The editor state is
 * pushed in with [syncState]; while a finger/stylus drag is in progress the
 * view previews locally and calls the host only once on release.
 */
class PianoRollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var host: RollHost? = null
        set(value) {
            field = value
            invalidate()
        }

    // ------------------------------------------------------------------
    // State mirrored from the editor
    // ------------------------------------------------------------------

    private var project: Project? = null
    private var notesByPitch: Map<Int, List<Note>> = emptyMap()
    private var prefs = EditorPrefs()
    private var selectionIds: Set<Long> = emptySet()
    private var viewportEpoch = Int.MIN_VALUE
    private var playing = false
    private var meter: MeterMap? = null

    // ------------------------------------------------------------------
    // Viewport (dp-based so rotation/density changes keep the same view)
    // ------------------------------------------------------------------

    private var startTick = 0.0
    private var dpPerTick = 0.14f
    private var lowestPitch = 48f
    private var rowHeightDp = 20f

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale

    private val keyboardWidth = 58f * density
    private val rulerHeight = 30f * density
    private val loopLaneHeight = 16f * density
    private val topHeight get() = rulerHeight + loopLaneHeight
    private val resizeHandle = 22f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val contentLeft get() = keyboardWidth
    private val contentTop get() = topHeight
    private val contentRight get() = width.toFloat()
    private val contentBottom get() = height.toFloat()
    private val tickScale get() = dpPerTick * density
    private val rowPx get() = rowHeightDp * density

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    // ------------------------------------------------------------------
    // Paints and palette
    // ------------------------------------------------------------------

    private val colBg = 0xFF0E1116.toInt()
    private val colPanel = 0xFF141A21.toInt()
    private val colPanelRaised = 0xFF1A222B.toInt()
    private val colRowWhite = 0xFF161D25.toInt()
    private val colRowBlack = 0xFF11161C.toInt()
    private val colGrid = 0xFF1F2933.toInt()
    private val colBeat = 0xFF2A3744.toInt()
    private val colBar = 0xFF3B4E60.toInt()
    private val colNote = 0xFFF2B65A.toInt()
    private val colSelected = 0xFF3ED6C4.toInt()
    private val colText = 0xFFE7EDF4.toInt()
    private val colDim = 0xFF93A1B1.toInt()
    private val colFaint = 0xFF5D6B7A.toInt()
    private val colPlayhead = 0xFFFF6F6A.toInt()
    private val colLoop = 0xFF2BA99A.toInt()
    private val colWhiteKey = 0xFFD9E1E9.toInt()
    private val colBlackKey = 0xFF1B232C.toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(10f)
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(8.5f)
    }
    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = sp(10f)
    }
    private val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = sp(9f)
    }
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tmpRect = RectF()
    private val loopRect = RectF()
    private val hudRect = RectF()
    private val path = Path()

    // ------------------------------------------------------------------
    // Gesture state
    // ------------------------------------------------------------------

    private enum class Mode {
        NONE,
        PENDING_EMPTY,
        PENDING_NOTE,
        PAN,
        PINCH,
        DRAW,
        DRAG_MOVE,
        DRAG_RESIZE,
        MARQUEE,
        RULER_SEEK,
        LOOP_DRAG,
        KEY_PREVIEW,
        ERASE,
        VELOCITY_DRAG,
        SUSTAIN_DRAG,
        TEMPO_DRAG,
    }

    private var mode = Mode.NONE
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTick = 0.0
    private var downPitch = 48
    private var gestureMoved = false
    private var longPressFired = false

    private var panStartTick = 0.0
    private var panStartLowest = 0f
    private var panAxis = 0 // 0 unknown, 1 horizontal, 2 vertical

    private var pinchStartSpanX = 1f
    private var pinchStartSpanY = 1f
    private var pinchStartDpPerTick = 0.14f
    private var pinchStartRowHeight = 20f
    private var pinchAnchorTick = 0.0
    private var pinchAnchorPitch = 48f

    private var downNoteId: Long? = null
    private var downZone = HoverZone.EMPTY
    private var activeNoteIds: Set<Long> = emptySet()
    private var dragOriginals: List<Note> = emptyList()
    private var dragPrimary: Note? = null
    private var dragDownTick = 0.0
    private var dragDownPitch = 48
    private var dragPreview: List<Note>? = null

    private var drawStartTick = 0
    private var drawPitch = 48
    private var drawEndTick = 0

    private var marqueeStartX = 0f
    private var marqueeStartY = 0f
    private var marqueeEndX = 0f
    private var marqueeEndY = 0f
    private var marqueeSelection: Set<Long> = emptySet()
    private var marqueeBaseSelection: Set<Long> = emptySet()

    // Velocity graph overlay. Dragging a node previews locally and the whole
    // gesture is committed as one undo step on release.
    private var velocityPrimaryId: Long? = null
    private var velocityOriginals: List<Note> = emptyList()
    private var velocityPreview: List<Note>? = null
    private var velocityDownY = 0f
    private var velocityMoved = false
    private var velocityDisplayPitch: Int? = null
    private var velocityDisplayVelocity: Int? = null

    private var loopDragHandle = 0 // 1 start, 2 end, 3 new range
    private var loopDragStart = 0
    private var loopDragEnd = 0
    private var loopDragging = false

    private var keyPreviewPitch: Int? = null

    private var lastTapNoteId: Long? = null
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    // ------------------------------------------------------------------
    // Hover state
    // ------------------------------------------------------------------

    private var hoverActive = false
    private var hoverPitch: Int? = null
    private var hoverNoteId: Long? = null
    private var hoverZone = HoverZone.EMPTY
    private var hoverRawTick = 0
    private var ghostStart = 0
    private var ghostPitch = 48
    private var ghostDuration = 0
    private var lastHoverSignature: List<Any?> = emptyList()
    private var hoverClearScheduled = false

    // ------------------------------------------------------------------
    // Playback / frame state
    // ------------------------------------------------------------------

    private var frameScheduled = false
    private var viewportDirty = false
    private var lastIdleTick = -1.0
    private var lastPreviewPitch = -1
    private var lastPreviewAt = 0L
    private var scrollHintUntil = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val longPressRunnable = Runnable { onLongPress() }

    private val hoverClearRunnable = Runnable {
        hoverClearScheduled = false
        if (hoverActive) clearHover(sendToHost = true)
    }

    private val idleTickRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            val tick = host?.playheadTick() ?: 0.0
            if (abs(tick - lastIdleTick) > 0.01) {
                lastIdleTick = tick
                invalidate()
            }
            handler.postDelayed(this, 280)
        }
    }

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        if (playing) {
            val tick = host?.playheadTick() ?: 0.0
            if (prefs.autoScroll) followPlayhead(tick)
            invalidate()
            ensureFrame()
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun syncState(
        project: Project?,
        prefs: EditorPrefs,
        selection: Set<Long>,
        viewport: ViewportState,
        epoch: Int,
        playing: Boolean,
    ) {
        val first = this.project == null && project != null
        val projectChanged = project !== this.project
        this.project = project
        this.prefs = prefs
        this.selectionIds = selection
        this.playing = playing

        if (projectChanged) {
            rebuildMeter()
            notesByPitch = project?.notes?.groupBy { it.pitch } ?: emptyMap()
            velocityPreview = null
            velocityPrimaryId = null
            velocityOriginals = emptyList()
            velocityDisplayPitch = null
            velocityDisplayVelocity = null
        }

        if (first || epoch != viewportEpoch) {
            viewportEpoch = epoch
            startTick = viewport.startTick
            dpPerTick = viewport.dpPerTick
            lowestPitch = viewport.lowestPitch
            rowHeightDp = viewport.rowHeightDp
            dragPreview = null
            loopDragging = false
            clampViewport()
            viewportDirty = false
        }

        if (playing) {
            ensureFrame()
        } else if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
            if (viewportDirty) publishViewport()
            viewportDirty = false
        }

        val h = hoverNoteId
        if (h != null && project?.noteById(h) == null) {
            clearHover(sendToHost = true)
        }
        invalidate()
    }

    fun zoomHorizontal(factor: Float) {
        val center = startTick + visibleTicks() / 2.0
        dpPerTick = (dpPerTick * factor).coerceIn(MIN_DP_PER_TICK, MAX_DP_PER_TICK)
        startTick = center - visibleTicks() / 2.0
        afterViewportEdit()
    }

    fun zoomVertical(factor: Float) {
        val centerPitch = lowestPitch + visibleRows() / 2f
        rowHeightDp = (rowHeightDp * factor).coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
        lowestPitch = centerPitch - visibleRows() / 2f
        afterViewportEdit()
    }

    fun followPlayheadNow() {
        val tick = host?.playheadTick() ?: return
        startTick = (tick - visibleTicks() * 0.15).coerceAtLeast(0.0)
        clampViewport()
        invalidate()
    }

    fun resetViewportHelp() {
        scrollHintUntil = SystemClock.uptimeMillis() + 1200
        invalidate()
    }

    /** Clears the local ghost/preview when the editor dismisses the hover toolbar. */
    fun dismissHover() {
        clearHover(sendToHost = false)
    }

    /**
     * Fits the whole song into the roll: horizontal zoom covers the last note
     * plus a little breathing room, vertical zoom covers the used pitch range
     * with padding. Used by the context.getString(R.string.roll_fit_all) dock button.
     */
    fun fitToSong() {
        val p = project ?: return
        val widthPx = contentRight - contentLeft
        val heightPx = contentBottom - contentTop
        if (widthPx <= dp(40f) || heightPx <= dp(40f)) return

        val end = max(p.songEndTick(), p.ppq * 4).coerceAtLeast(1)
        val spanTicks = end + p.ppq * 1.5
        val widthDp = widthPx / density
        dpPerTick = (widthDp / spanTicks).toFloat()
            .coerceIn(MIN_DP_PER_TICK, MAX_DP_PER_TICK)
        startTick = 0.0

        if (p.notes.isNotEmpty()) {
            val minPitch = p.notes.minOf { it.pitch }
            val maxPitch = p.notes.maxOf { it.pitch }
            val padding = 3
            val rows = (maxPitch - minPitch + 1 + padding * 2).coerceAtLeast(10)
            val heightDp = heightPx / density
            rowHeightDp = (heightDp / rows).coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
            val center = (minPitch + maxPitch) / 2f
            lowestPitch = center - visibleRows() / 2f
        }

        afterViewportEdit()
    }

    /** Scrolls the view by whole bars, keeping the current zoom. */
    fun scrollByBars(bars: Int) {
        if (bars == 0) return
        val m = meter ?: return
        val currentBar = m.barAt(startTick.toInt().coerceAtLeast(0))
        val target = (currentBar + bars).coerceAtLeast(0)
        startTick = m.barStartTick(target).toDouble()
        afterViewportEdit()
    }

    /** Vertically centers the used pitch range without touching the time zoom. */
    fun fitPitches() {
        val p = project ?: return
        if (p.notes.isEmpty()) return
        val minPitch = p.notes.minOf { it.pitch }
        val maxPitch = p.notes.maxOf { it.pitch }
        val center = (minPitch + maxPitch) / 2f
        lowestPitch = center - visibleRows() / 2f
        afterViewportEdit()
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusable = true
        handler.post(idleTickRunnable)
        if (playing) ensureFrame()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
        clearHover(sendToHost = true)
        publishViewport()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampViewport()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ------------------------------------------------------------------
    // Coordinate helpers
    // ------------------------------------------------------------------

    private fun tickToX(tick: Double): Float =
        (contentLeft + (tick - startTick) * tickScale).toFloat()

    private fun tickToX(tick: Int): Float = tickToX(tick.toDouble())

    private fun xToTick(x: Float): Double =
        startTick + (x - contentLeft) / tickScale.toDouble()

    private fun pitchToTop(pitch: Int): Float =
        contentBottom - (pitch - lowestPitch + 1f) * rowPx

    private fun pitchAtY(y: Float): Int =
        floor(lowestPitch + (contentBottom - y) / rowPx).toInt().coerceIn(0, 127)

    private fun visibleTicks(): Double =
        ((contentRight - contentLeft) / tickScale).toDouble().coerceAtLeast(1.0)

    private fun visibleRows(): Float =
        ((contentBottom - contentTop) / rowPx).coerceAtLeast(1f)

    /** Height of the velocity graph overlay, clamped so it stays usable. */
    private val velocityLaneHeight: Float
        get() = ((contentBottom - contentTop) * 0.30f).coerceIn(dp(84f), dp(148f))

    private val velocityLaneTop: Float get() = contentBottom - velocityLaneHeight

    private fun velocityToY(velocity: Int): Float {
        val top = velocityLaneTop + dp(20f)
        val bottom = contentBottom - dp(10f)
        val frac = (velocity.coerceIn(1, 127) - 1) / 126f
        return bottom - frac * (bottom - top).coerceAtLeast(dp(1f))
    }

    private fun velocityDeltaForDy(dy: Float): Int {
        val top = velocityLaneTop + dp(20f)
        val bottom = contentBottom - dp(10f)
        val span = (bottom - top).coerceAtLeast(dp(1f))
        return (-dy / span * 126f).roundToInt()
    }

    private fun velocityLaneContains(x: Float, y: Float): Boolean =
        prefs.velocityEditor && velocityLaneTop >= contentTop + dp(20f) &&
            x >= contentLeft && y >= velocityLaneTop && y <= contentBottom

    // ------------------------------------------------------------------
    // Sustain pedal (CC64) lane along the bottom edge.
    // ------------------------------------------------------------------

    /** Pedal-down events of the active track as [down, up) tick segments. */
    private fun sustainSegments(): List<Pair<Int, Int>> {
        val p = project ?: return emptyList()
        val evs = p.track.rawEvents.filter { it.isSustain }.sortedBy { it.tick }
        val out = ArrayList<Pair<Int, Int>>()
        var down: Int? = null
        for (e in evs) {
            val on = e.data[1] >= 64
            if (on) {
                if (down == null) down = e.tick
            } else {
                val d = down
                if (d != null) {
                    out += d to e.tick
                    down = null
                }
            }
        }
        val open = down
        if (open != null) out += open to (p.songEndTick() + p.ppq)
        return out
    }

    private val sustainLaneHeight: Float get() = dp(30f)

    /** Bottom strip reserved for the velocity editor, when it is on. */
    private val velocityReserved: Float
        get() = if (prefs.velocityEditor) velocityLaneHeight else 0f

    /** Bottom strip reserved for the pedal lane, when it is on. */
    private val pedalReserved: Float
        get() = if (prefs.showSustainLane) sustainLaneHeight else 0f

    private val sustainLaneTop: Float
        get() = contentBottom - velocityReserved - sustainLaneHeight

    private fun sustainLaneContains(x: Float, y: Float): Boolean =
        prefs.showSustainLane && sustainLaneTop >= contentTop + dp(20f) &&
            x >= contentLeft && x <= contentRight &&
            y >= sustainLaneTop && y < contentBottom - velocityReserved

    // ------------------------------------------------------------------
    // Tempo editor lane (project-wide BPM curve).
    // ------------------------------------------------------------------

    private val bpmMin = 40.0
    private val bpmMax = 240.0

    /** Height of the tempo graph overlay, clamped so it stays usable. */
    private val tempoLaneHeight: Float
        get() = ((contentBottom - contentTop) * 0.26f).coerceIn(dp(80f), dp(140f))

    private val tempoLaneBottom: Float
        get() = contentBottom - velocityReserved - pedalReserved

    private val tempoLaneTop: Float
        get() = tempoLaneBottom - tempoLaneHeight

    private fun tempoLaneContains(x: Float, y: Float): Boolean =
        prefs.showTempoLane && tempoLaneTop >= contentTop + dp(20f) &&
            x >= contentLeft && x <= contentRight &&
            y >= tempoLaneTop && y < tempoLaneBottom

    /** Base tempo at tick 0 plus every project-level tempo change. */
    private fun tempoPoints(): List<TempoPoint> {
        val p = project ?: return emptyList()
        val out = ArrayList<TempoPoint>(p.tempoChanges.size + 1)
        out += TempoPoint(0, p.bpm)
        out.addAll(p.tempoChanges)
        return out.sortedBy { it.tick }
    }

    private fun bpmToY(bpm: Double): Float {
        val top = tempoLaneTop + dp(14f)
        val bottom = tempoLaneBottom - dp(8f)
        val frac = ((bpm.coerceIn(bpmMin, bpmMax) - bpmMin) / (bpmMax - bpmMin)).toFloat()
        return bottom - frac * (bottom - top).coerceAtLeast(dp(1f))
    }

    private fun yToBpm(y: Float): Double {
        val top = tempoLaneTop + dp(14f)
        val bottom = tempoLaneBottom - dp(8f)
        val span = (bottom - top).coerceAtLeast(dp(1f))
        val frac = ((bottom - y) / span).coerceIn(0f, 1f)
        return bpmMin + frac * (bpmMax - bpmMin)
    }

    /** Segment whose span covers [tick], or null. */
    private fun sustainSegmentAt(tick: Int): Pair<Int, Int>? =
        sustainSegments().firstOrNull { (down, up) -> tick >= down && tick < up }

    /** Current drag preview (down tick, up tick) while drawing a segment. */
    private var sustainPreview: Pair<Int, Int>? = null

    private fun clampViewport() {
        rowHeightDp = rowHeightDp.coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
        dpPerTick = dpPerTick.coerceIn(MIN_DP_PER_TICK, MAX_DP_PER_TICK)
        val p = project
        val visible = visibleTicks()
        val end = (p?.songEndTick() ?: 0).toDouble()
        val maxStart = max(end + (p?.ppq ?: 480) * 16.0, visible * 0.5)
        startTick = startTick.coerceIn(0.0, maxStart)
        val rows = visibleRows()
        val maxLow = (128f - rows).coerceAtLeast(0f)
        lowestPitch = lowestPitch.coerceIn(0f, maxLow)
    }

    private fun afterViewportEdit() {
        clampViewport()
        scrollHintUntil = SystemClock.uptimeMillis() + 900
        viewportDirty = true
        publishViewport()
        invalidate()
    }

    private fun publishViewport() {
        host?.onViewportSettled(
            ViewportState(
                startTick = startTick,
                dpPerTick = dpPerTick,
                lowestPitch = lowestPitch,
                rowHeightDp = rowHeightDp,
            )
        )
    }

    private fun rebuildMeter() {
        val p = project
        meter = p?.let { MeterMap(it.ppq, it.timeSignatures, it.timeSignature) }
    }

    private fun inputDurationTicks(): Int {
        val p = project ?: return 480
        val unit = if (prefs.inputDuration == GridUnit.OFF) GridUnit.QUARTER else prefs.inputDuration
        return unit.ticks(p.ppq).coerceAtLeast(1)
    }

    private fun snapIfEnabled(tick: Int): Int {
        val p = project ?: return tick
        return if (prefs.snapEnabled) snapTick(tick, prefs.grid, p.ppq) else tick
    }

    private fun ensureFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun followPlayhead(tick: Double) {
        if (contentRight <= contentLeft) return
        val visible = visibleTicks()
        val left = startTick
        val right = left + visible
        if (tick < left || tick > left + visible * 0.82) {
            startTick = (tick - visible * 0.16).coerceAtLeast(0.0)
            clampViewport()
            viewportDirty = true
        }
    }

    // ------------------------------------------------------------------
    // Touch input
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (project == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTouch(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) beginPinch(event)
            }
            MotionEvent.ACTION_MOVE -> updateTouch(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> endTouch(event)
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun beginTouch(event: MotionEvent) {
        parent?.requestDisallowInterceptTouchEvent(true)
        handler.removeCallbacks(longPressRunnable)
        clearHover(sendToHost = true)
        activePointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        lastX = downX
        lastY = downY
        gestureMoved = false
        longPressFired = false
        downTick = xToTick(downX)
        downPitch = pitchAtY(downY)
        dragPreview = null
        marqueeSelection = emptySet()

        when {
            downX < contentLeft && downY >= topHeight -> {
                mode = Mode.KEY_PREVIEW
                keyPreviewPitch = downPitch
                host?.previewNote(downPitch, prefs.inputVelocity)
            }
            downY < rulerHeight -> {
                mode = Mode.RULER_SEEK
                host?.seekTo(xToTick(downX).coerceAtLeast(0.0))
            }
            downY < topHeight -> {
                mode = Mode.LOOP_DRAG
                beginLoopDrag(downX)
            }
            velocityLaneContains(downX, downY) -> beginVelocityTouch(event)
            sustainLaneContains(downX, downY) -> beginSustainTouch(event)
            tempoLaneContains(downX, downY) -> beginTempoTouch(event)
            else -> beginRollTouch(event)
        }
        invalidate()
    }

    private fun beginRollTouch(event: MotionEvent) {
        val p = project ?: return
        val stylus = isStylus(event)
        val note = findNoteAt(event.x, event.y)

        if (isEraser(event)) {
            if (note != null) {
                mode = Mode.ERASE
                downNoteId = note.id
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else {
                mode = Mode.NONE
            }
            return
        }

        // Multi-select is an explicit tool mode: a tap toggles one note and a
        // drag (from a note or empty space) sweeps an additive marquee. Note
        // moving/resizing is intentionally suspended while it is on.
        // With pen-only selection, a finger never enters selection mode.
        if (prefs.multiSelectMode && (!prefs.penOnlySelection || stylus)) {
            downPitch = pitchAtY(event.y)
            if (note != null) {
                downNoteId = note.id
                downZone = zoneFor(note, event.x, event.y, null)
                activeNoteIds = selectionIds
                dragOriginals = p.notes.filter { it.id in activeNoteIds }
                dragPrimary = note
                mode = Mode.PENDING_NOTE
            } else {
                downNoteId = null
                mode = Mode.PENDING_EMPTY
            }
            return
        }

        if (note != null) {
            downNoteId = note.id
            downZone = zoneFor(note, event.x, event.y, null)
            activeNoteIds =
                if (note.id in selectionIds && selectionIds.size > 1) selectionIds
                else setOf(note.id)
            dragOriginals = p.notes.filter { it.id in activeNoteIds }
            dragPrimary = note
            dragDownTick = xToTick(event.x)
            dragDownPitch = pitchAtY(event.y)
            mode = Mode.PENDING_NOTE
            // Long-press toggles selection for both finger and pen: the pen
            // keeps precise drag editing, but can still build a multi-selection.
            handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            return
        }

        downNoteId = null
        downPitch = pitchAtY(event.y)
        val snapped = snapIfEnabled(xToTick(event.x).toInt()).coerceAtLeast(0)
        if (stylus) {
            mode = Mode.DRAW
            drawStartTick = snapped
            drawPitch = downPitch
            drawEndTick = snapped + inputDurationTicks()
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            mode = Mode.PENDING_EMPTY
            handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    private fun beginVelocityTouch(event: MotionEvent) {
        val p = project
        val note = if (p != null) findVelocityNodeAt(event.x, event.y) else null
        if (note == null) {
            // Empty part of the lane keeps the familiar scroll gesture, or
            // starts an additive marquee while the multi-select tool is on.
            if (prefs.multiSelectMode) {
                beginMarquee(clearExisting = false)
            } else {
                beginPan()
            }
            return
        }
        downNoteId = note.id
        velocityPrimaryId = note.id
        velocityOriginals = emptyList()
        velocityPreview = null
        velocityMoved = false
        velocityDownY = event.y
        velocityDisplayPitch = note.pitch
        velocityDisplayVelocity = note.velocity
        val current = host?.selection() ?: selectionIds
        activeNoteIds = if (note.id in current && current.size > 1) current else setOf(note.id)
        if (current != activeNoteIds) host?.setRollSelection(activeNoteIds)
        selectionIds = activeNoteIds
        velocityOriginals = p?.notes?.filter { it.id in activeNoteIds } ?: emptyList()
        mode = Mode.VELOCITY_DRAG
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun findVelocityNodeAt(x: Float, y: Float): Note? {
        val p = project ?: return null
        val radius = dp(22f)
        var best: Note? = null
        var bestDistance = radius * radius
        for (note in p.notes) {
            val nx = tickToX(note.startTick)
            if (nx < contentLeft - dp(6f) || nx > contentRight + dp(6f)) continue
            val ny = velocityToY(note.velocity)
            val dx = x - nx
            val dy = y - ny
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = note
            }
        }
        return best
    }

    /**
     * Tempo lane touch: tap empty space places a tempo change, tap a node
     * removes it, dragging a node moves it (the tick-0 base point only moves
     * vertically — the base tempo always applies from tick 0).
     */
    private fun beginTempoTouch(event: MotionEvent) {
        mode = Mode.TEMPO_DRAG
        tempoDragFromTick = findTempoNodeAt(event.x, event.y)?.tick ?: -1
        tempoPreviewTick = xToTick(event.x).toInt().coerceAtLeast(0)
        tempoPreviewBpm = yToBpm(event.y)
        tempoRampToggled = false
        if (tempoDragFromTick > 0) {
            // Long-press flips the point between step and linear ramp.
            handler.postDelayed(tempoLongPressRunnable, 500L)
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private val tempoLongPressRunnable = Runnable {
        if (mode == Mode.TEMPO_DRAG && tempoDragFromTick > 0 && !gestureMoved && !tempoRampToggled) {
            tempoRampToggled = true
            host?.toggleTempoRamp(tempoDragFromTick)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    private var tempoRampToggled = false

    /** Tick of the node being dragged, -1 while creating a new one. */
    private var tempoDragFromTick = -1
    private var tempoPreviewTick = 0
    private var tempoPreviewBpm = 0.0

    private fun findTempoNodeAt(x: Float, y: Float): TempoPoint? {
        val radius = dp(22f)
        var best: TempoPoint? = null
        var bestDistance = radius * radius
        for (pt in tempoPoints()) {
            val nx = tickToX(pt.tick)
            if (nx < contentLeft - dp(6f) || nx > contentRight + dp(6f)) continue
            val ny = bpmToY(pt.bpm)
            val dx = x - nx
            val dy = y - ny
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = pt
            }
        }
        return best
    }

    private fun updateTempoTouch(x: Float, y: Float) {
        if (gestureMoved) handler.removeCallbacks(tempoLongPressRunnable)
        tempoPreviewTick = xToTick(x).toInt().coerceAtLeast(0)
        tempoPreviewBpm = yToBpm(y)
        invalidate()
    }

    private fun commitTempoTouch() {
        handler.removeCallbacks(tempoLongPressRunnable)
        val from = tempoDragFromTick
        if (tempoRampToggled) {
            // The long-press already flipped the curve; do nothing else.
        } else if (from >= 0) {
            if (gestureMoved) {
                // The base tempo (tick 0) only changes value, never position.
                val target = if (from == 0) 0 else tempoPreviewTick
                host?.moveTempoPoint(from, target, tempoPreviewBpm)
            } else if (from > 0) {
                host?.removeTempoPoint(from)
            }
        } else {
            // Tap on empty lane: place a tempo change here.
            host?.setTempoPoint(tempoPreviewTick, tempoPreviewBpm)
        }
        tempoDragFromTick = -1
        tempoPreviewBpm = 0.0
        tempoRampToggled = false
        invalidate()
    }

    /**
     * Pedal lane touch: tap toggles a segment under the finger, a horizontal
     * drag draws a new segment from the press point to the release point.
     */
    private fun beginSustainTouch(event: MotionEvent) {
        mode = Mode.SUSTAIN_DRAG
        val downTick = xToTick(event.x).toInt().coerceAtLeast(0)
        sustainDragStart = downTick
        sustainPreview = null
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private var sustainDragStart = 0

    private fun updateSustainTouch(x: Float) {
        val p = project ?: return
        val minLen = max(p.ppq / 8, 1)
        val start = sustainDragStart
        val end = xToTick(x).toInt().coerceAtLeast(0)
        val lo = min(start, end)
        val hi = max(start, end) + minLen
        sustainPreview = lo to hi
        invalidate()
    }

    private fun commitSustainTouch() {
        val p = project ?: return
        val preview = sustainPreview
        if (preview != null && gestureMoved) {
            val (lo, hi) = preview
            host?.addSustainSegment(lo, hi)
        } else {
            // Tap: toggle the segment under the finger.
            val tick = sustainDragStart
            val existing = sustainSegmentAt(tick)
            if (existing != null) {
                host?.removeSustainSegment(existing.first)
            } else {
                host?.addSustainSegment(tick, tick + p.ppq)
            }
        }
        sustainPreview = null
        invalidate()
    }

    private fun updateTouch(event: MotionEvent) {
        if (mode == Mode.PINCH) {
            updatePinch(event)
            return
        }
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        val x = event.getX(index)
        val y = event.getY(index)
        lastX = x
        lastY = y
        val dx = x - downX
        val dy = y - downY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance > touchSlop) gestureMoved = true

        when (mode) {
            Mode.PENDING_EMPTY -> {
                if (distance > touchSlop) {
                    cancelLongPressTimer()
                    if (prefs.multiSelectMode && (!prefs.penOnlySelection || isStylus(event))) {
                        beginMarquee(clearExisting = false)
                        updateMarquee(x, y)
                    } else {
                        beginPan()
                        applyPan(dx, dy)
                    }
                }
            }
            Mode.PENDING_NOTE -> {
                if (distance > touchSlop) {
                    cancelLongPressTimer()
                    if (prefs.multiSelectMode) {
                        beginMarquee(clearExisting = false)
                        updateMarquee(x, y)
                    } else {
                        val stylus = isStylus(event)
                        if (!stylus && !prefs.fingerEditing && !prefs.penOnlySelection) {
                            beginPan()
                            applyPan(dx, dy)
                        } else {
                            beginNoteDrag()
                            applyNoteDrag(x, y)
                        }
                    }
                }
            }
            Mode.VELOCITY_DRAG -> applyVelocityDrag(y)
            Mode.SUSTAIN_DRAG -> updateSustainTouch(x)
            Mode.TEMPO_DRAG -> updateTempoTouch(x, y)
            Mode.PAN -> applyPan(dx, dy)
            Mode.DRAW -> {
                val tick = snapIfEnabled(xToTick(x).toInt()).coerceAtLeast(0)
                drawEndTick = max(drawStartTick + 1, tick)
                scrollHintUntil = SystemClock.uptimeMillis() + 350
            }
            Mode.DRAG_MOVE, Mode.DRAG_RESIZE -> applyNoteDrag(x, y)
            Mode.MARQUEE -> updateMarquee(x, y)
            Mode.RULER_SEEK -> host?.seekTo(xToTick(x).coerceAtLeast(0.0))
            Mode.LOOP_DRAG -> updateLoopDrag(x)
            Mode.KEY_PREVIEW -> {
                val pitch = pitchAtY(y)
                if (pitch != keyPreviewPitch) {
                    keyPreviewPitch = pitch
                    host?.previewNote(pitch, prefs.inputVelocity)
                }
            }
            else -> Unit
        }
        invalidate()
    }

    private fun endTouch(event: MotionEvent) {
        cancelLongPressTimer()
        when (mode) {
            Mode.PENDING_EMPTY -> {
                if (prefs.fingerEditing && !prefs.multiSelectMode) {
                    val tick = snapIfEnabled(downTick.toInt()).coerceAtLeast(0)
                    host?.addNoteAt(downPitch, tick, inputDurationTicks())
                }
            }
            Mode.PENDING_NOTE -> {
                val id = downNoteId
                if (id != null) {
                    val current = host?.selection() ?: selectionIds
                    if (prefs.multiSelectMode) {
                        // Tapping toggles the note in the current selection.
                        val next = if (id in current) current - id else current + id
                        host?.setRollSelection(next)
                        selectionIds = next
                        project?.noteById(id)?.let { host?.previewNote(it.pitch, it.velocity) }
                        lastTapNoteId = null
                    } else {
                        if (id !in current) host?.setRollSelection(setOf(id))
                        val now = SystemClock.uptimeMillis()
                        val editAllowed = isStylus(event) || (prefs.fingerEditing && !prefs.penOnlySelection)
                        if (editAllowed &&
                            lastTapNoteId == id &&
                            now - lastTapAt <= 350 &&
                            kotlin.math.sqrt(
                                (downX - lastTapX) * (downX - lastTapX) +
                                    (downY - lastTapY) * (downY - lastTapY)
                            ) < dp(48f)
                        ) {
                            // Double-tap deletes the note. Numeric editing stays
                            // available from the dock and the hover toolbar.
                            host?.deleteNoteByDoubleTap(id)
                            lastTapNoteId = null
                        } else {
                            project?.noteById(id)?.let { host?.previewNote(it.pitch, it.velocity) }
                            lastTapNoteId = id
                            lastTapAt = now
                            lastTapX = downX
                            lastTapY = downY
                        }
                    }
                }
            }
            Mode.VELOCITY_DRAG -> {
                val id = velocityPrimaryId
                val preview = velocityPreview
                if (velocityMoved && preview != null && preview != project?.notes) {
                    host?.commitRollNotes(preview)
                } else if (!velocityMoved && id != null) {
                    val current = host?.selection() ?: selectionIds
                    val next = when {
                        prefs.multiSelectMode ->
                            if (id in current) current - id else current + id
                        id in current && current.size > 1 -> current
                        else -> setOf(id)
                    }
                    host?.setRollSelection(next)
                    selectionIds = next
                    project?.noteById(id)?.let { host?.previewNote(it.pitch, it.velocity) }
                }
                velocityPreview = null
                velocityOriginals = emptyList()
                velocityPrimaryId = null
                velocityDisplayPitch = null
                velocityDisplayVelocity = null
            }
            Mode.SUSTAIN_DRAG -> commitSustainTouch()
            Mode.TEMPO_DRAG -> commitTempoTouch()
            Mode.DRAW -> {
                val start = drawStartTick.coerceAtLeast(0)
                val end = max(start + 1, drawEndTick)
                host?.addNoteAt(drawPitch, start, end - start)
            }
            Mode.DRAG_MOVE, Mode.DRAG_RESIZE -> {
                val preview = dragPreview
                if (preview != null && preview != project?.notes) host?.commitRollNotes(preview)
                dragPreview = null
                publishViewport()
            }
            Mode.PAN -> {
                scrollHintUntil = SystemClock.uptimeMillis() + 900
                publishViewport()
            }
            Mode.MARQUEE -> host?.setRollSelection(marqueeSelection)
            Mode.LOOP_DRAG -> {
                if (loopDragging && gestureMoved) host?.setLoopRange(loopDragStart, loopDragEnd)
                loopDragging = false
            }
            Mode.ERASE -> downNoteId?.let { host?.deleteNotes(setOf(it)) }
            else -> Unit
        }
        // A completed tap (as opposed to a pan, pinch or edit drag) counts as
        // a click for accessibility services.
        if (!gestureMoved && mode != Mode.PAN && mode != Mode.PINCH) performClick()
        mode = Mode.NONE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        keyPreviewPitch = null
        downNoteId = null
        dragOriginals = emptyList()
        dragPrimary = null
        dragPreview = null
        marqueeBaseSelection = emptySet()
        velocityPreview = null
        velocityOriginals = emptyList()
        velocityPrimaryId = null
        velocityDisplayPitch = null
        velocityDisplayVelocity = null
        handler.removeCallbacks(hoverClearRunnable)
        hoverClearScheduled = true
        handler.postDelayed(hoverClearRunnable, 60)
        invalidate()
    }

    private fun cancelGesture() {
        cancelLongPressTimer()
        mode = Mode.NONE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        dragPreview = null
        dragOriginals = emptyList()
        dragPrimary = null
        keyPreviewPitch = null
        loopDragging = false
        downNoteId = null
        marqueeBaseSelection = emptySet()
        velocityPreview = null
        velocityOriginals = emptyList()
        velocityPrimaryId = null
        velocityDisplayPitch = null
        velocityDisplayVelocity = null
        sustainPreview = null
        tempoDragFromTick = -1
        tempoPreviewBpm = 0.0
        tempoRampToggled = false
        handler.removeCallbacks(tempoLongPressRunnable)
        invalidate()
    }

    private fun handlePointerUp(event: MotionEvent) {
        if (mode != Mode.PINCH) return
        val remaining = event.pointerCount - 1
        if (remaining >= 2) {
            val lifted = event.actionIndex
            val indices = (0 until event.pointerCount).filter { it != lifted }.take(2)
            if (indices.size == 2) {
                initPinch(
                    event.getX(indices[0]), event.getY(indices[0]),
                    event.getX(indices[1]), event.getY(indices[1]),
                )
            }
        } else {
            mode = Mode.NONE
            activePointerId = MotionEvent.INVALID_POINTER_ID
            publishViewport()
            invalidate()
        }
    }

    private fun beginPan() {
        mode = Mode.PAN
        panStartTick = startTick
        panStartLowest = lowestPitch
        panAxis = 0
    }

    private fun applyPan(dx: Float, dy: Float) {
        if (panAxis == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
            panAxis = if (abs(dx) >= abs(dy)) 1 else 2
        }
        when (panAxis) {
            1 -> startTick = panStartTick - dx / tickScale.toDouble()
            2 -> lowestPitch = panStartLowest + dy / rowPx
            else -> {
                startTick = panStartTick - dx / tickScale.toDouble()
                lowestPitch = panStartLowest + dy / rowPx
            }
        }
        clampViewport()
        scrollHintUntil = SystemClock.uptimeMillis() + 350
        viewportDirty = true
    }

    private fun beginPinch(event: MotionEvent) {
        if (event.pointerCount < 2) return
        dragPreview = null
        cancelLongPressTimer()
        clearHover(sendToHost = true)
        initPinch(
            event.getX(0), event.getY(0),
            event.getX(1), event.getY(1),
        )
    }

    private fun initPinch(x0: Float, y0: Float, x1: Float, y1: Float) {
        mode = Mode.PINCH
        activePointerId = MotionEvent.INVALID_POINTER_ID
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        pinchStartSpanX = max(abs(x1 - x0), dp(24f))
        pinchStartSpanY = max(abs(y1 - y0), dp(24f))
        pinchStartDpPerTick = dpPerTick
        pinchStartRowHeight = rowHeightDp
        pinchAnchorTick = xToTick(cx)
        pinchAnchorPitch = pitchAtY(cy).toFloat()
        scrollHintUntil = SystemClock.uptimeMillis() + 900
    }

    private fun updatePinch(event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val spanX = max(abs(x1 - x0), dp(24f))
        val spanY = max(abs(y1 - y0), dp(24f))

        val hScale = if (pinchStartSpanX > dp(48f)) {
            (spanX / pinchStartSpanX).coerceIn(0.4f, 2.5f)
        } else 1f
        val vScale = if (pinchStartSpanY > dp(48f)) {
            (spanY / pinchStartSpanY).coerceIn(0.4f, 2.5f)
        } else 1f

        dpPerTick = (pinchStartDpPerTick * hScale).coerceIn(MIN_DP_PER_TICK, MAX_DP_PER_TICK)
        rowHeightDp = (pinchStartRowHeight * vScale).coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)

        startTick = pinchAnchorTick - (cx - contentLeft) / tickScale.toDouble()
        lowestPitch = pinchAnchorPitch - (contentBottom - cy) / rowPx
        clampViewport()
        scrollHintUntil = SystemClock.uptimeMillis() + 700
        viewportDirty = true
    }

    // ------------------------------------------------------------------
    // Note editing
    // ------------------------------------------------------------------

    private fun beginNoteDrag() {
        val p = project ?: return
        val id = downNoteId ?: return
        dragOriginals = p.notes.filter { it.id in activeNoteIds }
        dragPrimary = p.noteById(id)
        if (dragOriginals.isEmpty() || dragPrimary == null) return
        dragDownTick = xToTick(downX)
        dragDownPitch = pitchAtY(downY)
        mode =
            if (downZone == HoverZone.NOTE_START || downZone == HoverZone.NOTE_END)
                Mode.DRAG_RESIZE
            else Mode.DRAG_MOVE
        val current = host?.selection() ?: selectionIds
        if (current != activeNoteIds) host?.setRollSelection(activeNoteIds)
    }

    private fun applyNoteDrag(x: Float, y: Float) {
        val p = project ?: return
        val primary = dragPrimary ?: return
        if (dragOriginals.isEmpty()) return
        val rawTick = xToTick(x)

        if (mode == Mode.DRAG_MOVE) {
            val rawDelta = (rawTick - dragDownTick).roundToInt()
            var newStart = primary.startTick + rawDelta
            if (prefs.snapEnabled) {
                newStart = snapTick(newStart, prefs.grid, p.ppq)
            }
            var deltaTick = newStart - primary.startTick
            val minStart = dragOriginals.minOf { it.startTick }
            if (minStart + deltaTick < 0) deltaTick = -minStart

            var deltaPitch = pitchAtY(y) - dragDownPitch
            val minPitch = dragOriginals.minOf { it.pitch }
            val maxPitch = dragOriginals.maxOf { it.pitch }
            deltaPitch = deltaPitch.coerceIn(-minPitch, 127 - maxPitch)

            val moved = HashMap<Long, Note>(dragOriginals.size * 2)
            dragOriginals.forEach { n ->
                moved[n.id] = n.copy(
                    startTick = (n.startTick + deltaTick).coerceAtLeast(0),
                    pitch = (n.pitch + deltaPitch).coerceIn(0, 127),
                )
            }
            dragPreview = p.notes.map { moved[it.id] ?: it }
            val shown = moved[primary.id]
            if (shown != null) {
                maybePreview(shown.pitch, shown.velocity)
            }
        } else if (mode == Mode.DRAG_RESIZE) {
            val deltaRaw = rawTick - dragDownTick
            val map = HashMap<Long, Note>(dragOriginals.size * 2)
            if (downZone == HoverZone.NOTE_START) {
                var newStart = primary.startTick + deltaRaw.roundToInt()
                if (prefs.snapEnabled) {
                    newStart = snapTick(newStart, prefs.grid, p.ppq).coerceAtLeast(0)
                }
                var delta = newStart - primary.startTick
                var lo = Int.MIN_VALUE
                var hi = Int.MAX_VALUE
                dragOriginals.forEach { n ->
                    lo = max(lo, -n.startTick)
                    hi = min(hi, n.durationTicks - 1)
                }
                if (lo > hi) lo = hi
                delta = delta.coerceIn(lo, hi)
                dragOriginals.forEach { n ->
                    map[n.id] = n.copy(
                        startTick = n.startTick + delta,
                        durationTicks = n.durationTicks - delta,
                    )
                }
            } else {
                var newEnd = primary.endTick + deltaRaw.roundToInt()
                if (prefs.snapEnabled) {
                    newEnd = snapTick(newEnd, prefs.grid, p.ppq)
                }
                var delta = newEnd - primary.endTick
                var minDelta = Int.MIN_VALUE
                dragOriginals.forEach { n ->
                    minDelta = max(minDelta, 1 - n.durationTicks)
                }
                delta = delta.coerceAtLeast(minDelta)
                dragOriginals.forEach { n ->
                    map[n.id] = n.copy(
                        durationTicks = (n.durationTicks + delta).coerceAtLeast(1)
                    )
                }
            }
            dragPreview = p.notes.map { map[it.id] ?: it }
        }
    }

    private fun applyVelocityDrag(y: Float) {
        val p = project ?: return
        if (velocityOriginals.isEmpty()) return
        val dy = y - velocityDownY
        if (!velocityMoved && abs(dy) > touchSlop * 0.5f) velocityMoved = true
        if (!velocityMoved) return
        val delta = velocityDeltaForDy(dy)
        val changed = HashMap<Long, Int>(velocityOriginals.size * 2)
        velocityOriginals.forEach { note ->
            changed[note.id] = (note.velocity + delta).coerceIn(1, 127)
        }
        velocityPreview = p.notes.map { note ->
            val velocity = changed[note.id]
            if (velocity != null && velocity != note.velocity) note.copy(velocity = velocity) else note
        }
        val primary = velocityPreview?.firstOrNull { it.id == velocityPrimaryId } ?: return
        velocityDisplayPitch = primary.pitch
        velocityDisplayVelocity = primary.velocity
        maybePreview(primary.pitch, primary.velocity)
    }

    private fun maybePreview(pitch: Int, velocity: Int) {
        val now = SystemClock.uptimeMillis()
        if (pitch != lastPreviewPitch || now - lastPreviewAt > 130) {
            lastPreviewPitch = pitch
            lastPreviewAt = now
            host?.previewNote(pitch, velocity)
        }
    }

    private fun updateMarquee(x: Float, y: Float) {
        marqueeEndX = x
        marqueeEndY = y
        val p = project ?: return
        val left = min(marqueeStartX, x)
        val right = max(marqueeStartX, x)
        val top = min(marqueeStartY, y)
        val bottom = max(marqueeStartY, y)
        tmpRect.set(left, top, right, bottom)
        val ids = LinkedHashSet<Long>()
        ids.addAll(marqueeBaseSelection)
        p.notes.forEach { note ->
            val noteRect = noteRect(note, RectF())
            if (RectF.intersects(noteRect, tmpRect)) ids.add(note.id)
        }
        if (ids != marqueeSelection) {
            marqueeSelection = ids
            host?.setRollSelection(ids)
        }
    }

    private fun beginMarquee(clearExisting: Boolean) {
        mode = Mode.MARQUEE
        marqueeStartX = downX
        marqueeStartY = downY
        marqueeEndX = downX
        marqueeEndY = downY
        marqueeBaseSelection =
            if (clearExisting) emptySet() else (host?.selection() ?: selectionIds)
        marqueeSelection = marqueeBaseSelection
        if (clearExisting) {
            host?.setRollSelection(emptySet())
            selectionIds = emptySet()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        invalidate()
    }

    private fun beginLoopDrag(x: Float) {
        val p = project ?: return
        val tick = snapIfEnabled(xToTick(x).toInt()).coerceAtLeast(0)
        val startX = tickToX(p.loopStartTick)
        val endX = tickToX(p.loopEndTick)
        val tolerance = dp(20f)
        loopDragHandle = when {
            abs(x - startX) <= tolerance -> 1
            abs(x - endX) <= tolerance -> 2
            else -> 3
        }
        loopDragStart = p.loopStartTick
        loopDragEnd = p.loopEndTick
        if (loopDragHandle == 3) {
            loopDragStart = tick
            loopDragEnd = tick + (p.ppq * 4)
        }
        loopDragging = true
        scrollHintUntil = SystemClock.uptimeMillis() + 600
    }

    private fun updateLoopDrag(x: Float) {
        if (!loopDragging) return
        val tick = snapIfEnabled(xToTick(x).toInt()).coerceAtLeast(0)
        when (loopDragHandle) {
            1 -> loopDragStart = min(tick, loopDragEnd - 1).coerceAtLeast(0)
            2 -> loopDragEnd = max(tick, loopDragStart + 1)
            else -> {
                if (tick >= loopDragStart) {
                    loopDragEnd = max(tick, loopDragStart + 1)
                } else {
                    loopDragEnd = loopDragStart
                    loopDragStart = tick
                }
            }
        }
    }

    private fun onLongPress() {
        if (!isAttachedToWindow) return
        when (mode) {
            Mode.PENDING_EMPTY -> {
                longPressFired = true
                beginMarquee(clearExisting = !prefs.multiSelectMode)
            }
            Mode.PENDING_NOTE -> {
                longPressFired = true
                val id = downNoteId ?: return
                val current = host?.selection() ?: selectionIds
                val next = if (id in current) current - id else current + id
                host?.setRollSelection(next)
                selectionIds = next
                mode = Mode.NONE
                activePointerId = MotionEvent.INVALID_POINTER_ID
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
            else -> Unit
        }
    }

    private fun cancelLongPressTimer() {
        handler.removeCallbacks(longPressRunnable)
    }

    // ------------------------------------------------------------------
    // Hover / stylus preview
    // ------------------------------------------------------------------

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                handleHover(event)
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                scheduleHoverClear()
                return true
            }
        }
        return super.onHoverEvent(event)
    }

    private fun handleHover(event: MotionEvent) {
        val tool = event.getToolType(0)
        val stylus = tool == MotionEvent.TOOL_TYPE_STYLUS ||
            tool == MotionEvent.TOOL_TYPE_ERASER ||
            tool == MotionEvent.TOOL_TYPE_MOUSE
        if (!stylus) {
            clearHover(sendToHost = true)
            return
        }
        if (mode != Mode.NONE) return

        handler.removeCallbacks(hoverClearRunnable)
        hoverClearScheduled = false
        val x = event.x
        val y = event.y
        lastX = x
        lastY = y
        val p = project ?: return

        if (x < contentLeft) {
            hoverPitch = pitchAtY(y).takeIf { y >= topHeight }
            hoverActive = false
            hoverNoteId = null
            hoverZone = HoverZone.EMPTY
            ghostDuration = 0
            host?.onHoverInfo(null)
            lastHoverSignature = emptyList()
            invalidate()
            return
        }
        if (y < topHeight) {
            hoverActive = false
            hoverPitch = null
            hoverNoteId = null
            ghostDuration = 0
            host?.onHoverInfo(null)
            lastHoverSignature = emptyList()
            invalidate()
            return
        }

        val pitch = pitchAtY(y)
        val rawTick = xToTick(x).coerceAtLeast(0.0)
        val snapped = snapIfEnabled(rawTick.toInt()).coerceAtLeast(0)
        val note = findNoteAt(x, y)
        hoverActive = true
        hoverPitch = pitch
        hoverRawTick = rawTick.toInt()
        val previousZone = if (note != null && hoverNoteId == note.id) hoverZone else null

        if (note != null) {
            hoverNoteId = note.id
            hoverZone = zoneFor(note, x, y, previousZone)
            ghostStart = note.startTick
            ghostPitch = note.pitch
            ghostDuration = note.durationTicks
        } else {
            hoverNoteId = null
            hoverZone = HoverZone.EMPTY
            ghostStart = snapped
            ghostPitch = pitch
            ghostDuration = inputDurationTicks()
        }

        val barLabel = meter?.positionAt(rawTick.toInt().coerceAtLeast(0))?.label() ?: "1.1"
        val signature = listOf(
            hoverRawTick,
            hoverPitch,
            hoverZone,
            hoverNoteId,
            ghostStart,
            ghostPitch,
            ghostDuration,
            barLabel,
        )
        if (signature != lastHoverSignature) {
            lastHoverSignature = signature
            val info = HoverInfo(
                xPx = x,
                yPx = y,
                pitch = pitch,
                tick = rawTick.toInt(),
                snappedTick = snapped,
                barBeatLabel = barLabel,
                zone = hoverZone,
                noteId = note?.id,
                notePitch = note?.pitch,
                noteStart = note?.startTick,
                noteDuration = note?.durationTicks,
                ghostStart = ghostStart,
                ghostPitch = ghostPitch,
                ghostDurationTicks = ghostDuration,
                stylus = true,
            )
            host?.onHoverInfo(info)
        }
        invalidate()
    }

    private fun scheduleHoverClear() {
        if (!hoverActive) return
        handler.removeCallbacks(hoverClearRunnable)
        hoverClearScheduled = true
        handler.postDelayed(hoverClearRunnable, 2400)
    }

    private fun clearHover(sendToHost: Boolean) {
        handler.removeCallbacks(hoverClearRunnable)
        hoverClearScheduled = false
        if (hoverActive || hoverPitch != null || ghostDuration != 0) {
            hoverActive = false
            hoverPitch = null
            hoverNoteId = null
            hoverZone = HoverZone.EMPTY
            ghostDuration = 0
            lastHoverSignature = emptyList()
            if (sendToHost) host?.onHoverInfo(null)
            invalidate()
        }
    }

    private fun zoneFor(note: Note, x: Float, y: Float, previous: HoverZone?): HoverZone {
        if (pitchAtY(y) != note.pitch) return HoverZone.EMPTY
        val x1 = tickToX(note.startTick)
        val x2 = tickToX(note.endTick)
        val width = x2 - x1
        val handle = min(resizeHandle, max(dp(10f), width * 0.36f))
        val hysteresis = dp(5f)
        val startEdge = x1 + handle
        val endEdge = x2 - handle
        if (previous == HoverZone.NOTE_START && x <= startEdge + hysteresis) {
            return HoverZone.NOTE_START
        }
        if (previous == HoverZone.NOTE_END && x >= endEdge - hysteresis) {
            return HoverZone.NOTE_END
        }
        return when {
            x <= startEdge -> HoverZone.NOTE_START
            x >= endEdge -> HoverZone.NOTE_END
            else -> HoverZone.NOTE_BODY
        }
    }

    private fun findNoteAt(x: Float, y: Float): Note? {
        val pitch = pitchAtY(y)
        val tolerance = dp(2f)
        val candidates = notesByPitch[pitch] ?: return null
        for (i in candidates.indices.reversed()) {
            val n = candidates[i]
            val x1 = tickToX(n.startTick)
            val x2 = tickToX(n.endTick)
            if (x >= x1 - tolerance && x <= x2 + tolerance) return n
        }
        return null
    }

    private fun isStylus(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_MOUSE
    }

    private fun isEraser(event: MotionEvent): Boolean =
        event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Fill only this view's own rect. Canvas.drawColor would fill the whole
        // clip region, and the interop host no longer clips AndroidViews to
        // their bounds, so drawColor() used to paint over the composables above
        // the roll (top bar) as well.
        fillPaint.color = colBg
        fillPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        val p = project
        if (p == null) {
            textPaint.color = colDim
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.common_loading), width / 2f, height / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        drawRollBackground(canvas)
        canvas.save()
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
        drawGrid(canvas, p)
        drawLoopShade(canvas, p)
        drawNotes(canvas, p)
        drawGhost(canvas, p)
        drawVelocityEditor(canvas, p)
        drawSustainLane(canvas, p)
        drawTempoLane(canvas, p)
        drawPlayheadLine(canvas)
        drawMarquee(canvas)
        drawEmptyHint(canvas, p)
        canvas.restore()

        drawRuler(canvas, p)
        drawLoopLane(canvas, p)
        drawKeyboard(canvas, p)
        drawScrollbars(canvas, p)
        drawDragHud(canvas, p)
        drawCorner(canvas)
    }

    private fun drawRollBackground(canvas: Canvas) {
        fillPaint.color = colRowWhite
        canvas.drawRect(contentLeft, contentTop, contentRight, contentBottom, fillPaint)
        if (contentBottom <= contentTop) return
        val first = floor(lowestPitch).toInt().coerceAtLeast(0)
        val last = ceil(lowestPitch + visibleRows()).toInt().coerceAtMost(128)
        fillPaint.color = colRowBlack
        for (pitch in first until last) {
            if (isBlackKey(pitch)) {
                val top = pitchToTop(pitch)
                canvas.drawRect(contentLeft, top, contentRight, top + rowPx, fillPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas, p: Project) {
        val m = meter ?: return
        val viewStart = startTick
        val viewEnd = startTick + visibleTicks()
        strokePaint.strokeWidth = dp(1f)

        val grid = prefs.grid
        if (grid != GridUnit.OFF) {
            val step = grid.ticks(p.ppq)
            val stepPx = step * tickScale
            if (stepPx >= dp(5f)) {
                var tick = (floor(viewStart / step).toInt() * step).coerceAtLeast(0)
                strokePaint.color = colGrid
                while (tick <= viewEnd && tick < Int.MAX_VALUE - step) {
                    val x = tickToX(tick)
                    if (x >= contentLeft && x <= contentRight) {
                        canvas.drawLine(x, contentTop, x, contentBottom, strokePaint)
                    }
                    tick += step
                }
            }
        }

        var bar = m.barAt(viewStart.toInt().coerceAtLeast(0))
        var guard = 0
        while (guard++ < 400) {
            val barStart = m.barStartTick(bar).coerceAtLeast(0)
            if (barStart > viewEnd) break
            val sig = m.signatureAt(barStart)
            val barTicks = sig.barTicks(p.ppq).coerceAtLeast(1)
            val beatTicks = barTicks / sig.numerator
            if (beatTicks * tickScale >= dp(7f)) {
                strokePaint.color = colBeat
                for (b in 1 until sig.numerator) {
                    val tick = barStart + b * beatTicks
                    val x = tickToX(tick)
                    if (x in contentLeft..contentRight) {
                        canvas.drawLine(x, contentTop, x, contentBottom, strokePaint)
                    }
                }
            }
            val x = tickToX(barStart)
            if (x in contentLeft..contentRight) {
                strokePaint.color = colBar
                strokePaint.strokeWidth = dp(1.4f)
                canvas.drawLine(x, contentTop, x, contentBottom, strokePaint)
                strokePaint.strokeWidth = dp(1f)
            }
            bar++
        }

        if (rowPx >= dp(7f)) {
            val first = floor(lowestPitch).toInt().coerceAtLeast(0)
            val last = ceil(lowestPitch + visibleRows()).toInt().coerceAtMost(128)
            strokePaint.color = colGrid
            for (pitch in first..last) {
                val y = pitchToTop(pitch)
                if (y in contentTop..contentBottom) {
                    canvas.drawLine(contentLeft, y, contentRight, y, strokePaint)
                }
            }
        }
    }

    private fun drawLoopShade(canvas: Canvas, p: Project) {
        val start: Int
        val end: Int
        if (loopDragging) {
            start = loopDragStart
            end = loopDragEnd
        } else {
            start = p.loopStartTick
            end = p.loopEndTick
        }
        if (end <= start) return
        val x1 = tickToX(start)
        val x2 = tickToX(end)
        if (x2 < contentLeft || x1 > contentRight) return
        fillPaint.color = if (p.loopEnabled || loopDragging) {
            withAlpha(colLoop, 0.075f)
        } else {
            withAlpha(colDim, 0.045f)
        }
        canvas.drawRect(x1, contentTop, x2, contentBottom, fillPaint)
    }

    private fun drawNotes(canvas: Canvas, p: Project) {
        val notes = dragPreview ?: p.notes
        val viewStart = startTick - 1
        val viewEnd = startTick + visibleTicks() + 1
        for (note in notes) {
            if (note.endTick < viewStart || note.startTick > viewEnd) continue
            drawNote(canvas, note)
        }
    }

    private fun drawNote(canvas: Canvas, note: Note) {
        val x1 = tickToX(note.startTick)
        var x2 = tickToX(note.endTick)
        if (x2 - x1 < dp(3f)) x2 = x1 + dp(3f)
        if (x2 < contentLeft || x1 > contentRight) return
        val top = pitchToTop(note.pitch)
        val height = rowPx
        val inset = (height * 0.09f).coerceIn(dp(0.5f), dp(2.5f))
        val left = max(x1, contentLeft - dp(2f))
        val right = min(x2, contentRight + dp(2f))
        if (right <= left) return
        tmpRect.set(left, top + inset, right, top + height - inset)
        val radius = min(dp(4f), tmpRect.height() / 2f)
        val selected = note.id in selectionIds
        val hovered = note.id == hoverNoteId

        notePaint.color = if (selected) colSelected else velocityColor(note.velocity)
        canvas.drawRoundRect(tmpRect, radius, radius, notePaint)
        if (selected) {
            noteStroke.color = colText
            noteStroke.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(tmpRect, radius, radius, noteStroke)
        } else if (hovered) {
            noteStroke.color = withAlpha(colText, 0.85f)
            noteStroke.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(tmpRect, radius, radius, noteStroke)
        }

        if (tmpRect.width() > dp(26f) && rowPx >= dp(12f)) {
            noteTextPaint.color = if (selected) 0xFF06201E.toInt() else 0xFF221607.toInt()
            noteTextPaint.textSize = min(sp(9f), rowPx * 0.55f).coerceAtLeast(sp(7f))
            val baseline = tmpRect.centerY() - (noteTextPaint.ascent() + noteTextPaint.descent()) / 2f
            canvas.save()
            canvas.clipRect(tmpRect.left + dp(3f), tmpRect.top, tmpRect.right - dp(2f), tmpRect.bottom)
            val label = if (tmpRect.width() > dp(72f)) {
                "${noteName(note.pitch)}  ${note.velocity}"
            } else {
                noteName(note.pitch)
            }
            canvas.drawText(label, tmpRect.left + dp(4f), baseline, noteTextPaint)
            canvas.restore()
        }

        if (selected || hovered) {
            noteStroke.color = if (selected) colText else colSelected
            noteStroke.strokeWidth = dp(2f)
            val grip = min(dp(3f), tmpRect.width() * 0.2f)
            canvas.drawLine(tmpRect.left + grip, tmpRect.top + dp(2f), tmpRect.left + grip, tmpRect.bottom - dp(2f), noteStroke)
            canvas.drawLine(tmpRect.right - grip, tmpRect.top + dp(2f), tmpRect.right - grip, tmpRect.bottom - dp(2f), noteStroke)
        }
    }

    private fun drawGhost(canvas: Canvas, p: Project) {
        val start: Int
        val pitch: Int
        val duration: Int
        if (mode == Mode.DRAW) {
            start = drawStartTick
            pitch = drawPitch
            duration = max(1, drawEndTick - drawStartTick)
        } else if (hoverActive && hoverNoteId == null && ghostDuration > 0) {
            start = ghostStart
            pitch = ghostPitch
            duration = ghostDuration
        } else {
            return
        }
        val x1 = tickToX(start)
        var x2 = tickToX(start + duration)
        if (x2 - x1 < dp(4f)) x2 = x1 + dp(4f)
        val top = pitchToTop(pitch)
        val inset = (rowPx * 0.09f).coerceIn(dp(0.5f), dp(2.5f))
        tmpRect.set(x1, top + inset, x2, top + rowPx - inset)
        val radius = min(dp(4f), tmpRect.height() / 2f)
        ghostFill.color = withAlpha(colSelected, 0.18f)
        ghostStroke.color = withAlpha(colSelected, 0.95f)
        canvas.drawRoundRect(tmpRect, radius, radius, ghostFill)
        canvas.drawRoundRect(tmpRect, radius, radius, ghostStroke)
        if (tmpRect.width() > dp(34f) && rowPx >= dp(12f)) {
            noteTextPaint.color = colSelected
            noteTextPaint.textSize = min(sp(9f), rowPx * 0.55f).coerceAtLeast(sp(7f))
            val baseline = tmpRect.centerY() - (noteTextPaint.ascent() + noteTextPaint.descent()) / 2f
            canvas.drawText(noteName(pitch), tmpRect.left + dp(4f), baseline, noteTextPaint)
        }
    }

    /**
     * Velocity graph overlaid on the lower part of the roll.
     *
     * Every note contributes one node at its start tick, connected by a line
     * so the dynamic shape of the phrase is readable at a glance. Dragging a
     * node vertically changes its velocity (and the whole selection when
     * several notes are selected).
     */
    /**
     * Sustain pedal (CC64) lane along the bottom edge: teal bars for each
     * pedal-down → pedal-up span. Tap toggles a segment, drag draws one.
     */
    private fun drawSustainLane(canvas: Canvas, p: Project) {
        if (!prefs.showSustainLane) return
        val laneTop = sustainLaneTop
        if (laneTop < contentTop + dp(20f)) return
        val laneBottom = contentBottom - velocityReserved

        fillPaint.color = withAlpha(colPanel, 0.96f)
        canvas.drawRect(contentLeft, laneTop, contentRight, laneBottom, fillPaint)
        strokePaint.color = withAlpha(colSelected, 0.45f)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawLine(contentLeft, laneTop, contentRight, laneTop, strokePaint)

        boldTextPaint.color = withAlpha(colText, 0.85f)
        boldTextPaint.textSize = sp(9f)
        canvas.drawText(context.getString(R.string.roll_label_pedal), contentLeft + dp(8f), laneTop + dp(12f), boldTextPaint)

        // Visible segments plus the in-progress drag preview.
        val segments = ArrayList(sustainSegments())
        if (mode == Mode.SUSTAIN_DRAG) {
            sustainPreview?.let { segments += it }
        }
        val barTop = laneTop + dp(7f)
        val barBottom = laneBottom - dp(7f)
        for ((down, up) in segments) {
            val x0 = tickToX(down)
            val x1 = tickToX(up).coerceAtLeast(x0 + dp(3f))
            if (x1 < contentLeft || x0 > contentRight) continue
            val clampedLeft = maxOf(x0, contentLeft + dp(26f))
            val clampedRight = minOf(x1, contentRight)
            if (clampedRight <= clampedLeft) continue
            fillPaint.color = withAlpha(colSelected, 0.75f)
            val rect = RectF(clampedLeft, barTop, clampedRight, barBottom)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), fillPaint)
            // Pedal-down marker.
            fillPaint.color = withAlpha(colNote, 0.95f)
            canvas.drawRoundRect(
                RectF(clampedLeft, barTop, clampedLeft + dp(4f), barBottom),
                dp(2f), dp(2f), fillPaint,
            )
        }
        if (segments.isEmpty()) {
            smallTextPaint.color = withAlpha(colDim, 0.8f)
            smallTextPaint.textSize = sp(9f)
            canvas.drawText(
                context.getString(R.string.roll_pedal_hint),
                contentLeft + dp(70f),
                laneTop + dp(13.5f),
                smallTextPaint,
            )
        }
    }

    /**
     * Tempo editor lane: a BPM graph over time in the same style as the
     * velocity editor. Tap empty space to place a tempo change, tap a node to
     * remove it, drag a node to move it. Tempo is project-wide.
     */
    private fun drawTempoLane(canvas: Canvas, p: Project) {
        if (!prefs.showTempoLane) return
        val laneTop = tempoLaneTop
        if (laneTop < contentTop + dp(20f)) return
        val laneBottom = tempoLaneBottom

        fillPaint.color = withAlpha(colPanel, 0.94f)
        canvas.drawRect(contentLeft, laneTop, contentRight, laneBottom, fillPaint)
        strokePaint.color = withAlpha(colSelected, 0.45f)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawLine(contentLeft, laneTop, contentRight, laneTop, strokePaint)

        // BPM guides.
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(5f)), 0f)
        smallTextPaint.textAlign = Paint.Align.RIGHT
        smallTextPaint.textSize = sp(8f)
        for (bpm in intArrayOf(240, 180, 120, 60)) {
            val y = bpmToY(bpm.toDouble())
            strokePaint.color = withAlpha(colDim, if (bpm == 240 || bpm == 60) 0.32f else 0.16f)
            canvas.drawLine(contentLeft, y, contentRight, y, strokePaint)
            smallTextPaint.color = withAlpha(colDim, 0.75f)
            canvas.drawText(bpm.toString(), contentRight - dp(6f), y - dp(2f), smallTextPaint)
        }
        strokePaint.pathEffect = null
        smallTextPaint.textAlign = Paint.Align.LEFT

        boldTextPaint.color = withAlpha(colText, 0.9f)
        boldTextPaint.textSize = sp(9f)
        canvas.drawText(context.getString(R.string.roll_label_tempo), contentLeft + dp(8f), laneTop + dp(13f), boldTextPaint)

        val points = tempoPoints()

        // Step line: a tempo holds until the next point, or ramps linearly
        // into it when the next point is marked as a ramp.
        if (points.isNotEmpty()) {
            strokePaint.color = withAlpha(colSelected, 0.9f)
            strokePaint.strokeWidth = dp(2f)
            var prevX = tickToX(points.first().tick).coerceAtLeast(contentLeft)
            var prevY = bpmToY(points.first().bpm)
            for (i in 1 until points.size) {
                val x = tickToX(points[i].tick)
                val y = bpmToY(points[i].bpm)
                val clamped = x.coerceIn(contentLeft, contentRight)
                if (points[i].ramp) {
                    if (clamped > prevX) {
                        canvas.drawLine(prevX, prevY, clamped, y, strokePaint)
                    }
                } else {
                    if (clamped > prevX) {
                        canvas.drawLine(prevX, prevY, clamped, prevY, strokePaint)
                        canvas.drawLine(clamped, prevY, clamped, y, strokePaint)
                    }
                }
                prevX = clamped
                prevY = y
            }
            if (prevX < contentRight) {
                canvas.drawLine(prevX, prevY, contentRight, prevY, strokePaint)
            }
            strokePaint.strokeWidth = dp(1f)
        }

        // Nodes: teal for the tick-0 base tempo, amber for changes. Ramp
        // nodes carry a ring so the curve type is readable at a glance.
        for (pt in points) {
            val nx = tickToX(pt.tick)
            if (nx < contentLeft - dp(6f) || nx > contentRight + dp(6f)) continue
            val ny = bpmToY(pt.bpm)
            fillPaint.color = withAlpha(if (pt.tick == 0) colSelected else colNote, 0.95f)
            canvas.drawCircle(nx, ny, dp(5f), fillPaint)
            strokePaint.color = withAlpha(colBg, 0.9f)
            canvas.drawCircle(nx, ny, dp(5f), strokePaint)
            if (pt.ramp) {
                strokePaint.color = withAlpha(colSelected, 0.9f)
                strokePaint.strokeWidth = dp(1.5f)
                canvas.drawCircle(nx, ny, dp(8f), strokePaint)
                strokePaint.strokeWidth = dp(1f)
            }
        }

        // Drag preview node with its BPM.
        if (mode == Mode.TEMPO_DRAG && tempoPreviewBpm > 0.0) {
            val px = tickToX(if (tempoDragFromTick == 0) 0 else tempoPreviewTick)
            val py = bpmToY(tempoPreviewBpm)
            fillPaint.color = withAlpha(colPlayhead, 0.9f)
            canvas.drawCircle(px, py, dp(6f), fillPaint)
            smallTextPaint.color = withAlpha(colText, 0.95f)
            smallTextPaint.textSize = sp(9f)
            canvas.drawText(
                "${tempoPreviewBpm.roundToInt()}",
                px + dp(9f),
                py - dp(7f),
                smallTextPaint,
            )
        } else if (points.size <= 1) {
            smallTextPaint.color = withAlpha(colDim, 0.85f)
            smallTextPaint.textSize = sp(9.5f)
            canvas.drawText(
                context.getString(R.string.roll_tempo_hint),
                contentLeft + dp(70f),
                laneTop + dp(26f),
                smallTextPaint,
            )
        }
    }

    private fun drawVelocityEditor(canvas: Canvas, p: Project) {
        if (!prefs.velocityEditor) return
        val laneTop = velocityLaneTop
        if (laneTop < contentTop + dp(20f)) return
        val laneBottom = contentBottom

        fillPaint.color = withAlpha(colPanel, 0.94f)
        canvas.drawRect(contentLeft, laneTop, contentRight, laneBottom, fillPaint)
        strokePaint.color = withAlpha(colSelected, 0.45f)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawLine(contentLeft, laneTop, contentRight, laneTop, strokePaint)

        val guides = intArrayOf(127, 96, 64, 32, 1)
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(5f)), 0f)
        smallTextPaint.textAlign = Paint.Align.RIGHT
        smallTextPaint.textSize = sp(8f)
        for (v in guides) {
            val y = velocityToY(v)
            strokePaint.color = withAlpha(colDim, if (v == 127 || v == 1) 0.32f else 0.16f)
            canvas.drawLine(contentLeft, y, contentRight, y, strokePaint)
            smallTextPaint.color = withAlpha(colDim, 0.75f)
            canvas.drawText(v.toString(), contentRight - dp(6f), y - dp(2f), smallTextPaint)
        }
        strokePaint.pathEffect = null
        smallTextPaint.textAlign = Paint.Align.LEFT

        boldTextPaint.color = withAlpha(colText, 0.9f)
        boldTextPaint.textSize = sp(9f)
        canvas.drawText(context.getString(R.string.roll_label_velocity), contentLeft + dp(8f), laneTop + dp(13f), boldTextPaint)

        val notes = (velocityPreview ?: p.notes)
            .filter {
                val x = tickToX(it.startTick)
                x >= contentLeft - dp(2f) && x <= contentRight + dp(2f)
            }
            .sortedWith(compareBy({ it.startTick }, { it.pitch }))

        if (notes.isEmpty()) {
            smallTextPaint.color = withAlpha(colDim, 0.85f)
            smallTextPaint.textSize = sp(9.5f)
            val hint = if (p.notes.isEmpty()) {
                context.getString(R.string.roll_vel_empty_hint)
            } else {
                context.getString(R.string.roll_no_notes)
            }
            canvas.drawText(hint, contentLeft + dp(10f), laneTop + dp(31f), smallTextPaint)
            return
        }

        val firstX = tickToX(notes.first().startTick)
        val lastX = tickToX(notes.last().startTick)
        val floorY = laneBottom - dp(8f)

        // Shaded area under the curve.
        path.reset()
        path.moveTo(firstX, floorY)
        notes.forEach { note ->
            path.lineTo(tickToX(note.startTick), velocityToY(note.velocity))
        }
        path.lineTo(lastX, floorY)
        path.close()
        fillPaint.color = withAlpha(colSelected, 0.10f)
        canvas.drawPath(path, fillPaint)

        // Stems make each node's value easy to compare.
        for (note in notes) {
            val x = tickToX(note.startTick)
            val y = velocityToY(note.velocity)
            strokePaint.color = withAlpha(
                if (note.id in selectionIds) colSelected else colNote,
                0.38f,
            )
            strokePaint.strokeWidth = dp(1.2f)
            canvas.drawLine(x, y, x, floorY, strokePaint)
        }

        // Connected graph line.
        if (notes.size > 1) {
            path.reset()
            notes.forEachIndexed { index, note ->
                val x = tickToX(note.startTick)
                val y = velocityToY(note.velocity)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            strokePaint.color = withAlpha(colSelected, 0.9f)
            strokePaint.strokeWidth = dp(2f)
            canvas.drawPath(path, strokePaint)
        }

        // Nodes (larger and outlined when selected or being dragged).
        for (note in notes) {
            val x = tickToX(note.startTick)
            val y = velocityToY(note.velocity)
            val selected = note.id in selectionIds
            val primary = note.id == velocityPrimaryId
            val radius = if (selected || primary) dp(5.5f) else dp(4f)
            notePaint.color = if (selected || primary) colSelected else colNote
            canvas.drawCircle(x, y, radius, notePaint)
            noteStroke.color = withAlpha(colText, if (primary) 1f else 0.7f)
            noteStroke.strokeWidth = if (primary) dp(2f) else dp(1f)
            canvas.drawCircle(x, y, radius, noteStroke)
        }

        val displayPitch = velocityDisplayPitch
        val displayVelocity = velocityDisplayVelocity
        if (displayPitch != null && displayVelocity != null) {
            val label = "${noteName(displayPitch)}  v$displayVelocity"
            boldTextPaint.textSize = sp(10.5f)
            boldTextPaint.color = colText
            val width = boldTextPaint.measureText(label) + dp(16f)
            val top = laneTop + dp(5f)
            hudRect.set(
                contentRight - width - dp(8f),
                top,
                contentRight - dp(8f),
                top + dp(21f),
            )
            fillPaint.color = withAlpha(0xFF0A0E12.toInt(), 0.94f)
            canvas.drawRoundRect(hudRect, dp(7f), dp(7f), fillPaint)
            strokePaint.color = withAlpha(colSelected, 0.8f)
            strokePaint.strokeWidth = dp(1f)
            canvas.drawRoundRect(hudRect, dp(7f), dp(7f), strokePaint)
            canvas.drawText(
                label,
                hudRect.left + dp(8f),
                hudRect.centerY() - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f,
                boldTextPaint,
            )
        }
    }

    private fun drawPlayheadLine(canvas: Canvas) {
        val tick = host?.playheadTick() ?: 0.0
        val x = tickToX(tick)
        if (x < contentLeft - dp(1f) || x > contentRight + dp(1f)) return
        strokePaint.color = colPlayhead
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(x, contentTop, x, contentBottom, strokePaint)
        strokePaint.strokeWidth = dp(1f)
    }

    private fun drawMarquee(canvas: Canvas) {
        if (mode != Mode.MARQUEE) return
        val left = min(marqueeStartX, marqueeEndX)
        val right = max(marqueeStartX, marqueeEndX)
        val top = min(marqueeStartY, marqueeEndY)
        val bottom = max(marqueeStartY, marqueeEndY)
        tmpRect.set(left, top, right, bottom)
        fillPaint.color = withAlpha(colSelected, 0.12f)
        canvas.drawRect(tmpRect, fillPaint)
        strokePaint.color = withAlpha(colSelected, 0.8f)
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawRect(tmpRect, strokePaint)
        strokePaint.strokeWidth = dp(1f)
    }

    private fun drawRuler(canvas: Canvas, p: Project) {
        fillPaint.color = colPanel
        canvas.drawRect(contentLeft, 0f, contentRight, rulerHeight, fillPaint)
        val m = meter ?: return
        val viewStart = startTick
        val viewEnd = startTick + visibleTicks()
        val firstBar = m.barAt(viewStart.toInt().coerceAtLeast(0))
        var bar = firstBar
        var guard = 0
        val stepPx = max(dp(46f), dp(10f))
        var labelStep = 1
        while (guard++ < 400) {
            val barStart = m.barStartTick(bar).coerceAtLeast(0)
            if (barStart > viewEnd + 1) break
            val barTicks = m.barTickLength(bar).coerceAtLeast(1)
            val widthPx = barTicks * tickScale
            if (widthPx > 0) {
                labelStep = max(1, ceil(stepPx / widthPx).toInt())
            }
            val x = tickToX(barStart)
            if (x in contentLeft..contentRight) {
                if (bar % labelStep == 0) {
                    strokePaint.color = colBar
                    strokePaint.strokeWidth = dp(1.2f)
                    canvas.drawLine(x, rulerHeight * 0.35f, x, rulerHeight, strokePaint)
                    strokePaint.strokeWidth = dp(1f)
                    boldTextPaint.color = colText
                    boldTextPaint.textSize = sp(9.5f)
                    val baseline = rulerHeight * 0.52f - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f
                    canvas.drawText("${bar + 1}", x + dp(3f), baseline, boldTextPaint)
                } else {
                    strokePaint.color = colGrid
                    canvas.drawLine(x, rulerHeight * 0.7f, x, rulerHeight, strokePaint)
                }
            }
            bar++
        }

        strokePaint.color = colBar
        canvas.drawLine(contentLeft, rulerHeight, contentRight, rulerHeight, strokePaint)

        val playX = tickToX(host?.playheadTick() ?: 0.0)
        if (playX in contentLeft..contentRight) {
            fillPaint.color = colPlayhead
            path.reset()
            path.moveTo(playX - dp(5f), 0f)
            path.lineTo(playX + dp(5f), 0f)
            path.lineTo(playX, dp(7f))
            path.close()
            canvas.drawPath(path, fillPaint)
            strokePaint.color = colPlayhead
            strokePaint.strokeWidth = dp(1.5f)
            canvas.drawLine(playX, 0f, playX, rulerHeight, strokePaint)
            strokePaint.strokeWidth = dp(1f)
        }
    }

    private fun drawLoopLane(canvas: Canvas, p: Project) {
        fillPaint.color = colPanelRaised
        canvas.drawRect(contentLeft, rulerHeight, contentRight, topHeight, fillPaint)
        val start: Int
        val end: Int
        if (loopDragging) {
            start = loopDragStart
            end = loopDragEnd
        } else {
            start = p.loopStartTick
            end = p.loopEndTick
        }
        if (end > start) {
            val x1 = tickToX(start)
            val x2 = tickToX(end)
            val left = max(contentLeft, x1)
            val right = min(contentRight, x2)
            if (right > left) {
                loopRect.set(left, rulerHeight + dp(1.5f), right, topHeight - dp(1.5f))
                fillPaint.color = if (p.loopEnabled || loopDragging) {
                    withAlpha(colLoop, 0.85f)
                } else {
                    withAlpha(colFaint, 0.7f)
                }
                canvas.drawRect(loopRect, fillPaint)
                if (x1 in contentLeft..contentRight) {
                    fillPaint.color = if (p.loopEnabled || loopDragging) colSelected else colDim
                    canvas.drawRect(x1 - dp(1.5f), rulerHeight, x1 + dp(1.5f), topHeight, fillPaint)
                }
                if (x2 in contentLeft..contentRight) {
                    fillPaint.color = if (p.loopEnabled || loopDragging) colSelected else colDim
                    canvas.drawRect(x2 - dp(1.5f), rulerHeight, x2 + dp(1.5f), topHeight, fillPaint)
                }
                if (right - left > dp(42f)) {
                    noteTextPaint.color = if (p.loopEnabled || loopDragging) 0xFF04201D.toInt() else colText
                    noteTextPaint.textSize = sp(7.5f)
                    val baseline = rulerHeight + loopLaneHeight / 2f -
                        (noteTextPaint.ascent() + noteTextPaint.descent()) / 2f
                    canvas.drawText("LOOP", left + dp(4f), baseline, noteTextPaint)
                }
            }
        }
        strokePaint.color = colBar
        canvas.drawLine(contentLeft, topHeight, contentRight, topHeight, strokePaint)
    }

    private fun drawKeyboard(canvas: Canvas, p: Project) {
        fillPaint.color = colPanel
        canvas.drawRect(0f, contentTop, contentLeft, contentBottom, fillPaint)
        val first = floor(lowestPitch).toInt().coerceAtLeast(0)
        val last = ceil(lowestPitch + visibleRows()).toInt().coerceAtMost(128)
        val highlight = keyPreviewPitch ?: hoverPitch?.takeIf { hoverActive }
        for (pitch in first until last) {
            val top = pitchToTop(pitch)
            if (top + rowPx < contentTop || top > contentBottom) continue
            val black = isBlackKey(pitch)
            fillPaint.color = if (black) colBlackKey else colWhiteKey
            canvas.drawRect(0f, top, contentLeft, top + rowPx, fillPaint)
            if (pitch == highlight) {
                fillPaint.color = withAlpha(colSelected, 0.45f)
                canvas.drawRect(0f, top, contentLeft, top + rowPx, fillPaint)
            }
            if (!black) {
                strokePaint.color = if (pitch % 12 == 0) colFaint else withAlpha(colFaint, 0.45f)
                canvas.drawLine(0f, top + rowPx, contentLeft, top + rowPx, strokePaint)
            }
            if (pitch % 12 == 0 && rowPx >= dp(13f)) {
                noteTextPaint.color = 0xFF303A45.toInt()
                noteTextPaint.textSize = min(sp(8.5f), rowPx * 0.5f).coerceAtLeast(sp(7f))
                val baseline = top + rowPx / 2f - (noteTextPaint.ascent() + noteTextPaint.descent()) / 2f
                canvas.drawText(noteName(pitch), dp(7f), baseline, noteTextPaint)
            }
        }
        strokePaint.color = colBar
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawLine(contentLeft, contentTop, contentLeft, contentBottom, strokePaint)
        strokePaint.strokeWidth = dp(1f)
    }

    private fun drawCorner(canvas: Canvas) {
        fillPaint.color = colPanel
        canvas.drawRect(0f, 0f, contentLeft, topHeight, fillPaint)
        strokePaint.color = colBar
        canvas.drawLine(0f, topHeight, contentLeft, topHeight, strokePaint)
        canvas.drawLine(contentLeft, 0f, contentLeft, topHeight, strokePaint)
        boldTextPaint.color = colDim
        boldTextPaint.textSize = sp(8.5f)
        boldTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("KEY", contentLeft / 2f, topHeight / 2f -
            (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f, boldTextPaint)
        boldTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawScrollbars(canvas: Canvas, p: Project) {
        val contentW = contentRight - contentLeft
        val contentH = contentBottom - contentTop
        if (contentW <= 0 || contentH <= 0) return
        val alpha = if (SystemClock.uptimeMillis() < scrollHintUntil) 0.55f else 0.22f

        val totalTicks = max(
            p.songEndTick().toDouble() + p.ppq * 16.0,
            startTick + visibleTicks(),
        ).coerceAtLeast(1.0)
        val frac = (visibleTicks() / totalTicks).toFloat().coerceIn(0.03f, 1f)
        val thumbW = (contentW * frac).coerceAtLeast(dp(18f))
        val maxScroll = (totalTicks - visibleTicks()).coerceAtLeast(1.0)
        val scrollFrac = (startTick / maxScroll).toFloat().coerceIn(0f, 1f)
        val thumbX = contentLeft + (contentW - thumbW) * scrollFrac
        val scrollbarBottom =
            if (prefs.velocityEditor) velocityLaneTop - dp(2f) else contentBottom
        fillPaint.color = withAlpha(colText, alpha)
        tmpRect.set(
            thumbX,
            scrollbarBottom - dp(4f),
            thumbX + thumbW,
            scrollbarBottom - dp(1f),
        )
        canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), fillPaint)

        val rows = visibleRows()
        val totalRows = 128f
        val vFrac = (rows / totalRows).coerceIn(0.05f, 1f)
        val thumbH = (contentH * vFrac).coerceAtLeast(dp(18f))
        val maxLow = (128f - rows).coerceAtLeast(1f)
        val vScroll = (lowestPitch / maxLow).coerceIn(0f, 1f)
        val thumbY = contentTop + (contentH - thumbH) * (1f - vScroll)
        fillPaint.color = withAlpha(colText, alpha)
        tmpRect.set(contentRight - dp(4f), thumbY, contentRight - dp(1f), thumbY + thumbH)
        canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), fillPaint)
    }

    private fun drawDragHud(canvas: Canvas, p: Project) {
        if (mode != Mode.DRAW && mode != Mode.DRAG_MOVE && mode != Mode.DRAG_RESIZE) return
        val note = when (mode) {
            Mode.DRAW -> Note(
                id = -1,
                pitch = drawPitch,
                startTick = drawStartTick,
                durationTicks = max(1, drawEndTick - drawStartTick),
                velocity = prefs.inputVelocity,
                channel = p.track.channel,
            )
            else -> dragPreview?.firstOrNull { it.id == downNoteId } ?: dragPrimary
        } ?: return

        val bar = meter?.positionAt(note.startTick)?.label() ?: ""
        val count = if (mode == Mode.DRAG_MOVE || mode == Mode.DRAG_RESIZE) {
            max(1, dragOriginals.size)
        } else 0
        val line1 = if (count > 1) {
            "${noteName(note.pitch)}  v${note.velocity}  (${count})"
        } else {
            "${noteName(note.pitch)}  v${note.velocity}"
        }
        val line2 = "$bar   ${note.durationTicks}t"
        boldTextPaint.textSize = sp(11.5f)
        val textW = max(
            boldTextPaint.measureText(line1),
            smallTextPaint.measureText(line2),
        )
        val hudW = textW + dp(20f)
        val hudH = dp(40f)
        var left = lastX - hudW / 2f
        left = left.coerceIn(contentLeft + dp(4f), max(contentLeft + dp(4f), contentRight - hudW - dp(4f)))
        var top = lastY - hudH - dp(18f)
        if (top < contentTop + dp(4f)) top = lastY + dp(22f)
        top = top.coerceIn(contentTop + dp(4f), max(contentTop + dp(4f), contentBottom - hudH - dp(4f)))
        hudRect.set(left, top, left + hudW, top + hudH)
        fillPaint.color = withAlpha(0xFF0A0E12.toInt(), 0.93f)
        canvas.drawRoundRect(hudRect, dp(8f), dp(8f), fillPaint)
        strokePaint.color = withAlpha(colSelected, 0.7f)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(hudRect, dp(8f), dp(8f), strokePaint)
        boldTextPaint.color = colText
        boldTextPaint.textSize = sp(11.5f)
        canvas.drawText(
            line1,
            hudRect.left + dp(10f),
            hudRect.top + dp(15f) - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f,
            boldTextPaint,
        )
        smallTextPaint.color = colDim
        smallTextPaint.textSize = sp(9.5f)
        canvas.drawText(
            line2,
            hudRect.left + dp(10f),
            hudRect.top + dp(30f) - (smallTextPaint.ascent() + smallTextPaint.descent()) / 2f,
            smallTextPaint,
        )
    }

    private fun drawEmptyHint(canvas: Canvas, p: Project) {
        if (p.notes.isNotEmpty() || dragPreview != null || mode == Mode.DRAW ||
            (hoverActive && hoverNoteId == null && ghostDuration > 0)
        ) return
        val cx = (contentLeft + contentRight) / 2f
        val cy = (contentTop + contentBottom) / 2f
        if (contentRight - contentLeft < dp(180f)) return
        textPaint.color = withAlpha(colDim, 0.85f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(13f)
        canvas.drawText(context.getString(R.string.roll_tap_to_add), cx, cy - dp(8f), textPaint)
        textPaint.textSize = sp(11f)
        textPaint.color = withAlpha(colFaint, 0.95f)
        canvas.drawText(context.getString(R.string.roll_pen_hint), cx, cy + dp(14f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun noteRect(note: Note, out: RectF): RectF {
        val x1 = tickToX(note.startTick)
        val x2 = max(x1 + dp(3f), tickToX(note.endTick))
        val top = pitchToTop(note.pitch)
        out.set(x1, top, x2, top + rowPx)
        return out
    }

    private fun velocityColor(velocity: Int): Int {
        val v = (velocity.coerceIn(1, 127) / 127f)
        val dark = 0xFF8C5B1F.toInt()
        val light = 0xFFF6C77B.toInt()
        return blend(dark, light, 0.25f + v * 0.75f)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return Color.rgb(
            (ar + (br - ar) * f).roundToInt(),
            (ag + (bg - ag) * f).roundToInt(),
            (ab + (bb - ab) * f).roundToInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private companion object {
        const val MIN_DP_PER_TICK = 0.018f
        const val MAX_DP_PER_TICK = 2.2f
        const val MIN_ROW_HEIGHT = 8f
        const val MAX_ROW_HEIGHT = 64f
    }
}
