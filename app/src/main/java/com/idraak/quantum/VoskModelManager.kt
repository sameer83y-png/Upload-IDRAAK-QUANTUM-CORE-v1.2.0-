
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
import java.util.concurrent.Executors

/**
 * IDRAAK Vosk Model Manager
 *
 * Responsibilities:
 * - Download supported Vosk models.
 * - Extract ZIP files safely.
 * - Validate downloaded model structure.
 * - Store models in internal app storage.
 * - Load and cache the active Vosk Model.
 *
 * Supported recognition languages in this version:
 * - Hindi
 * - English
 */
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

        private const val CONNECT_TIMEOUT = 20000
        private const val READ_TIMEOUT = 60000

        private const val MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 600L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 100000

        private const val BUFFER_SIZE = 8192

        private val MODELS = mapOf(
            "hi" to ModelInfo(
                language = "hi",
                directoryName = "vosk-model-small-hi-0.22",
                downloadUrl =
                    "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
            ),
            "en" to ModelInfo(
                language = "en",
                directoryName = "vosk-model-small-en-us-0.15",
                downloadUrl =
                    "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            )
        )
    }

    private val appContext = context.applicationContext

    private val modelDirectory =
        File(appContext.filesDir, "vosk_models")

    private val executor = Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    @Volatile
    private var activeModel: Model? = null

    @Volatile
    private var activeLanguage: String? = null

    /**
     * Starts model preparation on a background thread.
     *
     * The listener callbacks are delivered on the main thread.
     */
    fun ensureModel(
        language: String,
        listener: Listener
    ) {
        val normalizedLanguage = normalizeLanguage(language)

        if (normalizedLanguage == null) {
            notifyError(
                listener,
                language,
                "Vosk currently supports Hindi and English only. " +
                    "Arabic and Urdu recognition are not configured."
            )
            return
        }

        val info = MODELS[normalizedLanguage]
            ?: run {
                notifyError(
                    listener,
                    language,
                    "No Vosk model is configured for this language."
                )
                return
            }

        if (closed) {
            notifyError(listener, language, "Model manager is closed.")
            return
        }

        try {
            executor.execute {
                if (closed) return@execute

                try {
                    val targetDirectory =
                        File(modelDirectory, info.directoryName)

                    if (!isValidModelDirectory(targetDirectory)) {
                        downloadAndExtract(info, targetDirectory, listener)
                    }

                    if (closed) return@execute

                    if (!isValidModelDirectory(targetDirectory)) {
                        throw IOException(
                            "Downloaded Vosk model failed validation."
                        )
                    }

                    val currentModel = activeModel

                    if (
                        currentModel != null &&
                        activeLanguage == info.language
                    ) {
                        notifyReady(
                            listener,
                            info.language,
                            currentModel
                        )
                        return@execute
                    }

                    val newModel = Model(targetDirectory.absolutePath)

                    if (closed) {
                        newModel.close()
                        return@execute
                    }

                    val oldModel = activeModel

                    activeModel = newModel
                    activeLanguage = info.language

                    try {
                        oldModel?.close()
                    } catch (exception: Exception) {
                        Log.w(TAG, "Could not close previous model", exception)
                    }

                    notifyReady(
                        listener,
                        info.language,
                        newModel
                    )

                } catch (exception: Exception) {
                    Log.e(TAG, "Model preparation failed", exception)

                    notifyError(
                        listener,
                        language,
                        exception.message ?: "Unable to prepare Vosk model."
                    )
                }
            }
        } catch (exception: Exception) {
            notifyError(
                listener,
                language,
                exception.message ?: "Unable to start model preparation."
            )
        }
    }

    /**
     * Converts app language names and locale codes
     * into supported Vosk model identifiers.
     */
    private fun normalizeLanguage(language: String): String? {
        val value = language
            .trim()
            .lowercase()
            .replace('_', '-')

        return when {
            value == "hi" ||
                value.startsWith("hi-") ||
                value == "hindi" -> "hi"

            value == "en" ||
                value.startsWith("en-") ||
                value == "english" -> "en"

            else -> null
        }
    }

    /**
     * Downloads the model ZIP and extracts it into
     * a temporary directory before installing it.
     */
    private fun downloadAndExtract(
        info: ModelInfo,
        targetDirectory: File,
        listener: Listener
    ) {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Cannot create Vosk model directory.")
        }

        val stagingDirectory = File(
            modelDirectory,
            "${info.directoryName}.installing"
        )

        val zipFile = File(
            modelDirectory,
            "${info.directoryName}.zip.part"
        )

        stagingDirectory.deleteRecursively()
        zipFile.delete()

        try {
            downloadFile(
                info.downloadUrl,
                zipFile,
                info.language,
                listener
            )

            if (closed) {
                throw IOException("Model installation was cancelled.")
            }

            val extractionDirectory =
                File(stagingDirectory, "extracted")

            if (!extractionDirectory.mkdirs()) {
                throw IOException("Cannot create extraction directory.")
            }

            extractZipSafely(
                zipFile,
                extractionDirectory
            )

            val extractedModel =
                findModelDirectory(extractionDirectory)

            if (!isValidModelDirectory(extractedModel)) {
                throw IOException(
                    "ZIP archive does not contain a valid Vosk model."
                )
            }

            if (closed) {
                throw IOException("Model installation was cancelled.")
            }

            if (targetDirectory.exists() &&
                !targetDirectory.deleteRecursively()
            ) {
                throw IOException("Cannot replace existing model directory.")
            }

            if (!extractedModel.renameTo(targetDirectory)) {
                throw IOException("Could not install extracted Vosk model.")
            }

            Log.i(
                TAG,
                "Installed model: ${info.directoryName}"
            )

        } finally {
            zipFile.delete()
            stagingDirectory.deleteRecursively()
        }
    }

    /**
     * Downloads a ZIP file using HTTPS.
     */
    private fun downloadFile(
        downloadUrl: String,
        destination: File,
        language: String,
        listener: Listener
    ) {
        val connection =
            URL(downloadUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "IDRAAK-Quantum-Core"
            )

            connection.connect()

            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(
                    "Model server returned HTTP $responseCode."
                )
            }

            val contentLength = connection.contentLengthLong

            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw IOException(
                    "Model download exceeds the allowed size."
                )
            }

            var downloadedBytes = 0L
            var lastReportedPercent = -1

            BufferedInputStream(
                connection.inputStream
            ).use { input ->

                BufferedOutputStream(
                    FileOutputStream(destination)
                ).use { output ->

                    val buffer = ByteArray(BUFFER_SIZE)

                    while (true) {
                        if (closed) {
                            throw IOException(
                                "Model download was cancelled."
                            )
                        }

                        val count = input.read(buffer)

                        if (count == -1) break

                        downloadedBytes += count

                        if (downloadedBytes > MAX_DOWNLOAD_BYTES) {
                            throw IOException(
                                "Model download exceeded the allowed size."
                            )
                        }

                        output.write(buffer, 0, count)

                        if (contentLength > 0L) {
                            val percent = (
                                downloadedBytes * 100L /
                                    contentLength
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

            if (destination.length() <= 0L) {
                throw IOException("Downloaded model ZIP is empty.")
            }

            if (
                contentLength > 0L &&
                destination.length() != contentLength
            ) {
                throw IOException("Model download was incomplete.")
            }

            notifyProgress(listener, language, 100)

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extracts ZIP entries while preventing ZIP path traversal.
     */
    private fun extractZipSafely(
        zipFile: File,
        destinationDirectory: File
    ) {
        val destinationRoot =
            destinationDirectory.canonicalFile

        val rootPath =
            destinationRoot.path + File.separator

        var totalExtractedBytes = 0L
        var entryCount = 0

        java.util.zip.ZipInputStream(
            BufferedInputStream(zipFile.inputStream())
        ).use { zipInput ->

            while (true) {
                if (closed) {
                    throw IOException("ZIP extraction was cancelled.")
                }

                val entry = zipInput.nextEntry ?: break

                entryCount++

                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IOException("ZIP contains too many entries.")
                }

                val outputFile =
                    File(destinationRoot, entry.name).canonicalFile

                if (
                    !outputFile.path.startsWith(rootPath) &&
                    outputFile != destinationRoot
                ) {
                    throw IOException(
                        "Unsafe ZIP entry detected."
                    )
                }

                if (entry.isDirectory) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw IOException(
                            "Could not create model directory."
                        )
                    }
                } else {
                    val parent = outputFile.parentFile
                        ?: throw IOException("Invalid ZIP entry path.")

                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException(
                            "Could not create extraction folder."
                        )
                    }

                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)

                        while (true) {
                            if (closed) {
                                throw IOException(
                                    "ZIP extraction was cancelled."
                                )
                            }

                            val count = zipInput.read(buffer)

                            if (count == -1) break

                            totalExtractedBytes += count

                            if (
                                totalExtractedBytes >
                                MAX_EXTRACTED_BYTES
                            ) {
                                throw IOException(
                                    "Extracted model exceeds size limit."
                                )
                            }

                            output.write(buffer, 0, count)
                        }

                        output.flush()
                    }
                }

                zipInput.closeEntry()
            }
        }
    }

    /**
     * Finds the actual model root inside the extracted ZIP.
     */
    private fun findModelDirectory(
        extractionDirectory: File
    ): File {
        if (isValidModelDirectory(extractionDirectory)) {
            return extractionDirectory
        }

        val children = extractionDirectory.listFiles()
            ?: throw IOException("Cannot inspect extracted model files.")

        return children.firstOrNull {
            it.isDirectory && isValidModelDirectory(it)
        } ?: throw IOException(
            "Vosk model files were not found in the ZIP archive."
        )
    }

    /**
     * Checks essential files required by the Vosk model.
     */
    private fun isValidModelDirectory(directory: File): Boolean {
        if (!directory.isDirectory) return false

        val acousticModel = File(directory, "am/final.mdl")
        val featureConfig = File(directory, "conf/mfcc.conf")

        return acousticModel.isFile &&
            acousticModel.length() > 0L &&
            featureConfig.isFile &&
            featureConfig.length() > 0L
    }

    private fun notifyProgress(
        listener: Listener,
        language: String,
        percent: Int
    ) {
        mainHandler.post {
            if (!closed) {
                listener.onProgress(language, percent)
            }
        }
    }

    private fun notifyReady(
        listener: Listener,
        language: String,
        model: Model
    ) {
        mainHandler.post {
            if (!closed) {
                listener.onReady(language, model)
            }
        }
    }

    private fun notifyError(
        listener: Listener,
        language: String,
        message: String
    ) {
        mainHandler.post {
            if (!closed) {
                listener.onError(language, message)
            }
        }
    }

    /**
     * Releases the active model and stops background work.
     */
    override fun close() {
        if (closed) return

        closed = true
        executor.shutdownNow()

        try {
            activeModel?.close()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not close active Vosk model", exception)
        }

        activeModel = null
        activeLanguage = null
    }
}
