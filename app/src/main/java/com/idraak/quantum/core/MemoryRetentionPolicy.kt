package com.idraak.quantum.core

/** Bounds correction history while preserving the newest user corrections. */
internal object MemoryRetentionPolicy {
    const val MAX_CORRECTIONS = 300

    fun shouldPrune(currentCount: Int): Boolean =
        currentCount > MAX_CORRECTIONS
}
