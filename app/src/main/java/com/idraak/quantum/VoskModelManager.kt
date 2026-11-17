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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

internal class VoskModelManager(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "VoskModelManager"

        private const val MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 600L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 100_000
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 60_000

        private const val MODEL_HINDI = "hi"
        private const val MODEL_ENGLISH = "en"

        private const val HINDI_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"

        private const val ENGLISH_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }

    interface Listener {
        fun onProgress(language: String, percent: Int)
        fun onReady(language: String, model: Model)
        fun onError(language: String, message: String)
    }

    private data class ModelConfig(
        val id: String,
        val downloadUrl: String
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val lock = Any()

    private val modelDirectory =
        File(appContext.filesDir, "vosk_models")

    private val loadedModels = mutableMapOf<String, Model>()

    /*
     * पुराने model को तुरंत close नहीं करते।
     * Recognition engine जब तक इस्तेमाल कर सकता है,
     * तब तक model जीवित रहना चाहिए।
     */
    private val ownedModels = mutableListOf<Model>()

    @Volatile
    private var closed = false

    init {
        if (!modelDirectory.exists()) {
            modelDirectory.mkdirs()
        }
    }

    fun ensureModel(language: String, listener: Listener) {
        if (closed) {
            notifyError(language, "Vosk Model Manager बंद है।")
            return
        }

        val config = getModelConfig(language)

        if (config == null) {
            notifyError(
                language,
                "इस भाषा के लिए Vosk model उपलब्ध नहीं है: $language"
            )
            return
        }

        try {
            executor.execute {
                if (closed) return@execute

                try {
                    val cachedModel = synchronized(lock) {
                        loadedModels[config.id]
                    }

                    if (cachedModel != null) {
                        notifyReady(language, cachedModel)
                        return@execute
                    }

                    notifyProgress(language, 5)

                    val modelFolder = File(modelDirectory, config.id)

                    if (!isValidModelFolder(modelFolder)) {
                        if (modelFolder.exists()) {
                            modelFolder.deleteRecursively()
                        }

                        installModel(config, modelFolder) { percent ->
                            notifyProgress(language, percent)
                        }
                    }

                    if (closed) return@execute

                    if (!isValidModelFolder(modelFolder)) {
                        throw IllegalStateException(
                            "Downloaded Vosk model की files अधूरी हैं।"
                        )
                    }

                    notifyProgress(language, 95)

                    val model = Model(modelFolder.absolutePath)

                    synchronized(lock) {
                        if (closed) {
                            try {
                                model.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Model close error", e)
                            }
                            return@execute
                        }

                        loadedModels[config.id] = model
                        ownedModels.add(model)
                    }

                    notifyProgress(language, 100)
                    notifyReady(language, model)

                } catch (e: LinkageError) {
                    Log.e(TAG, "Vosk native library error", e)
                    notifyError(
                        language,
                        "Vosk native library load नहीं हो सकी: ${e.message}"
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Model loading failed", e)
                    notifyError(
                        language,
                        e.message ?: "Vosk model load नहीं हो सका।"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to schedule model loading", e)
            notifyError(
                language,
                e.message ?: "Model loading शुरू नहीं हो सकी।"
            )
        }
    }

    private fun getModelConfig(language: String): ModelConfig? {
        return when (language.trim().lowercase()) {
            "hi", "hi-in", "hindi" -> {
                ModelConfig(MODEL_HINDI, HINDI_URL)
            }

            "en", "en-in", "en-us", "english" -> {
                ModelConfig(MODEL_ENGLISH, ENGLISH_URL)
            }

            else -> null
        }
    }

    private fun isValidModelFolder(folder: File): Boolean {
        if (!folder.exists() || !folder.isDirectory) {
            return false
        }

        val modelConfig = File(folder, "conf/model.conf")
        val acousticModel = File(folder, "am/final.mdl")

        return modelConfig.isFile &&
                acousticModel.isFile &&
                modelConfig.length() > 0 &&
                acousticModel.length() > 0
    }

    private fun installModel(
        config: ModelConfig,
        destination: File,
        onProgress: (Int) -> Unit
    ) {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IllegalStateException(
                "Vosk model directory नहीं बन सकी।"
            )
        }

        val zipFile = File(modelDirectory, "${config.id}.zip")
        val extractDirectory =
            File(modelDirectory, "${config.id}_extract")

        try {
            if (extractDirectory.exists()) {
                extractDirectory.deleteRecursively()
            }

            if (!extractDirectory.mkdirs()) {
                throw IllegalStateException(
                    "Temporary extraction directory नहीं बन सकी।"
                )
            }

            downloadFile(config.downloadUrl, zipFile, onProgress)

            if (closed) return

            onProgress(75)

            extractZipSafely(zipFile, extractDirectory)

            if (closed) return

            onProgress(90)

            val extractedModel = findModelDirectory(extractDirectory)
                ?: throw IllegalStateException(
                    "ZIP में Vosk model की सही directory नहीं मिली।"
                )

            if (destination.exists()) {
                destination.deleteRecursively()
            }

            if (!extractedModel.renameTo(destination)) {
                copyDirectory(extractedModel, destination)
            }

            if (!isValidModelFolder(destination)) {
                destination.deleteRecursively()

                throw IllegalStateException(
                    "Extracted Vosk model valid नहीं है।"
                )
            }

            onProgress(94)

        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
            }

            if (extractDirectory.exists()) {
                extractDirectory.deleteRecursively()
            }
        }
    }

    private fun downloadFile(
        downloadUrl: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(downloadUrl)

            if (url.protocol != "https") {
                throw SecurityException(
                    "केवल HTTPS model downloads स्वीकार हैं।"
                )
            }

            if (url.host != "alphacephei.com") {
                throw SecurityException(
                    "अनुमति नहीं प्राप्त download host।"
                )
            }

            connection =
                url.openConnection() as HttpURLConnection

            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"

            connection.connect()

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Model download HTTP error: $responseCode"
                )
            }

            val contentLength = connection.contentLengthLong

            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw IllegalStateException(
                    "Model download निर्धारित size limit से बड़ा है।"
                )
            }

            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(outputFile).use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->

                        val buffer = ByteArray(16 * 1024)

                        while (true) {
                            if (closed) {
                                throw InterruptedException(
                                    "Model download बंद किया गया।"
                                )
                            }

                            val count = input.read(buffer)

                            if (count == -1) break

                            downloaded += count

                            if (downloaded > MAX_DOWNLOAD_BYTES) {
                                throw IllegalStateException(
                                    "Model download size limit पार कर गया।"
                                )
                            }

                            output.write(buffer, 0, count)

                            if (contentLength > 0) {
                                val percent =
                                    ((downloaded * 65) / contentLength)
                                        .toInt()
                                        .coerceIn(5, 70)

                                onProgress(percent)
                            }
                        }

                        output.flush()
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException(
                    "Downloaded ZIP file खाली है।"
                )
            }

        } finally {
            connection?.disconnect()
        }
    }

    private fun extractZipSafely(
        zipFile: File,
        destination: File
    ) {
        val canonicalDestination =
            destination.canonicalFile

        var totalExtracted = 0L
        var entryCount = 0

        ZipInputStream(
            BufferedInputStream(zipFile.inputStream())
        ).use { zipInput ->

            while (true) {
                if (closed) {
                    throw InterruptedException(
                        "ZIP extraction बंद किया गया।"
                    )
                }

                val entry = zipInput.nextEntry ?: break

                entryCount++

                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IllegalStateException(
                        "ZIP में बहुत अधिक entries हैं।"
                    )
                }

                val outputFile = File(
                    canonicalDestination,
                    entry.name
                )

                val canonicalOutput = outputFile.canonicalFile

                if (!canonicalOutput.path.startsWith(
                        canonicalDestination.path +
                                File.separator
                    ) &&
                    canonicalOutput != canonicalDestination
                ) {
                    throw SecurityException(
                        "ZIP में unsafe file path मिला।"
                    )
                }

                if (entry.isDirectory) {
                    if (!canonicalOutput.exists() &&
                        !canonicalOutput.mkdirs()
                    ) {
                        throw IllegalStateException(
                            "ZIP directory नहीं बनाई जा सकी।"
                        )
                    }
                } else {
                    val parent = canonicalOutput.parentFile

                    if (parent != null &&
                        !parent.exists() &&
                        !parent.mkdirs()
                    ) {
                        throw IllegalStateException(
                            "ZIP parent directory नहीं बनाई जा सकी।"
                        )
                    }

                    FileOutputStream(canonicalOutput).use { output ->
                        val buffer = ByteArray(16 * 1024)

                        while (true) {
                            if (closed) {
                                throw InterruptedException(
                                    "ZIP extraction बंद किया गया।"
                                )
                            }

                            val count = zipInput.read(buffer)

                            if (count == -1) break

                            totalExtracted += count

                            if (totalExtracted > MAX_EXTRACTED_BYTES) {
                                throw IllegalStateException(
                                    "Extracted model size limit पार कर गया।"
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

    private fun findModelDirectory(root: File): File? {
        if (isValidModelFolder(root)) {
            return root
        }

        val children = root.listFiles() ?: return null

        for (child in children) {
            if (child.isDirectory) {
                if (isValidModelFolder(child)) {
                    return child
                }

                val nested = findModelDirectory(child)

                if (nested != null) {
                    return nested
                }
            }
        }

        return null
    }

    private fun copyDirectory(
        source: File,
        destination: File
    ) {
        if (!source.exists() || !source.isDirectory) {
            throw IllegalArgumentException(
                "Invalid source model directory."
            )
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw IllegalStateException(
                "Destination model directory नहीं बन सकी।"
            )
        }

        val children = source.listFiles()
            ?: throw IllegalStateException(
                "Source model directory पढ़ी नहीं जा सकी।"
            )

        for (child in children) {
            val target = File(destination, child.name)

            if (child.isDirectory) {
                copyDirectory(child, target)
            } else {
                child.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            }
        }
    }

    private fun notifyProgress(
        language: String,
        percent: Int
    ) {
        if (closed) return

        mainHandler.post {
            if (!closed) {
                // Progress listener को Service लागू करता है।
                currentListeners[language]?.onProgress(
                    language,
                    percent.coerceIn(0, 100)
                )
            }
        }
    }

    private fun notifyReady(
        language: String,
        model: Model
    ) {
        if (closed) return

        mainHandler.post {
            if (!closed) {
                currentListeners.remove(language)?.onReady(
                    language,
                    model
                )
            }
        }
    }

    private fun notifyError(
        language: String,
        message: String
    ) {
        if (closed) return

        mainHandler.post {
            if (!closed) {
                currentListeners.remove(language)?.onError(
                    language,
                    message
                )
            }
        }
    }

    private val currentListeners =
        mutableMapOf<String, Listener>()

    private fun registerListener(
        language: String,
        listener: Listener
    ) {
        synchronized(currentListeners) {
            currentListeners[language] = listener
        }
    }

    override fun close() {
        if (closed) return

        closed = true

        executor.shutdownNow()

        var terminated = false

        try {
            terminated = executor.awaitTermination(
                3,
                TimeUnit.SECONDS
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while closing model manager")
        }

        if (!terminated) {
            Log.w(
                TAG,
                "Model worker अभी चल रहा है; models को close नहीं किया गया।"
            )
            return
        }

        synchronized(lock) {
            for (model in ownedModels) {
                try {
                    model.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing Vosk model", e)
                }
            }

            ownedModels.clear()
            loadedModels.clear()
        }

        synchronized(currentListeners) {
            currentListeners.clear()
        }

        mainHandler.removeCallbacksAndMessages(null)
    }
}
