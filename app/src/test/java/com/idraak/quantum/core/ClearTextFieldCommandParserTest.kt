package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearTextFieldCommandParserTest {
    @Test fun recognizesExplicitEnglishUrduAndHindiRequests() {
        assertTrue(ClearTextFieldCommandParser.matches("clear text field"))
        assertTrue(ClearTextFieldCommandParser.matches("Clear current field."))
        assertTrue(ClearTextFieldCommandParser.matches("موجودہ ٹیکسٹ فیلڈ صاف کرو"))
        assertTrue(ClearTextFieldCommandParser.matches("منتخب فیلڈ کا متن صاف کرو"))
        assertTrue(ClearTextFieldCommandParser.matches("मौजूदा टेक्स्ट फ़ील्ड साफ करो"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedRequests() {
        assertFalse(ClearTextFieldCommandParser.matches("clear"))
        assertFalse(ClearTextFieldCommandParser.matches("clear password"))
        assertFalse(ClearTextFieldCommandParser.matches("delete my account"))
        assertFalse(ClearTextFieldCommandParser.matches("paste clipboard"))
    }
}
