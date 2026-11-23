package com.medit.daw.audio

/**
 * Tracks RPN / NRPN selection while a controller stream is scanned and
 * translates data-entry (CC6 coarse / CC38 fine) into pitch-bend-range
 * changes when RPN (0, 0) is selected. This is by far the most common RPN;
 * other RPN / NRPN targets are stored but have no audible effect.
 */
internal class BendRangeScanner {

    private var rpnNumber: Int = -1
    private var nrpnActive: Boolean = false
    private var coarseCents: Float = 200f

    /**
     * Feed one controller change. Returns the new bend range in cents when
     * this CC completes an RPN (0,0) data entry, or null otherwise.
     */
    fun feed(controller: Int, value: Int): Float? = when (controller) {
        101 -> { // RPN MSB
            rpnNumber = if (rpnNumber < 0) (value and 0x7F) shl 7 else (rpnNumber and 0x7F) or ((value and 0x7F) shl 7)
            nrpnActive = false
            null
        }
        100 -> { // RPN LSB
            rpnNumber = if (rpnNumber < 0) value and 0x7F else (rpnNumber and 0x3F80) or (value and 0x7F)
            nrpnActive = false
            null
        }
        99, 98 -> { // NRPN MSB / LSB — data entry now targets an NRPN.
            nrpnActive = true
            null
        }
        6 -> { // Data entry coarse.
            if (!nrpnActive && rpnNumber == 0) {
                coarseCents = (value and 0x7F) * 100f
                coarseCents
            } else {
                null
            }
        }
        38 -> { // Data entry fine: adds up to one extra semitone (value/128).
            if (!nrpnActive && rpnNumber == 0) {
                (coarseCents + (value and 0x7F) * 100f / 128f)
            } else {
                null
            }
        }
        else -> null
    }
}
