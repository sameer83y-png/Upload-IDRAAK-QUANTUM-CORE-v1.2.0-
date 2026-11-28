package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppsCommandParserTest {
    @Test fun recognizesEnglishUrduAndHindi() {
        assertTrue(RecentAppsCommandParser.matches("open recent apps"))
        assertTrue(RecentAppsCommandParser.matches("app switcher"))
        assertTrue(RecentAppsCommandParser.matches("حالیہ ایپس کھولو"))
        assertTrue(RecentAppsCommandParser.matches("کھلی ایپس دکھاؤ"))
        assertTrue(RecentAppsCommandParser.matches("हाल के ऐप दिखाओ"))
    }

    @Test fun rejectsUnrelatedCommands() {
        assertFalse(RecentAppsCommandParser.matches("open settings"))
        assertFalse(RecentAppsCommandParser.matches("recent"))
    }
}
