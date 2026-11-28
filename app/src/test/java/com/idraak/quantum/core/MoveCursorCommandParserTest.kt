package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveCursorCommandParserTest {
    @Test fun recognizesStartAndEndRequestsInSupportedLanguages() {
        assertEquals(CursorPosition.START, MoveCursorCommandParser.parse("move cursor to start"))
        assertEquals(CursorPosition.START, MoveCursorCommandParser.parse("کرسر شروع میں لے جاؤ"))
        assertEquals(CursorPosition.START, MoveCursorCommandParser.parse("कर्सर शुरुआत में ले जाओ"))
        assertEquals(CursorPosition.END, MoveCursorCommandParser.parse("move cursor to end."))
        assertEquals(CursorPosition.END, MoveCursorCommandParser.parse("کرسر آخر میں لے جاؤ"))
        assertEquals(CursorPosition.END, MoveCursorCommandParser.parse("कर्सर अंत में ले जाओ"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedCommands() {
        assertNull(MoveCursorCommandParser.parse("move cursor"))
        assertNull(MoveCursorCommandParser.parse("start typing"))
        assertNull(MoveCursorCommandParser.parse("delete text"))
    }
}
