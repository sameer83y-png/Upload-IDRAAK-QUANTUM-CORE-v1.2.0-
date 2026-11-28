package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveCursorByCharacterCommandParserTest {
    @Test fun recognizesLeftAndRightCommands() {
        assertEquals(CursorStep.BACKWARD, MoveCursorByCharacterCommandParser.parse("move cursor left"))
        assertEquals(CursorStep.BACKWARD, MoveCursorByCharacterCommandParser.parse("کرسر ایک حرف پیچھے کرو"))
        assertEquals(CursorStep.BACKWARD, MoveCursorByCharacterCommandParser.parse("कर्सर बाईं ओर ले जाओ"))
        assertEquals(CursorStep.FORWARD, MoveCursorByCharacterCommandParser.parse("move cursor right."))
        assertEquals(CursorStep.FORWARD, MoveCursorByCharacterCommandParser.parse("کرسر ایک حرف آگے کرو"))
        assertEquals(CursorStep.FORWARD, MoveCursorByCharacterCommandParser.parse("कर्सर एक अक्षर आगे करो"))
    }

    @Test fun rejectsAmbiguousCommands() {
        assertNull(MoveCursorByCharacterCommandParser.parse("move cursor"))
        assertNull(MoveCursorByCharacterCommandParser.parse("delete character"))
        assertNull(MoveCursorByCharacterCommandParser.parse("select word"))
    }
}
