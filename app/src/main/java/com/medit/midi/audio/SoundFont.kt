package com.medit.midi.audio

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
 * A parsed SoundFont 2.04 instrument bank with all of its presets.
 *
 * [regions] holds the playable regions of the currently selected preset and
 * can be switched with [selectPreset]. [sampleData] is shared by every
 * preset of the bank so switching presets never re-decodes samples.
 */
class SoundFont(
    val name: String,
    val sampleData: ShortArray,
    val presets: List<SoundFontPreset>,
) {
    /** Regions of the currently selected preset. */
    var regions: List<SfRegion> = emptyList()
        private set

    /** Index of the currently selected preset. */
    var presetIndex: Int = 0
        private set

    /** Regions of each preset, populated by the loader. */
    private val presetRegions = mutableMapOf<Int, List<SfRegion>>()

    val regionCount: Int get() = regions.size

    /**
     * Switches the active preset by [presetIndex]. Returns false when the
     * index is out of range. The synth's voices keep using the old regions
     * until the next note-on, so there are no audible clicks mid-note.
     */
    fun selectPreset(index: Int): Boolean {
        if (index < 0 || index >= presets.size) return false
        presetIndex = index
        return true
    }

    /** Caller (the synth) installs the regions decoded for a preset. */
    internal fun setPresetRegions(index: Int, regions: List<SfRegion>) {
        presetRegions[index] = regions
        if (index == presetIndex) this.regions = regions
    }

    /** Regions decoded for [index], if any. */
    internal fun regionsForPreset(index: Int): List<SfRegion>? = presetRegions[index]

    fun activePreset(): SoundFontPreset? = presets.getOrNull(presetIndex)
}
