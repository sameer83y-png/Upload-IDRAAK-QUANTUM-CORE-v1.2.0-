package com.idraak.quantum

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val TAG = "IDRAAK_CRASH_LOGGER"

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return

        val appContext = context.applicationContext

        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            try {
                val directory = File(
                    appContext.getExternalFilesDir(null),
                    "crash_logs"
                )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.US
                ).format(Date())

                val crashFile = File(
                    directory,
                    "IDRAAK_CRASH_$timestamp.txt"
                )

                crashFile.bufferedWriter().use { writer ->
                    writer.appendLine("IDRAAK QUANTUM CORE CRASH REPORT")
                    writer.appendLine("Date: $timestamp")
                    writer.appendLine("Thread: ${thread.name}")
                    writer.appendLine("Thread ID: ${thread.id}")
                    writer.appendLine()
                    writer.appendLine("EXCEPTION:")
                    writer.appendLine(throwable.toString())
                    writer.appendLine()
                    writer.appendLine("STACK TRACE:")

                    throwable.printStackTrace(
                        java.io.PrintWriter(writer)
                    )
                }

                Log.e(
                    TAG,
                    "Crash report saved: ${crashFile.absolutePath}",
                    throwable
                )

            } catch (error: Exception) {
                Log.e(TAG, "Could not save crash report", error)
            }

            previousHandler?.uncaughtException(thread, throwable)
        }

        installed = true

        Log.i(TAG, "Crash logger installed")
    }
}
