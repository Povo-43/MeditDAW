package com.medit.daw.audio

/**
 * One playable preset (instrument) inside a SoundFont 2.04 bank.
 */
data class SoundFontPreset(
    val index: Int,
    val name: String,
    val program: Int,
    val bank: Int,
)

/**
 * An immutable parsed SoundFont 2.04 bank, shared by every track that uses
 * the same file. [sampleData] is by far the largest part of a bank, so the
 * global library stores one [SoundFontBank] per SF2 file and hands out cheap
 * [SoundFont] views instead of re-parsing per track.
 *
 * Regions of every playable preset are decoded once by the loader and cached
 * here; switching presets never re-decodes samples.
 */
class SoundFontBank(
    val name: String,
    val sampleData: ShortArray,
    val presets: List<SoundFontPreset>,
) {
    /** Regions of each preset, decoded by the loader. Read across tracks. */
    private val presetRegions = java.util.concurrent.ConcurrentHashMap<Int, List<SfRegion>>()

    /** First preset that produced playable regions, or -1 when none did. */
    internal var firstPlayableIndex: Int = -1

    internal fun regionsForPreset(index: Int): List<SfRegion>? = presetRegions[index]

    internal fun storePresetRegions(index: Int, regions: List<SfRegion>) {
        presetRegions[index] = regions
        if (firstPlayableIndex < 0 && regions.isNotEmpty()) firstPlayableIndex = index
    }

    fun hasPlayablePreset(): Boolean = firstPlayableIndex >= 0
}

/**
 * A per-track view over a shared [SoundFontBank].
 *
 * Each track keeps its own preset selection: [selectPreset] swaps in the
 * bank-cached regions of that preset, so two tracks can play different
 * instruments from the same SF2 at the same time. [regions] holds the
 * playable regions of the currently selected preset and is what the synth
 * reads on every note-on.
 */
class SoundFont(private val bankRef: SoundFontBank) {

    constructor(
        name: String,
        sampleData: ShortArray,
        presets: List<SoundFontPreset>,
    ) : this(SoundFontBank(name, sampleData, presets))

    val bank: SoundFontBank get() = bankRef
    val name: String get() = bankRef.name
    val sampleData: ShortArray get() = bankRef.sampleData
    val presets: List<SoundFontPreset> get() = bankRef.presets

    /** Regions of the currently selected preset. */
    var regions: List<SfRegion> = emptyList()
        private set

    /** Index of the currently selected preset, -1 before the first selection. */
    var presetIndex: Int = -1
        private set

    val regionCount: Int get() = regions.size

    /**
     * Switches the active preset by [presetIndex] and installs its cached
     * regions so the next note-on plays the new instrument. Returns false
     * when the index is out of range.
     */
    fun selectPreset(index: Int): Boolean {
        if (index < 0 || index >= bankRef.presets.size) return false
        presetIndex = index
        regions = bankRef.regionsForPreset(index) ?: emptyList()
        return true
    }

    /** Selects the first preset that has playable regions. */
    fun selectFirstPlayable(): Boolean =
        bankRef.firstPlayableIndex >= 0 && selectPreset(bankRef.firstPlayableIndex)

    /** Caller (the loader) installs the regions decoded for a preset. */
    internal fun setPresetRegions(index: Int, regions: List<SfRegion>) {
        bankRef.storePresetRegions(index, regions)
        if (index == presetIndex) this.regions = regions
    }

    /** Regions decoded for [index], if any. */
    internal fun regionsForPreset(index: Int): List<SfRegion>? =
        bankRef.regionsForPreset(index)

    fun activePreset(): SoundFontPreset? = bankRef.presets.getOrNull(presetIndex)
}
