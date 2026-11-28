package com.idraak.quantum.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class IdraakUpdateInfo(
    val latestVersion: String,
    val releasePage: String,
    val apkDownloadUrl: String?,
    val updateAvailable: Boolean
)

/**
 * Checks published GitHub Releases. Android still requires the user to review
 * and approve APK installation; the app never replaces its own package silently.
 */
class IdraakUpdateChecker(
    private val currentVersion: String
) {
    fun check(): IdraakUpdateInfo {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Idraak-Quantum-Android")
        }
        try {
            val status = connection.responseCode
            if (status == 404) throw IllegalStateException("ابھی GitHub پر کوئی نیا official release شائع نہیں ہوا۔")
            if (status !in 200..299) throw IllegalStateException("Update check failed (HTTP $status).")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim().removePrefix("v")
            val page = json.optString("html_url")
            if (tag.isBlank() || page.isBlank()) throw IllegalStateException("Release information is incomplete.")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        break
                    }
                }
            }
            return IdraakUpdateInfo(tag, page, apkUrl, isNewer(tag, currentVersion.removePrefix("v")))
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(candidate: String, installed: String): Boolean =
        IdraakVersionComparator.isNewer(candidate, installed)

    companion object {
        private const val RELEASE_API =
            "https://api.github.com/repos/sameer83y-png/Upload-IDRAAK-QUANTUM-CORE-v1.2.0-/releases/latest"
    }
}
