package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectAllTextCommandParserTest {
    @Test fun recognizesExplicitEnglishUrduAndHindiRequests() {
        assertTrue(SelectAllTextCommandParser.matches("select all"))
        assertTrue(SelectAllTextCommandParser.matches("Select all text."))
        assertTrue(SelectAllTextCommandParser.matches("تمام متن منتخب کرو"))
        assertTrue(SelectAllTextCommandParser.matches("پورا متن سلیکٹ کرو"))
        assertTrue(SelectAllTextCommandParser.matches("पूरा टेक्स्ट सेलेक्ट करो"))
    }

    @Test fun rejectsUnrelatedOrAmbiguousRequests() {
        assertFalse(SelectAllTextCommandParser.matches("select"))
        assertFalse(SelectAllTextCommandParser.matches("select account"))
        assertFalse(SelectAllTextCommandParser.matches("clear text field"))
        assertFalse(SelectAllTextCommandParser.matches("paste clipboard"))
    }
}
