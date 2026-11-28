package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveCursorByWordCommandParserTest {
    @Test fun recognizesBackwardAndForwardCommands() {
        assertEquals(WordCursorDirection.BACKWARD, MoveCursorByWordCommandParser.parse("move cursor one word left"))
        assertEquals(WordCursorDirection.BACKWARD, MoveCursorByWordCommandParser.parse("کرسر ایک لفظ پیچھے کرو"))
        assertEquals(WordCursorDirection.BACKWARD, MoveCursorByWordCommandParser.parse("कर्सर एक शब्द पीछे करो"))
        assertEquals(WordCursorDirection.FORWARD, MoveCursorByWordCommandParser.parse("move cursor one word right."))
        assertEquals(WordCursorDirection.FORWARD, MoveCursorByWordCommandParser.parse("کرسر ایک لفظ آگے کرو"))
        assertEquals(WordCursorDirection.FORWARD, MoveCursorByWordCommandParser.parse("कर्सर एक शब्द आगे करो"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedCommands() {
        assertNull(MoveCursorByWordCommandParser.parse("move cursor"))
        assertNull(MoveCursorByWordCommandParser.parse("move cursor right"))
        assertNull(MoveCursorByWordCommandParser.parse("delete word"))
    }
}
