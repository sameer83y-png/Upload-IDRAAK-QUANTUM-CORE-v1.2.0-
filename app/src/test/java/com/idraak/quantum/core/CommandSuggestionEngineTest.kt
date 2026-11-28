package com.idraak.quantum.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSuggestionEngineTest {
    @Test
    fun suggestsSupportedYoutubePhrasesForHindiAndRomanUrdu() {
        val hindi = CommandSuggestionEngine.suggestionsFor("यूट्यूब खोलो")
        val roman = CommandSuggestionEngine.suggestionsFor("youtube kholo")

        assertNotNull(hindi)
        assertTrue(hindi!!.contains("youtube kholo"))
        assertTrue(hindi.contains("youtube par cricket search karo"))
        assertNotNull(roman)
    }

    @Test
    fun suggestsSearchPhraseForUnknownGoogleRequest() {
        val suggestion = CommandSuggestionEngine.suggestionsFor("गूगल पर कुछ खोजो")
        assertNotNull(suggestion)
        assertTrue(suggestion!!.contains("google par latest news search karo"))
    }

    @Test
    fun returnsNullWhenNoKnownCommandFamilyMatches() {
        assertTrue(CommandSuggestionEngine.suggestionsFor("make a sandwich") == null)
    }

    @Test
    fun unknownCommandPolicyUsesRelevantSuggestions() {
        val response = ActionRecoveryPolicy.responseForUnknownCommand(
            "यूट्यूब खोलो",
            "یہ حکم ابھی میرے آف لائن کمانڈز میں شامل نہیں۔ براہ کرم اسے دوسرے الفاظ میں بتائیں۔"
        )
        assertTrue(response.contains("youtube kholo"))
    }
}
