package com.idraak.quantum.core

import android.content.Context
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the multilingual Whisper tiny model once.
 * Recognition itself runs locally; the first model download needs internet.
 */
class OfflineWhisperModelManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "offline-speech")
    private val modelDir = File(root, "whisper-tiny")
    private val archiveFile = File(root, "whisper-tiny.tar.bz2")
    private val archiveTemp = File(root, "whisper-tiny.tar.bz2.part")
    private val extractTemp = File(root, "whisper-tiny.extracting")

    data class ModelFiles(val encoder: File, val decoder: File, val tokens: File)

    fun prepare(onProgress: (Int) -> Unit = {}): ModelFiles {
        root.mkdirs()
        findModelFiles()?.let { return it }

        downloadModel(onProgress)
        extractModel()
        return findModelFiles()
            ?: throw IllegalStateException("آف لائن وائس ماڈل مکمل نہیں ہوا۔ دوبارہ ڈاؤن لوڈ کریں۔")
    }

    private fun downloadModel(onProgress: (Int) -> Unit) {
        if (archiveFile.exists() && archiveFile.length() > 1_000_000L) return
        archiveTemp.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Idraak-Quantum-Offline-Voice")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("وائس ماڈل ڈاؤن لوڈ نہیں ہو سکا۔ انٹرنیٹ چیک کریں۔")
            }
            val expected = connection.contentLengthLong
            if (expected > MAX_ARCHIVE_BYTES) {
                throw IllegalStateException("وائس ماڈل کا سائز محفوظ حد سے زیادہ ہے۔")
            }

            var received = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(archiveTemp)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > MAX_ARCHIVE_BYTES) {
                            throw IllegalStateException("وائس ماڈل ڈاؤن لوڈ حد سے بڑا ہے۔")
                        }
                        output.write(buffer, 0, count)
                        if (expected > 0L) {
                            onProgress(((received * 100L) / expected).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (received < 10_000_000L) {
                throw IllegalStateException("وائس ماڈل کی فائل نامکمل ہے۔")
            }
            if (!archiveTemp.renameTo(archiveFile)) {
                throw IllegalStateException("وائس ماڈل فائل محفوظ نہیں ہو سکی۔")
            }
        } finally {
            connection.disconnect()
            archiveTemp.delete()
        }
    }

    private fun extractModel() {
        extractTemp.deleteRecursively()
        extractTemp.mkdirs()
        var expandedBytes = 0L

        try {
            BufferedInputStream(archiveFile.inputStream()).use { buffered ->
                BZip2CompressorInputStream(buffered, true).use { bz2 ->
                    TarArchiveInputStream(bz2).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            if (!entry.isFile) continue

                            val destination = File(extractTemp, entry.name)
                            val safeRoot = extractTemp.canonicalPath + File.separator
                            if (!destination.canonicalPath.startsWith(safeRoot)) {
                                throw IllegalStateException("وائس ماڈل آرکائیو میں غلط فائل راستہ ہے۔")
                            }
                            if (entry.size < 0L || entry.size > MAX_MODEL_BYTES) {
                                throw IllegalStateException("وائس ماڈل کی فائل حد سے بڑی ہے۔")
                            }
                            expandedBytes += entry.size
                            if (expandedBytes > MAX_MODEL_BYTES) {
                                throw IllegalStateException("وائس ماڈل کا مجموعی سائز حد سے زیادہ ہے۔")
                            }

                            destination.parentFile?.mkdirs()
                            FileOutputStream(destination).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var remaining = entry.size
                                while (remaining > 0L) {
                                    val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (count < 0) throw IllegalStateException("وائس ماڈل آرکائیو نامکمل ہے۔")
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                            }
                        }
                    }
                }
            }

            if (findModelFiles(extractTemp) == null) {
                throw IllegalStateException("ڈاؤن لوڈ شدہ وائس ماڈل میں ضروری فائلیں نہیں ملیں۔")
            }
            modelDir.deleteRecursively()
            if (!extractTemp.renameTo(modelDir)) {
                throw IllegalStateException("وائس ماڈل انسٹال نہیں ہو سکا۔")
            }
            if (findModelFiles() == null) {
                throw IllegalStateException("وائس ماڈل انسٹالیشن مکمل نہیں ہوئی۔")
            }
            archiveFile.delete()
        } catch (error: Exception) {
            extractTemp.deleteRecursively()
            throw error
        }
    }

    private fun findModelFiles(base: File = modelDir): ModelFiles? {
        if (!base.exists()) return null
        val encoder = base.walkTopDown().firstOrNull { it.isFile && it.name == "tiny-encoder.onnx" }
        val decoder = base.walkTopDown().firstOrNull { it.isFile && it.name == "tiny-decoder.onnx" }
        val tokens = base.walkTopDown().firstOrNull { it.isFile && it.name == "tiny-tokens.txt" }
        if (encoder == null || decoder == null || tokens == null) return null
        if (encoder.length() < 1_000_000L || decoder.length() < 1_000_000L || tokens.length() < 10_000L) return null
        return ModelFiles(encoder, decoder, tokens)
    }

    companion object {
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
        private const val MAX_ARCHIVE_BYTES = 150L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 350L * 1024L * 1024L
    }
}
