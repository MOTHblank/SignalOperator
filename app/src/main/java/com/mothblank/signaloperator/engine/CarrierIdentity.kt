package com.mothblank.signaloperator.engine

/**
 * RF fingerprints describe transmitter hardware, not claimed identity. A spoof can
 * therefore reuse a callsign while exposing a different physical carrier profile.
 */
object CarrierIdentity {
    private const val HOSTILE_REPEATER = "RF-7C19"

    fun fingerprint(sender: String, eventId: String?): String {
        if (eventId?.startsWith("deception-") == true) return HOSTILE_REPEATER
        return when (sender.uppercase()) {
            "ECHO-ACTUAL" -> "RF-A17C"
            "ECHO-2" -> "RF-E22B"
            "SECTOR-4-RELAY" -> "RF-S4D8"
            "SYSTEM-KERNEL" -> "RF-K001"
            "HARDWARE-MONITOR" -> "RF-K002"
            "INTEL-RECOVERY" -> "RF-K003"
            else -> "RF-" + Integer.toHexString(sender.hashCode()).takeLast(4).uppercase()
        }
    }
}
