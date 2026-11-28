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

    @Test fun parsesPinchZoomCommands() {
        assertEquals(true, ZoomCommandParser.parse("zoom in"))
        assertEquals(false, ZoomCommandParser.parse("zoom out"))
        assertEquals(true, ZoomCommandParser.parse("زوم اِن کرو"))
        assertEquals(false, ZoomCommandParser.parse("زوم آؤٹ کرو"))
        assertEquals(true, ZoomCommandParser.parse("ज़ूम इन करो"))
        assertEquals(false, ZoomCommandParser.parse("ज़ूम आउट करो"))
    }

    @Test fun rejectsAmbiguousCommands() {
        assertNull(DoubleTapCommandParser.parse("double tap"))
        assertNull(DoubleTapCommandParser.parse("tap photo"))
        assertNull(DoubleTapCommandParser.parse("tap twice"))
    }
}
