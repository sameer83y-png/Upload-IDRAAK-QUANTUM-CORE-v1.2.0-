package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLanguagePolicyTest {
    @Test
    fun supportsOnlyUrduPakistanVoiceOutput() {
        assertEquals(listOf("ur-PK"), VoiceLanguagePolicy.supportedLocales.map {
            "${it.language}-${it.country}"
        })
    }
}
