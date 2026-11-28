package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCommandParserTest {
    @Test fun recognizesEnglishUrduAndHindi() {
        assertTrue(ScreenshotCommandParser.matches("take a screenshot"))
        assertTrue(ScreenshotCommandParser.matches("Capture screen"))
        assertTrue(ScreenshotCommandParser.matches("اسکرین شاٹ لے لو"))
        assertTrue(ScreenshotCommandParser.matches("اسکرین کی تصویر محفوظ کرو"))
        assertTrue(ScreenshotCommandParser.matches("स्क्रीनशॉट लें"))
    }

    @Test fun rejectsUnrelatedOrAmbiguousCommands() {
        assertFalse(ScreenshotCommandParser.matches("take a photo"))
        assertFalse(ScreenshotCommandParser.matches("read screen"))
        assertFalse(ScreenshotCommandParser.matches("open camera"))
    }
}
