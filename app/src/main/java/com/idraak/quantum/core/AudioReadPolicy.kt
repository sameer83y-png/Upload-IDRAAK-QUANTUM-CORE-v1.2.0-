package com.idraak.quantum.core

/** Shared handling for AudioRecord.read(): zero is a transient empty read, negatives are errors. */
internal object AudioReadPolicy {
    enum class Outcome { RETRY, PROCESS, FAILURE }

    fun classify(readCount: Int): Outcome = when {
        readCount < 0 -> Outcome.FAILURE
        readCount == 0 -> Outcome.RETRY
        else -> Outcome.PROCESS
    }
}
