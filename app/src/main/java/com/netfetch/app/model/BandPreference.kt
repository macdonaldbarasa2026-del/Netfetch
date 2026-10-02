package com.netfetch.app.model

enum class BandPreference(val displayName: String, val frequencyBand: Int) {
    AUTO("Auto (Best Available)", 0),
    BAND_2GHZ("2.4 GHz (Longer Range)", 1),
    BAND_5GHZ("5 GHz (Faster Speed)", 2);

    companion object {
        fun fromOrdinal(ordinal: Int): BandPreference {
            return entries.getOrElse(ordinal) { AUTO }
        }
    }
}
