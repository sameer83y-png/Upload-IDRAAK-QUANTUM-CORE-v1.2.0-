package com.idraak.quantum.core

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadedIdraakUpdate(
    val version: String,
    val apkFile: File
)

/**
 * Downloads only the APK attached to this repository's latest official GitHub Release.
 * It requires GitHub's SHA-256 asset digest and checks the package name and signing
 * certificate before exposing the APK to Android's package installer.
 */
class IdraakAutoUpdater(private val activity: Activity) {
    fun checkAndDownload(currentVersion: String): DownloadedIdraakUpdate? {
        val release = readLatestRelease()
        val version = release.optString("tag_name").trim().removePrefix("v")
        if (version.isBlank()) throw IllegalStateException("Release version is missing.")
        if (!isNewer(version, currentVersion.removePrefix("v"))) return null

        val assets = release.optJSONArray("assets")
            ?: throw IllegalStateException("Release has no APK assets.")
        var apkUrl: String? = null
        var expectedDigest: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.equals("idraak-quantum-release.apk", ignoreCase = true) ||
                (apkUrl == null && name.endsWith(".apk", ignoreCase = true))
            ) {
                apkUrl = asset.optString("browser_download_url")
                expectedDigest = asset.optString("digest").removePrefix("sha256:")
                if (name.equals("idraak-quantum-release.apk", ignoreCase = true)) break
            }
        }
        val url = apkUrl ?: throw IllegalStateException("Official release APK was not found.")
        val digest = expectedDigest?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            ?: throw IllegalStateException("GitHub release has no valid SHA-256 digest. Update blocked for safety.")

        val directory = File(activity.cacheDir, "idraak-updates")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Cannot create the update cache.")
        }
        val apkFile = File(directory, "idraak-update.apk")
        download(url, apkFile)
        val actualDigest = sha256(apkFile)
        if (!actualDigest.equals(digest, ignoreCase = true)) {
            apkFile.delete()
            throw IllegalStateException("APK SHA-256 verification failed. Update blocked.")
        }
        verifyPackageAndSignature(apkFile)
        return DownloadedIdraakUpdate(version, apkFile)
    }

    fun install(update: DownloadedIdraakUpdate): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.packageName)
            )
            activity.startActivity(settingsIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            activity,
            activity.packageName + ".fileprovider",
            update.apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return true
    }

    private fun readLatestRelease(): JSONObject {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Idraak-Quantum-Android")
        }
        try {
            val status = connection.responseCode
            if (status == 404) throw IllegalStateException("ابھی کوئی نیا official release شائع نہیں ہوا۔")
            if (status !in 200..299) throw IllegalStateException("GitHub update check failed (HTTP $status).")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun download(downloadUrl: String, destination: File) {
        if (!downloadUrl.startsWith("https://github.com/")) {
            throw IllegalStateException("APK download URL is not an official GitHub release URL.")
        }
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Idraak-Quantum-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("APK download failed (HTTP $status).")
            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_APK_BYTES) throw IllegalStateException("APK is larger than the allowed download limit.")
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IllegalStateException("APK exceeded the allowed download limit.")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!destination.isFile || destination.length() == 0L) {
                throw IllegalStateException("Downloaded APK is empty.")
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyPackageAndSignature(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val installed = activity.packageManager.getPackageInfo(activity.packageName, flags)
        val candidate = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalStateException("Downloaded file is not a valid Android APK.")
        if (candidate.packageName != activity.packageName) {
            throw IllegalStateException("APK package identity does not match Idraak.")
        }
        val installedSigners = signerDigests(installed)
        val candidateSigners = signerDigests(candidate)
        if (installedSigners.isEmpty() || installedSigners != candidateSigners) {
            throw IllegalStateException(
                "APK signing identity does not match this installed Idraak. Update blocked to protect the app."
            )
        }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures?.map { sha256(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun isNewer(candidate: String, installed: String): Boolean =
        IdraakVersionComparator.isNewer(candidate, installed)

    companion object {
        private const val MAX_APK_BYTES = 150L * 1024L * 1024L
        private const val RELEASE_API =
            "https://api.github.com/repos/sameer83y-png/Upload-IDRAAK-QUANTUM-CORE-v1.2.0-/releases/latest"
    }
}
