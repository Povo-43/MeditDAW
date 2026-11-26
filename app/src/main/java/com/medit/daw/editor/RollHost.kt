package com.medit.daw.editor

import com.medit.daw.data.EditorPrefs
import com.medit.daw.data.ViewportState
import com.medit.daw.model.Note
import com.medit.daw.model.Project

/**
 * Everything the custom piano roll needs from the editor. Keeping this narrow
 * (instead of passing the ViewModel around) makes the view testable and stops
 * it from reaching into editor internals.
 */
interface RollHost {
    fun project(): Project?
    fun prefs(): EditorPrefs
    fun selection(): Set<Long>
    fun playheadTick(): Double
    fun isPlaying(): Boolean
    fun isAudioReady(): Boolean
    fun noteById(id: Long): Note?
    fun previewNote(pitch: Int, velocity: Int)
    fun addNoteAt(pitch: Int, startTick: Int, durationTicks: Int): Long?
    fun commitRollNotes(newNotes: List<Note>)
    fun setRollSelection(ids: Set<Long>)
    fun requestNoteInspector(id: Long)
    fun deleteNoteByDoubleTap(id: Long)
    fun deleteNotes(ids: Set<Long>)
    fun seekTo(tick: Double)
    fun setLoopRange(startTick: Int, endTick: Int)
    fun onViewportSettled(viewport: ViewportState)
    fun onHoverInfo(info: HoverInfo?)
    /** Adds a sustain-pedal segment (CC64 down at start, up at end). */
    fun addSustainSegment(startTick: Int, endTick: Int)
    /** Removes the sustain segment whose pedal-down event sits at [downTick]. */
    fun removeSustainSegment(downTick: Int)
    /** Adds or replaces the project-wide tempo change at [tick]. */
    fun setTempoPoint(tick: Int, bpm: Double)
    /** Removes the project-wide tempo change at [tick]. */
    fun removeTempoPoint(tick: Int)
    /** Moves the tempo change at [fromTick] to [toTick] with a new [bpm]. */
    fun moveTempoPoint(fromTick: Int, toTick: Int, bpm: Double)
    /** Toggles a tempo change between step (jump) and linear ramp. */
    fun toggleTempoRamp(tick: Int)
}
