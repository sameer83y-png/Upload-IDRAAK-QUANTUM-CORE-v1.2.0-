package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectToEdgeCommandParserTest {
    @Test fun recognizesStartAndEndSelectionInSupportedLanguages() {
        assertEquals(SelectionEdge.START, SelectToEdgeCommandParser.parse("select from cursor to start"))
        assertEquals(SelectionEdge.START, SelectToEdgeCommandParser.parse("کرسر سے شروع تک منتخب کرو"))
        assertEquals(SelectionEdge.START, SelectToEdgeCommandParser.parse("कर्सर से शुरुआत तक चुनो"))
        assertEquals(SelectionEdge.END, SelectToEdgeCommandParser.parse("select from cursor to end."))
        assertEquals(SelectionEdge.END, SelectToEdgeCommandParser.parse("کرسر سے آخر تک منتخب کرو"))
        assertEquals(SelectionEdge.END, SelectToEdgeCommandParser.parse("कर्सर से अंत तक चुनो"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedCommands() {
        assertNull(SelectToEdgeCommandParser.parse("select text"))
        assertNull(SelectToEdgeCommandParser.parse("move cursor to end"))
        assertNull(SelectToEdgeCommandParser.parse("delete text"))
    }
}
