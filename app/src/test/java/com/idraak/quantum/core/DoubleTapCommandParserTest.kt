package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubleTapCommandParserTest {
    @Test fun parsesEnglishAndUrduTargets() {
        assertEquals("photo", DoubleTapCommandParser.parse("double tap photo"))
        assertEquals("heart", DoubleTapCommandParser.parse("tap twice on heart"))
        assertEquals("تصویر", DoubleTapCommandParser.parse("دو بار ٹیپ کرو تصویر"))
    }

    @Test fun rejectsAmbiguousCommands() {
        assertNull(DoubleTapCommandParser.parse("double tap"))
        assertNull(DoubleTapCommandParser.parse("tap photo"))
        assertNull(DoubleTapCommandParser.parse("tap twice"))
    }
}
