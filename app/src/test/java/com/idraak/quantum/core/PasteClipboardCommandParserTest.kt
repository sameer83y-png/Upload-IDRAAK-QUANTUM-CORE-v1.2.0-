package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteClipboardCommandParserTest {
    @Test fun recognizesEnglishUrduAndHindi() {
        assertTrue(PasteClipboardCommandParser.matches("paste clipboard"))
        assertTrue(PasteClipboardCommandParser.matches("Paste from clipboard."))
        assertTrue(PasteClipboardCommandParser.matches("کلپ بورڈ سے پیسٹ کرو"))
        assertTrue(PasteClipboardCommandParser.matches("کاپی کیا ہوا متن پیسٹ کرو"))
        assertTrue(PasteClipboardCommandParser.matches("क्लिपबोर्ड से पेस्ट करो"))
    }

    @Test fun rejectsUnrelatedOrAmbiguousCommands() {
        assertFalse(PasteClipboardCommandParser.matches("paste"))
        assertFalse(PasteClipboardCommandParser.matches("copy screen text"))
        assertFalse(PasteClipboardCommandParser.matches("paste my password"))
    }
}
