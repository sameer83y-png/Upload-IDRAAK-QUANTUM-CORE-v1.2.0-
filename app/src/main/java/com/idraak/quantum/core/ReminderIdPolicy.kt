package com.idraak.quantum.core

/** Generates a positive PendingIntent request code not already used by a saved reminder. */
internal object ReminderIdPolicy {
    fun nextId(existingRecords: Collection<String>, candidate: () -> Int): Int {
        val usedIds = existingRecords.mapNotNull { it.substringBefore("|").toIntOrNull() }.toHashSet()
        repeat(100) {
            val id = candidate()
            if (id > 0 && id !in usedIds) return id
        }
        throw IllegalStateException("Could not allocate a unique reminder ID.")
    }
}
