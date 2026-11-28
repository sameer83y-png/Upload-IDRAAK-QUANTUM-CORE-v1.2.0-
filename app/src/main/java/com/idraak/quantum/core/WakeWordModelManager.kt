package com.idraak.quantum.core

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads and safely extracts the official sherpa-onnx English KWS model once. */
class WakeWordModelManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "wake-word")
    private val modelDir = File(root, MODEL_DIR)
    private val archive = File(root, "kws-model.tar.bz2")
    private val partial = File(root, "kws-model.tar.bz2.part")

    data class ModelFiles(
        val encoder: File, val decoder: File, val joiner: File,
        val tokens: File, val keywords: File
    )

    @Synchronized
    fun prepare(): ModelFiles {
        root.mkdirs()
        findFiles()?.let { return it }
        download()
        extract()
        return findFiles() ?: throw IllegalStateException("Wake-word model files are incomplete.")
    }

    private fun download() {
        partial.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 90000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Idraak-Quantum")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Wake-word model download failed. Check internet.")
            }
            val expected = connection.contentLengthLong
            if (expected > MAX_ARCHIVE || expected in 1 until MIN_ARCHIVE) {
                throw IllegalStateException("Unexpected wake-word model size.")
            }
            var received = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > MAX_ARCHIVE) throw IllegalStateException("Wake-word model is too large.")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (received < MIN_ARCHIVE) throw IllegalStateException("Wake-word download is incomplete.")
            if (!partial.renameTo(archive)) throw IllegalStateException("Could not save wake-word model.")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun extract() {
        val staging = File(root, "extracting")
        staging.deleteRecursively()
        staging.mkdirs()
        var total = 0L
        try {
            BufferedInputStream(archive.inputStream()).use { input ->
                BZip2CompressorInputStream(input, true).use { bz2 ->
                    TarArchiveInputStream(bz2).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            if (!entry.isFile) continue
                            val relative = entry.name.replace('\\\\', '/')
                            if (relative.startsWith("/") || relative.split('/').any { it == ".." }) {
                                throw IllegalStateException("Unsafe path in wake-word archive.")
                            }
                            val destination = File(staging, relative)
                            if (!destination.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                                throw IllegalStateException("Unsafe wake-word archive path.")
                            }
                            if (entry.size < 0 || entry.size > MAX_FILE) throw IllegalStateException("Wake-word file too large.")
                            total += entry.size
                            if (total > MAX_EXPANDED) throw IllegalStateException("Wake-word archive expanded beyond limit.")
                            destination.parentFile?.mkdirs()
                            FileOutputStream(destination).use { output ->
                                val buffer = ByteArray(65536)
                                var remaining = entry.size
                                while (remaining > 0) {
                                    val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (count < 0) throw IllegalStateException("Truncated wake-word archive.")
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                            }
                        }
                    }
                }
            }
            val stagedModel = File(staging, MODEL_DIR)
            if (findFiles(stagedModel) == null) throw IllegalStateException("Required wake-word model files are missing.")
            modelDir.deleteRecursively()
            if (!stagedModel.renameTo(modelDir)) throw IllegalStateException("Could not install wake-word model.")
            archive.delete()
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun findFiles(base: File = modelDir): ModelFiles? {
        if (!base.isDirectory) return null
        fun file(name: String) = File(base, name).takeIf { it.isFile && it.length() > 100 }
        val encoder = file("encoder-epoch-12-avg-2-chunk-16-left-64.onnx") ?: return null
        val decoder = file("decoder-epoch-12-avg-2-chunk-16-left-64.onnx") ?: return null
        val joiner = file("joiner-epoch-12-avg-2-chunk-16-left-64.onnx") ?: return null
        val tokens = file("tokens.txt") ?: return null
        val keywords = file("keywords.txt") ?: return null
        return ModelFiles(encoder, decoder, joiner, tokens, keywords)
    }

    companion object {
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
        private const val MIN_ARCHIVE = 5L * 1024L * 1024L
        private const val MAX_ARCHIVE = 40L * 1024L * 1024L
        private const val MAX_FILE = 30L * 1024L * 1024L
        private const val MAX_EXPANDED = 60L * 1024L * 1024L
    }
}
