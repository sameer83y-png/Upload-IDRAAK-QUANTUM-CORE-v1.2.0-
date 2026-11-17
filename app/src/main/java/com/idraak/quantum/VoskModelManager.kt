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
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

internal class VoskModelManager(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "VoskModelManager"

        private const val MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 600L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 100_000

        private const val MAX_REDIRECTS = 5
        private const val MAX_DIRECTORY_SEARCH = 100_000

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

    private data class ListenerEntry(
        val language: String,
        val listener: Listener
    )

    private val appContext = context.applicationContext

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val lock = Any()
    private val listenerLock = Any()

    private val modelDirectory =
        File(appContext.filesDir, "vosk_models")

    private val loadedModels =
        mutableMapOf<String, Model>()

    private val ownedModels =
        mutableListOf<Model>()

    private val loadingModels =
        mutableSetOf<String>()

    private val currentListeners =
        mutableMapOf<String, ListenerEntry>()

    @Volatile
    private var closed = false

    init {
        if (!modelDirectory.exists() &&
            !modelDirectory.mkdirs()
        ) {
            Log.e(TAG, "Vosk model directory नहीं बनाई जा सकी।")
        }
    }

    fun ensureModel(
        language: String,
        listener: Listener
    ) {
        if (closed) {
            postDirectError(
                language,
                listener,
                "Vosk Model Manager बंद है।"
            )
            return
        }

        val config = getModelConfig(language)

        if (config == null) {
            postDirectError(
                language,
                listener,
                "इस भाषा के लिए Vosk model उपलब्ध नहीं है: $language"
            )
            return
        }

        synchronized(listenerLock) {
            if (closed) {
                postDirectError(
                    language,
                    listener,
                    "Vosk Model Manager बंद है।"
                )
                return
            }

            currentListeners[config.id] =
                ListenerEntry(language, listener)
        }

        synchronized(lock) {
            if (closed) {
                notifyError(
                    config.id,
                    "Vosk Model Manager बंद है।"
                )
                return
            }

            if (!loadingModels.add(config.id)) {
                // इसी model की loading पहले से चल रही है।
                // नया listener पुराने listener को replace करेगा।
                return
            }
        }

        try {
            executor.execute {

                try {
                    if (closed) return@execute

                    val cachedModel = synchronized(lock) {
                        loadedModels[config.id]
                    }

                    if (cachedModel != null) {
                        notifyReady(config.id, cachedModel)
                        return@execute
                    }

                    notifyProgress(config.id, 5)

                    val modelFolder =
                        File(modelDirectory, config.id)

                    if (!isValidModelFolder(modelFolder)) {

                        if (modelFolder.exists()) {
                            modelFolder.deleteRecursively()
                        }

                        installModel(
                            config,
                            modelFolder
                        ) { percent ->
                            notifyProgress(
                                config.id,
                                percent
                            )
                        }
                    }

                    if (closed) return@execute

                    if (!isValidModelFolder(modelFolder)) {
                        throw IllegalStateException(
                            "Downloaded Vosk model की files अधूरी हैं।"
                        )
                    }

                    notifyProgress(config.id, 95)

                    val model =
                        Model(modelFolder.absolutePath)

                    var rejected = false

                    synchronized(lock) {
                        if (closed) {
                            rejected = true
                        } else {
                            loadedModels[config.id] = model
                            ownedModels.add(model)
                        }
                    }

                    if (rejected) {
                        try {
                            model.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Model close error", e)
                        }
                        return@execute
                    }

                    notifyProgress(config.id, 100)
                    notifyReady(config.id, model)

                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()

                    Log.w(
                        TAG,
                        "Model loading interrupted",
                        e
                    )

                    notifyError(
                        config.id,
                        "Vosk model loading रोक दी गई।"
                    )

                } catch (e: LinkageError) {
                    Log.e(
                        TAG,
                        "Vosk native library error",
                        e
                    )

                    notifyError(
                        config.id,
                        "Vosk native library load नहीं हो सकी: ${e.message}"
                    )

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Model loading failed",
                        e
                    )

                    notifyError(
                        config.id,
                        e.message ?: "Vosk model load नहीं हो सका।"
                    )

                } finally {
                    synchronized(lock) {
                        loadingModels.remove(config.id)
                    }
                }
            }

        } catch (e: RejectedExecutionException) {
            synchronized(lock) {
                loadingModels.remove(config.id)
            }

            Log.e(TAG, "Model executor rejected task", e)

            notifyError(
                config.id,
                "Model loading शुरू नहीं हो सकी।"
            )

        } catch (e: Exception) {
            synchronized(lock) {
                loadingModels.remove(config.id)
            }

            Log.e(TAG, "Unable to schedule model loading", e)

            notifyError(
                config.id,
                e.message ?: "Model loading शुरू नहीं हो सकी।"
            )
        }
    }

    private fun getModelConfig(
        language: String
    ): ModelConfig? {

        return when (language.trim().lowercase()) {

            "hi", "hi-in", "hindi" -> {
                ModelConfig(
                    MODEL_HINDI,
                    HINDI_URL
                )
            }

            "en", "en-in", "en-us", "english" -> {
                ModelConfig(
                    MODEL_ENGLISH,
                    ENGLISH_URL
                )
            }

            else -> null
        }
    }

    private fun isValidModelFolder(
        folder: File
    ): Boolean {

        if (!folder.exists() || !folder.isDirectory) {
            return false
        }

        val modelConfig =
            File(folder, "conf/model.conf")

        val acousticModel =
            File(folder, "am/final.mdl")

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
        if (!modelDirectory.exists() &&
            !modelDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "Vosk model directory नहीं बन सकी।"
            )
        }

        val zipFile =
            File(modelDirectory, "${config.id}.zip")

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

            downloadFile(
                config.downloadUrl,
                zipFile,
                onProgress
            )

            if (closed) {
                throw InterruptedException(
                    "Model installation बंद किया गया।"
                )
            }

            onProgress(75)

            extractZipSafely(
                zipFile,
                extractDirectory
            )

            if (closed) {
                throw InterruptedException(
                    "Model installation बंद किया गया।"
                )
            }

            onProgress(90)

            val extractedModel =
                findModelDirectory(extractDirectory)
                    ?: throw IllegalStateException(
                        "ZIP में Vosk model की सही directory नहीं मिली।"
                    )

            if (destination.exists()) {
                destination.deleteRecursively()
            }

            if (!extractedModel.renameTo(destination)) {
                copyDirectory(
                    extractedModel,
                    destination
                )
            }

            if (!isValidModelFolder(destination)) {
                destination.deleteRecursively()

                throw IllegalStateException(
                    "Extracted Vosk model valid नहीं है।"
                )
            }

            onProgress(94)

        } catch (e: Exception) {
            if (destination.exists() &&
                !isValidModelFolder(destination)
            ) {
                destination.deleteRecursively()
            }
            throw e

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
        var currentUrl = URL(downloadUrl)
        var connection: HttpURLConnection? = null

        try {
            var redirectCount = 0

            while (true) {
                validateDownloadUrl(currentUrl)

                connection =
                    currentUrl.openConnection() as HttpURLConnection

                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"

                connection.connect()

                val responseCode =
                    connection.responseCode

                if (responseCode in 300..399) {
                    val location =
                        connection.getHeaderField("Location")

                    connection.disconnect()
                    connection = null

                    if (location.isNullOrBlank()) {
                        throw IllegalStateException(
                            "Download redirect में Location नहीं मिला।"
                        )
                    }

                    redirectCount++

                    if (redirectCount > MAX_REDIRECTS) {
                        throw IllegalStateException(
                            "Download में बहुत अधिक redirects मिले।"
                        )
                    }

                    currentUrl = URL(currentUrl, location)
                    continue
                }

                if (responseCode !in 200..299) {
                    throw IllegalStateException(
                        "Model download HTTP error: $responseCode"
                    )
                }

                break
            }

            val activeConnection =
                connection ?: throw IllegalStateException(
                    "Download connection उपलब्ध नहीं है।"
                )

            val contentLength =
                activeConnection.contentLengthLong

            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw IllegalStateException(
                    "Model download निर्धारित size limit से बड़ा है।"
                )
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }

            var downloaded = 0L

            BufferedInputStream(
                activeConnection.inputStream
            ).use { input ->

                FileOutputStream(outputFile).use { fileOutput ->

                    BufferedOutputStream(fileOutput).use { output ->

                        val buffer = ByteArray(16 * 1024)

                        while (true) {
                            if (closed ||
                                Thread.currentThread().isInterrupted
                            ) {
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

                            output.write(
                                buffer,
                                0,
                                count
                            )

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

            if (!outputFile.exists() ||
                outputFile.length() == 0L
            ) {
                throw IllegalStateException(
                    "Downloaded ZIP file खाली है।"
                )
            }

        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e

        } finally {
            connection?.disconnect()
        }
    }

    private fun validateDownloadUrl(url: URL) {
        if (!url.protocol.equals("https", true)) {
            throw SecurityException(
                "केवल HTTPS model downloads स्वीकार हैं।"
            )
        }

        val host = url.host.lowercase()

        if (host != "alphacephei.com" &&
            host != "www.alphacephei.com"
        ) {
            throw SecurityException(
                "अनुमति नहीं प्राप्त download host: $host"
            )
        }

        if (url.port != -1 && url.port != 443) {
            throw SecurityException(
                "अनुमति नहीं प्राप्त download port।"
            )
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
                if (closed ||
                    Thread.currentThread().isInterrupted
                ) {
                    throw InterruptedException(
                        "ZIP extraction बंद किया गया।"
                    )
                }

                val entry =
                    zipInput.nextEntry ?: break

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

                val canonicalOutput =
                    outputFile.canonicalFile

                val safePath =
                    canonicalOutput.path.startsWith(
                        canonicalDestination.path +
                                File.separator
                    ) || canonicalOutput == canonicalDestination

                if (!safePath) {
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
                    if (canonicalOutput == canonicalDestination) {
                        throw SecurityException(
                            "ZIP में invalid file path मिला।"
                        )
                    }

                    val parent =
                        canonicalOutput.parentFile

                    if (parent != null &&
                        !parent.exists() &&
                        !parent.mkdirs()
                    ) {
                        throw IllegalStateException(
                            "ZIP parent directory नहीं बनाई जा सकी।"
                        )
                    }

                    FileOutputStream(
                        canonicalOutput
                    ).use { output ->

                        val buffer = ByteArray(16 * 1024)

                        while (true) {
                            if (closed ||
                                Thread.currentThread().isInterrupted
                            ) {
                                throw InterruptedException(
                                    "ZIP extraction बंद किया गया।"
                                )
                            }

                            val count =
                                zipInput.read(buffer)

                            if (count == -1) break

                            totalExtracted += count

                            if (totalExtracted > MAX_EXTRACTED_BYTES) {
                                throw IllegalStateException(
                                    "Extracted model size limit पार कर गया।"
                                )
                            }

                            output.write(
                                buffer,
                                0,
                                count
                            )
                        }

                        output.flush()
                    }
                }

                zipInput.closeEntry()
            }
        }
    }

    /*
     * Recursive search की जगह iterative search।
     * इससे बहुत गहरी ZIP directory पर StackOverflowError
     * आने का खतरा कम होता है।
     */
    private fun findModelDirectory(
        root: File
    ): File? {

        val pending = ArrayDeque<File>()
        pending.add(root)

        var checked = 0

        while (pending.isNotEmpty()) {
            if (closed ||
                Thread.currentThread().isInterrupted
            ) {
                throw InterruptedException(
                    "Model directory search बंद किया गया।"
                )
            }

            val current = pending.removeFirst()

            checked++

            if (checked > MAX_DIRECTORY_SEARCH) {
                throw IllegalStateException(
                    "Model directory में बहुत अधिक folders हैं।"
                )
            }

            if (isValidModelFolder(current)) {
                return current
            }

            val children =
                current.listFiles() ?: continue

            for (child in children) {
                if (child.isDirectory) {
                    pending.addLast(child)
                }
            }
        }

        return null
    }

    /*
     * Directory copy भी iterative है।
     */
    private fun copyDirectory(
        source: File,
        destination: File
    ) {
        if (!source.exists() ||
            !source.isDirectory
        ) {
            throw IllegalArgumentException(
                "Invalid source model directory."
            )
        }

        val pending =
            ArrayDeque<Pair<File, File>>()

        pending.add(
            Pair(source, destination)
        )

        var copiedEntries = 0

        while (pending.isNotEmpty()) {
            if (closed ||
                Thread.currentThread().isInterrupted
            ) {
                throw InterruptedException(
                    "Model copy बंद किया गया।"
                )
            }

            val (currentSource, currentDestination) =
                pending.removeFirst()

            if (!currentDestination.exists() &&
                !currentDestination.mkdirs()
            ) {
                throw IllegalStateException(
                    "Destination model directory नहीं बन सकी।"
                )
            }

            val children =
                currentSource.listFiles()
                    ?: throw IllegalStateException(
                        "Source model directory पढ़ी नहीं जा सकी।"
                    )

            for (child in children) {
                copiedEntries++

                if (copiedEntries > MAX_ZIP_ENTRIES) {
                    throw IllegalStateException(
                        "Model में बहुत अधिक files हैं।"
                    )
                }

                val target =
                    File(currentDestination, child.name)

                if (child.isDirectory) {
                    pending.add(
                        Pair(child, target)
                    )

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
    }

    private fun registerListener(
        modelId: String,
        language: String,
        listener: Listener
    ) {
        synchronized(listenerLock) {
            if (!closed) {
                currentListeners[modelId] =
                    ListenerEntry(language, listener)
            }
        }
    }

    private fun notifyProgress(
        modelId: String,
        percent: Int
    ) {
        if (closed) return

        mainHandler.post {
            if (closed) return@post

            val entry = synchronized(listenerLock) {
                currentListeners[modelId]
            } ?: return@post

            try {
                entry.listener.onProgress(
                    entry.language,
                    percent.coerceIn(0, 100)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Progress listener error", e)
            }
        }
    }

    private fun notifyReady(
        modelId: String,
        model: Model
    ) {
        if (closed) return

        mainHandler.post {
            if (closed) return@post

            val entry = synchronized(listenerLock) {
                currentListeners.remove(modelId)
            } ?: return@post

            try {
                entry.listener.onReady(
                    entry.language,
                    model
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ready listener error", e)
            }
        }
    }

    private fun notifyError(
        modelId: String,
        message: String
    ) {
        if (closed) return

        mainHandler.post {
            if (closed) return@post

            val entry = synchronized(listenerLock) {
                currentListeners.remove(modelId)
            } ?: return@post

            try {
                entry.listener.onError(
                    entry.language,
                    message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error listener callback failed", e)
            }
        }
    }

    private fun postDirectError(
        language: String,
        listener: Listener,
        message: String
    ) {
        mainHandler.post {
            try {
                listener.onError(
                    language,
                    message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Direct error callback failed", e)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }

        synchronized(listenerLock) {
            currentListeners.clear()
        }

        executor.shutdownNow()

        /*
         * Main thread को awaitTermination पर block नहीं करते।
         * Worker बंद होने के बाद ही native models close होंगे।
         */
        val cleanupThread = Thread({

            var terminated = false

            try {
                terminated = executor.awaitTermination(
                    3,
                    TimeUnit.SECONDS
                )
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()

                Log.w(
                    TAG,
                    "Interrupted while waiting for model worker",
                    e
                )
            }

            if (!terminated) {
                Log.w(
                    TAG,
                    "Model worker अभी चल रहा है; models सुरक्षित रखने के लिए close नहीं किए गए।"
                )
                return@Thread
            }

            synchronized(lock) {
                for (model in ownedModels) {
                    try {
                        model.close()
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error closing Vosk model",
                            e
                        )
                    }
                }

                ownedModels.clear()
                loadedModels.clear()
                loadingModels.clear()
            }

        }, "VoskModelCleanup")

        cleanupThread.isDaemon = true
        cleanupThread.start()

        mainHandler.removeCallbacksAndMessages(null)
    }
}
