package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteCharacterCommandParserTest {
    @Test fun recognizesBackwardAndForwardCommands() {
        assertEquals(DeleteDirection.BACKWARD, DeleteCharacterCommandParser.parse("delete character"))
        assertEquals(DeleteDirection.BACKWARD, DeleteCharacterCommandParser.parse("پچھلا حرف حذف کرو"))
        assertEquals(DeleteDirection.BACKWARD, DeleteCharacterCommandParser.parse("पिछला अक्षर हटाओ"))
        assertEquals(DeleteDirection.FORWARD, DeleteCharacterCommandParser.parse("delete next character."))
        assertEquals(DeleteDirection.FORWARD, DeleteCharacterCommandParser.parse("اگلا حرف مٹاؤ"))
        assertEquals(DeleteDirection.FORWARD, DeleteCharacterCommandParser.parse("अगला अक्षर मिटाओ"))
    }
    @Test fun rejectsAmbiguousOrBroadCommands() {
        assertNull(DeleteCharacterCommandParser.parse("delete"))
        assertNull(DeleteCharacterCommandParser.parse("delete word"))
        assertNull(DeleteCharacterCommandParser.parse("clear text field"))
    }
}
