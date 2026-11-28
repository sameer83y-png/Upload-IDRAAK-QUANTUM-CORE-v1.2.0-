package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomCommandParserTest {
    @Test fun parsesEnglishUrduAndHindi() {
        assertEquals(true, ZoomCommandParser.parse("zoom in"))
        assertEquals(false, ZoomCommandParser.parse("zoom out"))
        assertEquals(true, ZoomCommandParser.parse("زوم اِن کرو"))
        assertEquals(false, ZoomCommandParser.parse("زوم آؤٹ کرو"))
        assertEquals(true, ZoomCommandParser.parse("ज़ूम इन करो"))
        assertEquals(false, ZoomCommandParser.parse("ज़ूम आउट करो"))
    }

    @Test fun rejectsUnclearCommands() {
        assertNull(ZoomCommandParser.parse("zoom"))
        assertNull(ZoomCommandParser.parse("make bigger"))
    }
}
