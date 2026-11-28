package com.idraak.quantum.core

/** Small, fail-closed SemVer comparison for official update tags. */
object IdraakVersionComparator {
    private data class ParsedVersion(
        val core: List<Long>,
        val preRelease: List<String>?
    )

    fun isNewer(candidate: String, installed: String): Boolean {
        val left = parse(candidate) ?: return false
        val right = parse(installed) ?: return false

        for (index in 0 until maxOf(left.core.size, right.core.size)) {
            val a = left.core.getOrElse(index) { 0L }
            val b = right.core.getOrElse(index) { 0L }
            if (a != b) return a > b
        }

        val candidatePre = left.preRelease
        val installedPre = right.preRelease
        if (candidatePre == null) return installedPre != null
        if (installedPre == null) return false

        for (index in 0 until maxOf(candidatePre.size, installedPre.size)) {
            val a = candidatePre.getOrNull(index) ?: return false
            val b = installedPre.getOrNull(index) ?: return true
            if (a == b) continue
            val aNumeric = a.all(Char::isDigit)
            val bNumeric = b.all(Char::isDigit)
            return when {
                aNumeric && bNumeric -> compareNumericIdentifiers(a, b) > 0
                aNumeric -> false
                bNumeric -> true
                else -> a > b
            }
        }
        return false
    }

    private fun parse(raw: String): ParsedVersion? {
        val value = raw.trim().removePrefix("v").substringBefore('+')
        if (value.isBlank()) return null
        val pieces = value.split('-', limit = 2)
        val core = pieces[0].split('.').map { part ->
            if (part.isEmpty() || !part.all(Char::isDigit)) return null
            part.toLongOrNull() ?: return null
        }
        if (core.isEmpty()) return null
        val preRelease = pieces.getOrNull(1)?.let { suffix ->
            if (suffix.isBlank()) return null
            val identifiers = suffix.split('.')
            if (identifiers.any { it.isEmpty() || !it.all { ch -> ch.isLetterOrDigit() || ch == '-' } }) {
                return null
            }
            identifiers
        }
        return ParsedVersion(core, preRelease)
    }

    private fun compareNumericIdentifiers(left: String, right: String): Int {
        val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
        val normalizedRight = right.trimStart('0').ifEmpty { "0" }
        if (normalizedLeft.length != normalizedRight.length) {
            return normalizedLeft.length.compareTo(normalizedRight.length)
        }
        return normalizedLeft.compareTo(normalizedRight)
    }
}
