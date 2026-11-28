package com.idraak.quantum.core

import java.util.Locale

/**
 * Conservative matching for accessibility actions.
 * Exact labels win; partial matches are allowed only when exactly one node matches.
 */
internal object AccessibilityMatchPolicy {
    fun findUniqueMatchIndex(nodeLabels: List<List<String>>, query: String): Int? {
        val wanted = normalize(query)
        if (wanted.isBlank()) return null

        val normalized = nodeLabels.map { labels ->
            labels.map(::normalize).filter(String::isNotBlank)
        }
        val exact = normalized.indices.filter { index ->
            normalized[index].any { it == wanted }
        }
        if (exact.isNotEmpty()) return exact.singleOrNull()

        val partial = normalized.indices.filter { index ->
            normalized[index].any { it.length >= 2 && it.contains(wanted) }
        }
        return partial.singleOrNull()
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
