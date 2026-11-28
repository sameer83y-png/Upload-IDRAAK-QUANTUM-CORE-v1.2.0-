package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySelectedTextCommandParserTest {
    @Test fun recognizesExplicitEnglishUrduAndHindiRequests() {
        assertTrue(CopySelectedTextCommandParser.matches("copy selected text"))
        assertTrue(CopySelectedTextCommandParser.matches("Copy selection."))
        assertTrue(CopySelectedTextCommandParser.matches("منتخب متن کاپی کرو"))
        assertTrue(CopySelectedTextCommandParser.matches("منتخب کیا ہوا متن کاپی کرو"))
        assertTrue(CopySelectedTextCommandParser.matches("चुना हुआ टेक्स्ट कॉपी करो"))
    }

    @Test fun rejectsUnrelatedOrAmbiguousRequests() {
        assertFalse(CopySelectedTextCommandParser.matches("copy"))
        assertFalse(CopySelectedTextCommandParser.matches("copy all files"))
        assertFalse(CopySelectedTextCommandParser.matches("copy screen text"))
        assertFalse(CopySelectedTextCommandParser.matches("paste clipboard"))
    }
}
