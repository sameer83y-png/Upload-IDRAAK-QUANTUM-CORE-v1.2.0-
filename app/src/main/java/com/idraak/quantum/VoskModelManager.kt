
package com.idraak.quantum

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

internal class VoskModelManager(context: Context) : AutoCloseable {

    interface Listener {
        fun onProgress(language: String, percent: Int)
        fun onReady(language: String, model: Model)
        fun onError(language: String, message: String)
    }

    private data class ModelInfo(
        val language: String,
        val directoryName: String,
        val downloadUrl: String
    )

    companion object {
        private const val TAG = "IDRAAK_VOSK_MODEL"

        private const val MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 600L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 100_000
        private const val BUFFER_SIZE = 8192
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000

        private val MODEL_INFOS = listOf(
            ModelInfo(
                language = "hi",
                directoryName = "vosk-model-small-hi-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
            ),
            ModelInfo(
                language = "en",
                directoryName = "vosk-model-small-en-us-0.15",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            )
        )
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "IDRAAK-Vosk-Model").apply {
                isDaemon = true
            }
        }

    private val lock = Any()
    private val closed = AtomicBoolean(false)

    private val modelDirectory =
        File(appContext.filesDir, "vosk-models")

    private var activeModel: Model? = null
    private var activeLanguage: String? = null

    /**
     * Loads the requested language model.
     *
     * All disk and model-loading operations run on one background
     * executor, preventing simultaneous downloads and installations.
     */
    fun ensureModel(language: String, listener: Listener) {
        val normalizedLanguage = normalizeLanguage(language)
        val info = MODEL_INFOS.firstOrNull {
            it.language == normalizedLanguage
        }

        if (closed.get()) {
            notifyError(listener, language, "Vosk model manager is closed.")
            return
        }

        if (info == null) {
            notifyError(
                listener,
                language,
                "Unsupported Vosk language. Currently available models: Hindi and English."
            )
            return
        }

        try {
            executor.execute {
                if (closed.get()) return@execute

                try {
                    val existing = synchronized(lock) {
                        if (closed.get()) {
                            null
                        } else if (activeLanguage == info.language) {
                            activeModel
                        } else {
                            null
                        }
                    }

                    if (existing != null) {
                        notifyReadyIfCurrent(
                            listener,
                            info.language,
                            existing
                        )
                        return@execute
                    }

                    val modelFolder = File(
                        modelDirectory,
                        info.directoryName
                    )

                    if (!isValidModelDirectory(modelFolder)) {
                        downloadAndInstall(info, listener)
                    }

                    if (closed.get()) return@execute

                    if (!isValidModelDirectory(modelFolder)) {
                        throw IOException(
                            "Downloaded Vosk model is incomplete or invalid."
                        )
                    }

                    val newModel = Model(modelFolder.absolutePath)

                    val previousModel: Model?
                    val accepted: Boolean

                    synchronized(lock) {
                        if (closed.get()) {
                            accepted = false
                            previousModel = null
                        } else {
                            previousModel = activeModel
                            activeModel = newModel
                            activeLanguage = info.language
                            accepted = true
                        }
                    }

                    if (!accepted) {
                        try {
                            newModel.close()
                        } catch (_: Exception) {
                        }
                        return@execute
                    }

                    if (previousModel != null && previousModel !== newModel) {
                        try {
                            previousModel.close()
                        } catch (exception: Exception) {
                            Log.w(
                                TAG,
                                "Previous model cleanup failed",
                                exception
                            )
                        }
                    }

                    notifyReadyIfCurrent(
                        listener,
                        info.language,
                        newModel
                    )

                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Model loading failed for ${info.language}",
                        exception
                    )

                    notifyError(
                        listener,
                        info.language,
                        exception.message ?: "Unable to load Vosk model."
                    )
                }
            }
        } catch (exception: Exception) {
            notifyError(
                listener,
                info.language,
                exception.message ?: "Unable to schedule Vosk model loading."
            )
        }
    }

    private fun normalizeLanguage(language: String): String {
        val value = language.trim().lowercase(Locale.ROOT)

        return when {
            value == "hi" ||
                value.startsWith("hi-") ||
                value.contains("hindi") -> "hi"

            value == "en" ||
                value.startsWith("en-") ||
                value.contains("english") -> "en"

            else -> value
        }
    }

    private fun downloadAndInstall(
        info: ModelInfo,
        listener: Listener
    ) {
        checkNotClosed()

        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Unable to create Vosk model directory.")
        }

        val zipFile = File(
            modelDirectory,
            "${info.directoryName}.zip.part"
        )

        val stagingDirectory = File(
            modelDirectory,
            "${info.directoryName}.installing"
        )

        deleteRecursively(zipFile)
        deleteRecursively(stagingDirectory)

        try {
            downloadFile(
                info.downloadUrl,
                zipFile,
                info.language,
                listener
            )

            checkNotClosed()

            if (!zipFile.exists() || zipFile.length() <= 0L) {
                throw IOException("Downloaded model archive is empty.")
            }

            extractZipSafely(
                zipFile,
                stagingDirectory
            )

            checkNotClosed()

            val extractedModel = locateModelDirectory(
                stagingDirectory,
                info.directoryName
            ) ?: throw IOException(
                "Model files were not found in the downloaded archive."
            )

            if (!isValidModelDirectory(extractedModel)) {
                throw IOException(
                    "Required Vosk model files are missing."
                )
            }

            val destination = File(
                modelDirectory,
                info.directoryName
            )

            if (destination.exists()) {
                deleteRecursively(destination)
            }

            val installSource = if (
                extractedModel.absoluteFile == stagingDirectory.absoluteFile
            ) {
                stagingDirectory
            } else {
                extractedModel
            }

            if (!installSource.renameTo(destination)) {
                copyDirectory(installSource, destination)
                deleteRecursively(stagingDirectory)
            }

            if (!isValidModelDirectory(destination)) {
                deleteRecursively(destination)
                throw IOException("Vosk model installation verification failed.")
            }

        } finally {
            deleteRecursively(zipFile)
            deleteRecursively(stagingDirectory)
        }
    }

    private fun downloadFile(
        downloadUrl: String,
        destination: File,
        language: String,
        listener: Listener
    ) {
        var currentUrl = URL(downloadUrl)
        var connection: HttpURLConnection? = null

        try {
            var redirectCount = 0

            while (true) {
                checkNotClosed()

                if (currentUrl.protocol != "https") {
                    throw IOException("Only HTTPS model downloads are allowed.")
                }

                connection = currentUrl.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent",
                    "IDRAAK-Quantum-Core"
                )

                connection.connect()

                val responseCode = connection.responseCode

                if (
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 ||
                    responseCode == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Download redirect has no destination.")

                    connection.disconnect()
                    connection = null

                    if (++redirectCount > MAX_REDIRECTS) {
                        throw IOException("Too many model download redirects.")
                    }

                    currentUrl = URL(currentUrl, location)
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException(
                        "Model download failed with HTTP $responseCode."
                    )
                }

                break
            }

            val activeConnection = connection
                ?: throw IOException("Unable to establish model download connection.")

            val contentLength = activeConnection.contentLengthLong

            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw IOException("Model archive exceeds the download size limit.")
            }

            var totalBytes = 0L
            var lastReportedPercent = -1

            BufferedInputStream(activeConnection.inputStream).use { input ->
                FileOutputStream(destination).use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)

                        while (true) {
                            checkNotClosed()

                            val count = input.read(buffer)
                            if (count == -1) break

                            totalBytes += count

                            if (totalBytes > MAX_DOWNLOAD_BYTES) {
                                throw IOException(
                                    "Model archive exceeds the download size limit."
                                )
                            }

                            output.write(buffer, 0, count)

                            if (contentLength > 0L) {
                                val percent = (
                                    totalBytes * 100L / contentLength
                                ).toInt().coerceIn(0, 100)

                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    notifyProgress(
                                        listener,
                                        language,
                                        percent
                                    )
                                }
                            }
                        }

                        output.flush()
                    }
                }
            }

            if (totalBytes <= 0L) {
                throw IOException("Model download returned no data.")
            }

            notifyProgress(listener, language, 100)

        } finally {
            connection?.disconnect()
        }
    }

    private fun extractZipSafely(
        zipFile: File,
        destination: File
    ) {
        checkNotClosed()

        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Unable to create model extraction directory.")
        }

        val canonicalRoot = destination.canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator

        var totalExtracted = 0L
        var entryCount = 0

        ZipInputStream(
            BufferedInputStream(zipFile.inputStream())
        ).use { zipInput ->

            while (true) {
                checkNotClosed()

                val entry = zipInput.nextEntry ?: break
                entryCount++

                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IOException("Model archive contains too many files.")
                }

                val outputFile = File(destination, entry.name).canonicalFile

                if (
                    outputFile.path != canonicalRoot.path &&
                    !outputFile.path.startsWith(rootPrefix)
                ) {
                    throw IOException("Unsafe path found in model archive.")
                }

                if (entry.isDirectory) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw IOException("Unable to create model directory.")
                    }
                } else {
                    val parent = outputFile.parentFile
                        ?: throw IOException("Invalid model archive entry.")

                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Unable to create extraction folder.")
                    }

                    FileOutputStream(outputFile).use { fileOutput ->
                        BufferedOutputStream(fileOutput).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)

                            while (true) {
                                checkNotClosed()

                                val count = zipInput.read(buffer)
                                if (count == -1) break

                                totalExtracted += count

                                if (totalExtracted > MAX_EXTRACTED_BYTES) {
                                    throw IOException(
                                        "Extracted model exceeds the size limit."
                                    )
                                }

                                output.write(buffer, 0, count)
                            }

                            output.flush()
                        }
                    }
                }

                zipInput.closeEntry()
            }
        }
    }

    private fun locateModelDirectory(
        stagingDirectory: File,
        expectedDirectoryName: String
    ): File? {
        val expected = File(stagingDirectory, expectedDirectoryName)

        if (isValidModelDirectory(expected)) {
            return expected
        }

        if (isValidModelDirectory(stagingDirectory)) {
            return stagingDirectory
        }

        val children = stagingDirectory.listFiles() ?: return null

        for (child in children) {
            if (
                child.isDirectory &&
                isValidModelDirectory(child)
            ) {
                return child
            }
        }

        return null
    }

    private fun isValidModelDirectory(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false

        val acousticModel = File(directory, "am/final.mdl")
        val featureConfig = File(directory, "conf/mfcc.conf")

        return acousticModel.isFile &&
            acousticModel.length() > 0L &&
            featureConfig.isFile &&
            featureConfig.length() > 0L
    }

    private fun copyDirectory(source: File, destination: File) {
        checkNotClosed()

        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Unable to create model installation folder.")
        }

        val children = source.listFiles()
            ?: throw IOException("Unable to read extracted model files.")

        for (child in children) {
            checkNotClosed()

            val target = File(destination, child.name)

            if (child.isDirectory) {
                copyDirectory(child, target)
            } else {
                child.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)

                        while (true) {
                            checkNotClosed()

                            val count = input.read(buffer)
                            if (count == -1) break

                            output.write(buffer, 0, count)
                        }

                        output.flush()
                    }
                }
            }
        }
    }

    private fun notifyProgress(
        listener: Listener,
        language: String,
        percent: Int
    ) {
        if (closed.get()) return

        mainHandler.post {
            if (closed.get()) return@post

            try {
                listener.onProgress(language, percent)
            } catch (exception: Exception) {
                Log.e(TAG, "Progress callback failed", exception)
            }
        }
    }

    private fun notifyReadyIfCurrent(
        listener: Listener,
        language: String,
        model: Model
    ) {
        if (closed.get()) return

        mainHandler.post {
            if (closed.get()) return@post

            val stillCurrent = synchronized(lock) {
                !closed.get() &&
                    activeModel === model &&
                    activeLanguage == language
            }

            if (!stillCurrent) return@post

            try {
                listener.onReady(language, model)
            } catch (exception: Exception) {
                Log.e(TAG, "Model ready callback failed", exception)
            }
        }
    }

    private fun notifyError(
        listener: Listener,
        language: String,
        message: String
    ) {
        if (closed.get()) return

        mainHandler.post {
            if (closed.get()) return@post

            try {
                listener.onError(language, message)
            } catch (exception: Exception) {
                Log.e(TAG, "Model error callback failed", exception)
            }
        }
    }

    private fun checkNotClosed() {
        if (closed.get() || Thread.currentThread().isInterrupted) {
            throw IOException("Vosk model operation was cancelled.")
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return

        if (file.isDirectory) {
            val children = file.listFiles()
            children?.forEach { child ->
                deleteRecursively(child)
            }
        }

        if (!file.delete() && file.exists()) {
            Log.w(TAG, "Unable to delete temporary file: ${file.absolutePath}")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val oldModel = synchronized(lock) {
            val previous = activeModel
            activeModel = null
            activeLanguage = null
            previous
        }

        try {
            executor.shutdownNow()
        } catch (exception: Exception) {
            Log.w(TAG, "Executor shutdown failed", exception)
        }

        if (oldModel != null) {
            try {
                oldModel.close()
            } catch (exception: Exception) {
                Log.w(TAG, "Vosk model cleanup failed", exception)
            }
        }

        mainHandler.removeCallbacksAndMessages(null)
    }
}
