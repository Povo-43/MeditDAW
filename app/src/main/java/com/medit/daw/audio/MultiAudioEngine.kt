package com.medit.daw.audio

import android.content.Context
import com.medit.daw.R
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

/** Which SoundFont a track uses: the bundled piano or a file in the library. */
sealed interface Sf2Source {
    data object Bundled : Sf2Source
    data class File(val path: String) : Sf2Source

    /** Library key of this source ("bundled" or the file path). */
    val key: String
        get() = when (this) {
            Bundled -> BUNDLED_KEY
            is File -> path
        }

    companion object {
        const val BUNDLED_KEY = "bundled"

        /** Display name shown in the UI for a source. */
        fun displayName(source: Sf2Source, context: android.content.Context): String = when (source) {
            Bundled -> context.getString(R.string.panel_sound_bundled)
            is Sf2Source.File -> java.io.File(source.path).nameWithoutExtension
        }
    }
}

/** One entry of the app-wide SoundFont library, for the management UI. */
data class LibraryFont(
    val key: String,
    val name: String,
    val presetCount: Int,
    val isBundled: Boolean,
    /** Track indices currently using this font. */
    val usedByTracks: List<Int>,
)

/**
 * One synth per track, so each track can be assigned its own SoundFont (.sf2).
 *
 * Parsed banks live in a global library keyed by source, so the same SF2 file
 * assigned to several tracks is parsed once and its sample data is shared;
 * each track gets a lightweight [SoundFont] view with its own preset state.
 */
class MultiAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _statuses = MutableStateFlow<MutableMap<Int, TrackAudioStatus>>(mutableMapOf())
    val statuses: StateFlow<Map<Int, TrackAudioStatus>> = _statuses.asStateFlow()

    /** Synth per track index. Sparse — null until that track's SF2 is loaded. */
    private val synths = mutableMapOf<Int, PianoSynth>()
    /** Per-track SoundFont view (its own preset state over a shared bank). */
    private val fonts = mutableMapOf<Int, SoundFont>()
    /** Per-track assigned source. */
    private val trackSources = mutableMapOf<Int, Sf2Source>()
    /** Global library: source key -> parsed bank (sample data shared). */
    private val library = java.util.concurrent.ConcurrentHashMap<String, SoundFontBank>()
    /** User volume multiplier (1 = neutral), re-applied whenever a synth is installed. */
    @Volatile private var userVolume: Float = 1f

    private fun statusFor(trackIndex: Int): TrackAudioStatus {
        return _statuses.value.getOrPut(trackIndex) { TrackAudioStatus.Loading }
    }

    private fun postStatus(trackIndex: Int, status: TrackAudioStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(trackIndex, status) }
    }

    // -----------------------------------------------------------------
    // Global library
    // -----------------------------------------------------------------

    /** Parsed bank for [source], parsing it on first use. Blocking — call on IO. */
    private fun bankFor(source: Sf2Source): SoundFontBank =
        library.computeIfAbsent(source.key) { key ->
            when (source) {
                Sf2Source.Bundled -> {
                    val bytes = context.assets.open("piano.sf2").use { it.readBytes() }
                    SoundFontLoader.loadBank(bytes)
                }
                is Sf2Source.File -> SoundFontLoader.loadBank(java.io.File(source.path))
            }
        }

    /** All fonts in the global library with the tracks using them. */
    fun libraryFonts(): List<LibraryFont> {
        val usedBy = synchronized(trackSources) {
            trackSources.entries.groupBy({ it.value.key }, { it.key })
        }
        return library.map { (key, bank) ->
            LibraryFont(
                key = key,
                name = if (key == Sf2Source.BUNDLED_KEY)
                    context.getString(R.string.panel_sound_bundled)
                else bank.name.ifBlank { key.substringAfterLast('/') },
                presetCount = bank.presets.size,
                isBundled = key == Sf2Source.BUNDLED_KEY,
                usedByTracks = usedBy[key].orEmpty().sorted(),
            )
        }
    }

    fun hasFont(key: String): Boolean = library.containsKey(key)

    /** Is a library file still readable / parseable? */
    fun isFileFontValid(path: String): Boolean =
        runCatching { SoundFontLoader.loadBank(java.io.File(path)) }.isSuccess

    /**
     * Removes a file font from the library. Tracks still using it fall back
     * to the bundled piano. Returns the affected track indices.
     */
    fun removeFont(key: String): List<Int> {
        if (key == Sf2Source.BUNDLED_KEY) return emptyList()
        library.remove(key)
        val affected = trackSources.filterValues { it.key == key }.keys.toList()
        for (trackIndex in affected) {
            synths.remove(trackIndex)?.close()
            fonts.remove(trackIndex)
            trackSources.remove(trackIndex)
            loadForTrack(trackIndex, Sf2Source.Bundled)
        }
        return affected
    }

    // -----------------------------------------------------------------
    // Track installation
    // -----------------------------------------------------------------

    private fun installForTrack(trackIndex: Int, source: Sf2Source, presetIndex: Int) {
        val bank = try {
            bankFor(source)
        } catch (t: Throwable) {
            postStatus(
                trackIndex,
                TrackAudioStatus.Failed(trackIndex, context.getString(R.string.msg_sound_load_failed, t.message ?: t.javaClass.simpleName))
            )
            return
        }
        val font = SoundFont(bank)
        val wanted = presetIndex.takeIf { it in bank.presets.indices }
        if (wanted == null || !font.selectPreset(wanted) || font.regions.isEmpty()) {
            font.selectFirstPlayable()
        }
        val newSynth = try {
            PianoSynth(font)
        } catch (t: Throwable) {
            postStatus(trackIndex, TrackAudioStatus.Failed(trackIndex, context.getString(R.string.msg_audio_init_failed, t.message ?: t.javaClass.simpleName)))
            return
        }
        newSynth.setMasterVolume(userVolume)
        synths.remove(trackIndex)?.close()
        synths[trackIndex] = newSynth
        fonts[trackIndex] = font
        trackSources[trackIndex] = source
        postStatus(
            trackIndex,
            TrackAudioStatus.Ready(
                trackIndex,
                Sf2Source.displayName(source, context),
                font.activePreset()?.name ?: "Preset",
                font.regionCount,
            )
        )
    }

    /**
     * Assigns [source] to [trackIndex] and (re)installs its synth with
     * [presetIndex] selected. Re-assigning the source a track already uses
     * only re-applies the preset, never re-parses the bank.
     */
    fun loadForTrack(trackIndex: Int, source: Sf2Source, presetIndex: Int = 0) {
        postStatus(trackIndex, TrackAudioStatus.Loading)
        scope.launch {
            // Same source and a live synth: just re-apply the preset.
            if (trackSources[trackIndex] == source && synths[trackIndex] != null) {
                applyPreset(trackIndex, presetIndex)
                return@launch
            }
            installForTrack(trackIndex, source, presetIndex)
        }
    }

    private fun applyPreset(trackIndex: Int, presetIndex: Int) {
        val font = fonts[trackIndex] ?: return
        val applied = presetIndex.takeIf { it in font.presets.indices }
        if (applied == null || !font.selectPreset(applied) || font.regions.isEmpty()) {
            font.selectFirstPlayable()
        }
        synths[trackIndex]?.selectPreset(font.presetIndex)
        val source = trackSources[trackIndex]
        postStatus(
            trackIndex,
            TrackAudioStatus.Ready(
                trackIndex,
                source?.let { Sf2Source.displayName(it, context) } ?: font.name,
                font.activePreset()?.name ?: "Preset",
                font.regionCount,
            )
        )
    }

    /** Is the synth for [trackIndex] ready to play? */
    fun isReady(trackIndex: Int): Boolean {
        val s = synths[trackIndex]
        return s != null && _statuses.value.getOrDefault(trackIndex, TrackAudioStatus.Loading) is TrackAudioStatus.Ready
    }

    /** The SoundFont view loaded for [trackIndex], if any. */
    fun soundFontFor(trackIndex: Int): SoundFont? = fonts[trackIndex]

    /** The source assigned to [trackIndex], if any. */
    fun trackSource(trackIndex: Int): Sf2Source? = trackSources[trackIndex]

    /** Switches the active preset of the synth for [trackIndex]. */
    fun selectPreset(trackIndex: Int, presetIndex: Int) {
        applyPreset(trackIndex, presetIndex)
    }

    /** Routes a controller change to the track's synth. */
    fun sendCc(trackIndex: Int, controller: Int, value: Int) {
        synths[trackIndex]?.sendCc(controller, value)
    }

    /** 14-bit pitch bend for the track's synth. */
    fun setPitchBend(trackIndex: Int, value14: Int) {
        synths[trackIndex]?.setPitchBend(value14)
    }

    /** Pitch bend range (RPN 0) in cents for the track's synth. */
    fun setBendRangeCents(trackIndex: Int, cents: Float) {
        synths[trackIndex]?.setBendRangeCents(cents)
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
        trackSources.clear()
        _statuses.value = mutableMapOf()
    }
}
