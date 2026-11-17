package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * IDRAAK Voice Manager
 *
 * Responsibilities:
 * - Start VoiceForegroundService.
 * - Pause continuous voice recognition.
 * - Resume continuous voice recognition.
 * - Stop the voice service.
 *
 * Vosk model loading, microphone capture and speech recognition
 * are exclusively managed by VoiceForegroundService.
 */
class VoiceManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "IDRAAK_VOICE_MANAGER"
    }

    private fun showMessage(message: String) {
        mainHandler.post {
            Toast.makeText(
                appContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun serviceIntent(
        action: String? = null,
        language: String? = null
    ): Intent {
        return Intent(
            appContext,
            VoiceForegroundService::class.java
        ).apply {
            if (!action.isNullOrBlank()) {
                this.action = action
            }

            if (!language.isNullOrBlank()) {
                putExtra(
                    VoiceForegroundService.EXTRA_LANGUAGE,
                    language.trim()
                )
            }
        }
    }

    /**
     * Starts the voice foreground service.
     */
    fun start(language: String) {

        if (language.isBlank()) {
            Log.e(TAG, "Cannot start voice service: language is empty")
            showMessage("IDRAAK ERROR: Voice language is empty")
            return
        }

        startForegroundServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_START,
                language = language
            ),
            true
        )
    }

    /**
     * Pauses microphone recognition.
     */
    fun pause() {
        startServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_PAUSE
            )
        )
    }

    /**
     * Resumes continuous voice recognition.
     */
    fun resume(language: String) {

        if (language.isBlank()) {
            Log.e(TAG, "Cannot resume voice service: language is empty")
            showMessage("IDRAAK ERROR: Voice language is empty")
            return
        }

        startForegroundServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_RESUME,
                language = language
            ),
            true
        )
    }

    /**
     * Stops the voice service.
     */
    fun stop() {
        startServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_STOP
            )
        )
    }

    private fun startForegroundServiceSafely(
        intent: Intent,
        showDiagnostic: Boolean = false
    ) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }

            Log.d(
                TAG,
                "Foreground service command sent: ${intent.action}"
            )

            if (showDiagnostic) {
                showMessage(
                    "IDRAAK: Voice service start request sent"
                )
            }

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to start foreground service: ${intent.action}",
                exception
            )

            showMessage(
                "IDRAAK START ERROR: " +
                    (exception.message ?: exception.javaClass.simpleName)
            )
        }
    }

    private fun startServiceSafely(intent: Intent) {

        try {

            appContext.startService(intent)

            Log.d(
                TAG,
                "Service command sent: ${intent.action}"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to send service command: ${intent.action}",
                exception
            )

            showMessage(
                "IDRAAK SERVICE ERROR: " +
                    (exception.message ?: exception.javaClass.simpleName)
            )
        }
    }
}
