package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedPackagePolicyTest {
    @Test
    fun removesConnectionsForAppsThatAreNoLongerInstalled() {
        val connected = setOf("com.example.keep", "com.example.removed")
        val installed = setOf("com.example.keep", "com.example.other")

        assertEquals(
            setOf("com.example.keep"),
            ConnectedPackagePolicy.retainInstalled(connected, installed)
        )
    }

    @Test
    fun keepsAllConnectionsWhenEveryAppIsInstalled() {
        val connected = setOf("com.example.one", "com.example.two")

        assertEquals(
            connected,
            ConnectedPackagePolicy.retainInstalled(connected, connected)
        )
    }

    @Test
    fun emptyInstalledSetClearsAllStaleConnections() {
        assertEquals(
            emptySet<String>(),
            ConnectedPackagePolicy.retainInstalled(setOf("com.example.old"), emptySet())
        )
    }

    @Test
    fun doesNotAddAppsThatWereNeverConnected() {
        assertEquals(
            setOf("com.example.connected"),
            ConnectedPackagePolicy.retainInstalled(
                setOf("com.example.connected"),
                setOf("com.example.connected", "com.example.not-connected")
            )
        )
    }
}
