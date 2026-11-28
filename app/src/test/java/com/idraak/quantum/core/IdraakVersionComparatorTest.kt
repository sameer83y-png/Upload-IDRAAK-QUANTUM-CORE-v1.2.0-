package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdraakVersionComparatorTest {
    @Test
    fun comparesNumericReleaseSegments() {
        assertTrue(IdraakVersionComparator.isNewer("1.2.1", "1.2.0"))
        assertTrue(IdraakVersionComparator.isNewer("2.0.0", "1.99.99"))
        assertFalse(IdraakVersionComparator.isNewer("1.2.0", "1.2.0"))
    }

    @Test
    fun stableReleaseIsNewerThanItsPrerelease() {
        assertTrue(IdraakVersionComparator.isNewer("1.2.0", "1.2.0-rc.1"))
        assertFalse(IdraakVersionComparator.isNewer("1.2.0-beta.2", "1.2.0"))
    }

    @Test
    fun comparesPrereleaseIdentifiers() {
        assertTrue(IdraakVersionComparator.isNewer("1.2.0-rc.2", "1.2.0-rc.1"))
        assertTrue(IdraakVersionComparator.isNewer("1.2.0-rc.10", "1.2.0-rc.2"))
        assertFalse(IdraakVersionComparator.isNewer("1.2.0-alpha", "1.2.0-beta"))
    }

    @Test
    fun ignoresBuildMetadataAndFailsClosedOnInvalidVersions() {
        assertFalse(IdraakVersionComparator.isNewer("v1.2.0+build.2", "1.2.0"))
        assertFalse(IdraakVersionComparator.isNewer("latest", "1.2.0"))
        assertFalse(IdraakVersionComparator.isNewer("1.3.0", "unknown"))
    }
}
