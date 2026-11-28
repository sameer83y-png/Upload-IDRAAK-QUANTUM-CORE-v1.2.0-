package com.idraak.quantum.core

import java.util.Locale

/** Idraak voice output is Urdu-only; never silently switch to another language. */
internal object VoiceLanguagePolicy {
    val supportedLocales: List<Locale>
        get() = listOf(Locale("ur", "PK"))
}
