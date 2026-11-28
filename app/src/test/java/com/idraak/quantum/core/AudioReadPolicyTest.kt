package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioReadPolicyTest {
    @Test
    fun negativeReadCountIsFailure() {
        assertEquals(AudioReadPolicy.Outcome.FAILURE, AudioReadPolicy.classify(-1))
        assertEquals(AudioReadPolicy.Outcome.FAILURE, AudioReadPolicy.classify(-3))
    }

    @Test
    fun zeroReadCountIsRetriedWithoutTreatingItAsFailure() {
        assertEquals(AudioReadPolicy.Outcome.RETRY, AudioReadPolicy.classify(0))
    }

    @Test
    fun positiveReadCountCanBeProcessed() {
        assertEquals(AudioReadPolicy.Outcome.PROCESS, AudioReadPolicy.classify(1))
        assertEquals(AudioReadPolicy.Outcome.PROCESS, AudioReadPolicy.classify(1600))
    }
}
