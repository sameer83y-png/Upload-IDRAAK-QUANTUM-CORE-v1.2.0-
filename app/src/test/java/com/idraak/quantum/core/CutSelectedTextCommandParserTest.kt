package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutSelectedTextCommandParserTest {
    @Test fun recognizesExplicitEnglishUrduAndHindiRequests() {
        assertTrue(CutSelectedTextCommandParser.matches("cut selected text"))
        assertTrue(CutSelectedTextCommandParser.matches("Cut selection."))
        assertTrue(CutSelectedTextCommandParser.matches("منتخب متن کاٹو"))
        assertTrue(CutSelectedTextCommandParser.matches("منتخب کیا ہوا متن کاٹ دو"))
        assertTrue(CutSelectedTextCommandParser.matches("चुना हुआ टेक्स्ट काटो"))
    }

    @Test fun rejectsUnrelatedOrAmbiguousRequests() {
        assertFalse(CutSelectedTextCommandParser.matches("cut"))
        assertFalse(CutSelectedTextCommandParser.matches("cut the power"))
        assertFalse(CutSelectedTextCommandParser.matches("copy selected text"))
        assertFalse(CutSelectedTextCommandParser.matches("delete all"))
    }
}
