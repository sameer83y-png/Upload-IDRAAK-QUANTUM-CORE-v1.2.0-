package com.idraak.quantum.core

/** Pure policy for preventing stale connections to apps no longer installed. */
object ConnectedPackagePolicy {
    fun retainInstalled(connectedPackages: Set<String>, installedPackages: Set<String>): Set<String> =
        connectedPackages.intersect(installedPackages)
}
