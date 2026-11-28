package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteWordCommandParserTest {
    @Test fun recognizesBackwardAndForwardCommands() {
        assertEquals(DeleteDirection.BACKWARD, DeleteWordCommandParser.parse("delete previous word"))
        assertEquals(DeleteDirection.BACKWARD, DeleteWordCommandParser.parse("پچھلا لفظ حذف کرو"))
        assertEquals(DeleteDirection.BACKWARD, DeleteWordCommandParser.parse("पिछला शब्द हटाओ"))
        assertEquals(DeleteDirection.FORWARD, DeleteWordCommandParser.parse("delete next word."))
        assertEquals(DeleteDirection.FORWARD, DeleteWordCommandParser.parse("اگلا لفظ حذف کرو"))
        assertEquals(DeleteDirection.FORWARD, DeleteWordCommandParser.parse("अगला शब्द हटाओ"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedCommands() {
        assertNull(DeleteWordCommandParser.parse("delete"))
        assertNull(DeleteWordCommandParser.parse("delete word"))
        assertNull(DeleteWordCommandParser.parse("clear text field"))
    }
}
