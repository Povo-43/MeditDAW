package com.medit.daw.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface TrackAudioStatus {
    data object Loading : TrackAudioStatus
    data class Ready(val trackIndex: Int, val fontName: String, val presetName: String, val regionCount: Int) : TrackAudioStatus
    data class Failed(val trackIndex: Int, val message: String) : TrackAudioStatus
}

/**
 * One synth per track, so each track can be assigned its own SoundFont (.sf2).
 * Track index 0 is the default track and loads the bundled piano.sf2 automatically.
 */
class MultiAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _statuses = MutableStateFlow<MutableMap<Int, TrackAudioStatus>>(mutableMapOf())
    val statuses: StateFlow<Map<Int, TrackAudioStatus>> = _statuses.asStateFlow()

    /** Synth per track index. Sparse — null until that track's SF2 is loaded. */
    private val synths = mutableMapOf<Int, PianoSynth>()
    private val fonts = mutableMapOf<Int, SoundFont>()
    /** User volume multiplier (1 = neutral), re-applied whenever a synth is installed. */
    @Volatile private var userVolume: Float = 1f

    private fun statusFor(trackIndex: Int): TrackAudioStatus {
        return _statuses.value.getOrPut(trackIndex) { TrackAudioStatus.Loading }
    }

    private fun postStatus(trackIndex: Int, status: TrackAudioStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(trackIndex, status) }
    }

    private fun installForTrack(trackIndex: Int, font: SoundFont) {
        val newSynth = try {
            PianoSynth(font)
        } catch (t: Throwable) {
            postStatus(trackIndex, TrackAudioStatus.Failed(trackIndex, "オーディオを初期化できません: ${t.message ?: t.javaClass.simpleName}"))
            return
        }
        newSynth.setMasterVolume(userVolume)
        synths[trackIndex] = newSynth
        fonts[trackIndex] = font
        val name = font.name
        val preset = font.activePreset()?.name ?: "Preset 0"
        val regions = font.regionCount
        postStatus(trackIndex, TrackAudioStatus.Ready(trackIndex, name, preset, regions))
    }

    /** Load the bundled piano.sf2 for the given track. */
    fun loadBundledForTrack(trackIndex: Int) {
        postStatus(trackIndex, TrackAudioStatus.Loading)
        scope.launch {
            try {
                val bytes = context.assets.open("piano.sf2").use { it.readBytes() }
                installForTrack(trackIndex, SoundFontLoader.load(bytes))
            } catch (t: Throwable) {
                postStatus(trackIndex, TrackAudioStatus.Failed(trackIndex, "音源を読み込めません: ${t.message ?: t.javaClass.simpleName}"))
            }
        }
    }

    /** Load an external .sf2 for the given track. */
    fun loadExternalForTrack(trackIndex: Int, file: File) {
        postStatus(trackIndex, TrackAudioStatus.Loading)
        scope.launch {
            try {
                installForTrack(trackIndex, SoundFontLoader.load(file))
            } catch (t: Throwable) {
                postStatus(trackIndex, TrackAudioStatus.Failed(trackIndex, "SoundFontを読み込めません: ${t.message ?: t.javaClass.simpleName}"))
            }
        }
    }

    /** Is the synth for [trackIndex] ready to play? */
    fun isReady(trackIndex: Int): Boolean {
        val s = synths[trackIndex]
        return s != null && _statuses.value.getOrDefault(trackIndex, TrackAudioStatus.Loading) is TrackAudioStatus.Ready
    }

    /** The SoundFont loaded for [trackIndex], if any. */
    fun soundFontFor(trackIndex: Int): SoundFont? = fonts[trackIndex]

    /** Switches the active preset of the synth for [trackIndex]. */
    fun selectPreset(trackIndex: Int, presetIndex: Int) {
        synths[trackIndex]?.selectPreset(presetIndex)
    }

    fun noteOn(trackIndex: Int, pitch: Int, velocity: Int, channel: Int = 0) {
        synths[trackIndex]?.noteOn(pitch, velocity, channel)
    }

    fun noteOff(trackIndex: Int, pitch: Int, channel: Int = 0) {
        synths[trackIndex]?.noteOff(pitch, channel)
    }

    fun setSustain(trackIndex: Int, on: Boolean) {
        synths[trackIndex]?.setSustain(on)
    }

    /** Sustain on/off for all tracks. */
    fun setSustainAll(on: Boolean) {
        synths.values.forEach { it.setSustain(on) }
    }

    fun allNotesOff(trackIndex: Int, fast: Boolean = false) {
        synths[trackIndex]?.allNotesOff(fast)
    }

    /** All notes off on all tracks. */
    fun allNotesOffAll(fast: Boolean = false) {
        synths.values.forEach { it.allNotesOff(fast) }
    }

    fun setVolume(value: Float) {
        userVolume = value.coerceIn(0f, 4f)
        synths.values.forEach { it.setMasterVolume(userVolume) }
    }

    fun currentUserVolume(): Float = userVolume

    fun errorMessage(trackIndex: Int): String? = synths[trackIndex]?.errorMessage()

    fun shutdown() {
        synths.values.forEach { it.close() }
        synths.clear()
        fonts.clear()
        _statuses.value = mutableMapOf()
    }
}
