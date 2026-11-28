package com.idraak.quantum.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * User-approved, allow-listed app connections.
 * This registry never loads executable code from another application.
 */
object AppPluginRegistry {
    private const val PREFS_NAME = "idraak_plugin_registry"
    private const val KEY_CONNECTED_PACKAGES = "connected_packages"

    fun connectedPackages(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = preferences.getStringSet(KEY_CONNECTED_PACKAGES, emptySet())
            ?.toSet() ?: emptySet()
        if (saved.isEmpty()) return emptySet()

        // A removed app must not silently regain its old connection if it is
        // installed again later. Keep only packages currently installed.
        val installed = saved.filterTo(mutableSetOf()) { packageName ->
            try {
                context.packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }
        val valid = ConnectedPackagePolicy.retainInstalled(saved, installed)
        if (valid != saved) {
            preferences.edit().putStringSet(KEY_CONNECTED_PACKAGES, valid).apply()
        }
        return valid
    }

    fun isConnected(context: Context, packageName: String): Boolean =
        connectedPackages(context).contains(packageName)

    fun connect(context: Context, packageName: String) {
        val updated = connectedPackages(context).toMutableSet()
        updated.add(packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CONNECTED_PACKAGES, updated).apply()
    }

    fun disconnect(context: Context, packageName: String) {
        val updated = connectedPackages(context).toMutableSet()
        updated.remove(packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CONNECTED_PACKAGES, updated).apply()
    }

    /** Finds exactly one connected app by its visible launcher label. */
    fun findConnectedPackage(context: Context, requestedName: String): String? {
        val wanted = normalize(requestedName)
        if (wanted.isBlank()) return null
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = try {
            pm.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }
        val connected = connectedPackages(context)
        val visible = matches.mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (pkg !in connected || pkg == context.packageName) null
            else {
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else pkg to normalize(label)
            }
        }.distinctBy { it.first }
        val exact = visible.filter { it.second == wanted }
        val candidates = if (exact.isNotEmpty()) exact else visible.filter {
            it.second.startsWith(wanted) || wanted.startsWith(it.second)
        }
        return candidates.singleOrNull()?.first
    }

    fun supportsTextShare(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(packageName)
        return try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: Exception) {
            false
        }
    }

    fun supportsWebLinks(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
            .setPackage(packageName)
        return try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}
