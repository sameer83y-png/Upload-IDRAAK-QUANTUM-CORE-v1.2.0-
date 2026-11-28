package com.idraak.quantum.core

import org.junit.Assert.assertEquals
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

    @Test
    fun dashboardSuggestionsChangeWithTimeAndAccessibility() {
        val morning = CommandSuggestionEngine.dashboardSuggestions(8, false, emptyList())
        val night = CommandSuggestionEngine.dashboardSuggestions(23, false, emptyList())
        val earlyEvening = CommandSuggestionEngine.dashboardSuggestions(17, false, emptyList())
        val lateEvening = CommandSuggestionEngine.dashboardSuggestions(21, false, emptyList())
        val withAccessibility = CommandSuggestionEngine.dashboardSuggestions(14, true, emptyList())

        assertTrue(morning.first().suggestions.first().command.contains("یاد دلانا"))
        assertTrue(night.first().suggestions.first().command.contains("ساؤنڈ"))
        assertEquals("الارم 19:30 لگاؤ", earlyEvening.first().suggestions.first().command)
        assertEquals("الارم 22:00 لگاؤ", lateEvening.first().suggestions.first().command)
        assertTrue(withAccessibility.any { it.title == "اسکرین کنٹرول" })
    }

    @Test
    fun dashboardIncludesOnlyConnectedAppLabelsAndLimitsTheList() {
        val categories = CommandSuggestionEngine.dashboardSuggestions(
            12,
            false,
            listOf("YouTube", "Chrome", "YouTube", "", "Maps", "Gmail", "Instagram", "Telegram")
        )
        val apps = categories.single { it.title == "آپ کی منسلک ایپس" }.suggestions

        assertEquals(5, apps.size)
        assertEquals("open YouTube", apps.first().command)
        assertTrue(apps.none { it.title.isBlank() })
    }
}
