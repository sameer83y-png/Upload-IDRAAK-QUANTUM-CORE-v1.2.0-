package com.idraak.quantum.core

import java.util.Locale

/** Parses explicit two-finger zoom requests. */
object ZoomCommandParser {
    fun parse(raw: String): Boolean? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "zoom in", "pinch out", "زوم اِن کرو", "زوم ان کرو",
            "زوم بڑھاؤ", "زوم بڑا کرو", "ज़ूम इन करो", "ज़ूम बढ़ाओ" -> true
            "zoom out", "pinch in", "زوم آؤٹ کرو", "زوم آوٹ کرو",
            "زوم کم کرو", "زوم چھوٹا کرو", "ज़ूम आउट करो", "ज़ूम कम करो" -> false
            else -> null
        }
    }
}
