package com.idraak.quantum.core

/** Shared size limits for local memory content and correction history. */
internal object MemoryInputPolicy {
    const val MAX_CONTENT_LENGTH = 2000

    fun isValidContentLength(length: Int): Boolean =
        length in 1..MAX_CONTENT_LENGTH
}
