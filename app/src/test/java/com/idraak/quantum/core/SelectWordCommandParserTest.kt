package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectWordCommandParserTest {
    @Test fun recognizesExplicitWordSelectionCommands() {
        assertTrue(SelectWordCommandParser.matches("select word"))
        assertTrue(SelectWordCommandParser.matches("Select current word."))
        assertTrue(SelectWordCommandParser.matches("لفظ منتخب کرو"))
        assertTrue(SelectWordCommandParser.matches("موجودہ لفظ منتخب کرو"))
        assertTrue(SelectWordCommandParser.matches("शब्द चुनो"))
        assertTrue(SelectWordCommandParser.matches("मौजूदा शब्द चुनो"))
    }

    @Test fun rejectsAmbiguousOrUnrelatedCommands() {
        assertFalse(SelectWordCommandParser.matches("select all"))
        assertFalse(SelectWordCommandParser.matches("delete word"))
        assertFalse(SelectWordCommandParser.matches("move cursor to end"))
        assertFalse(SelectWordCommandParser.matches("select the second word"))
    }
}
