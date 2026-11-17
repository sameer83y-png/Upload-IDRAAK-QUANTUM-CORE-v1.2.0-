package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

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
 *
 * This class does not directly control AudioRecord, Vosk,
 * SpeechRecognizer or TextToSpeech.
 */
class VoiceManager(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "IDRAAK_VOICE_MANAGER"
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
     *
     * The service loads the required Vosk model
     * and starts continuous audio recognition.
     */
    fun start(language: String) {

        if (language.isBlank()) {
            Log.e(TAG, "Cannot start voice service: language is empty")
            return
        }

        startForegroundServiceSafely(
            serviceIntent(language = language)
        )
    }

    /**
     * Pauses microphone recognition.
     *
     * The foreground service remains responsible
     * for managing its lifecycle and resources.
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
     *
     * The selected language is passed to the service.
     */
    fun resume(language: String) {

        if (language.isBlank()) {
            Log.e(TAG, "Cannot resume voice service: language is empty")
            return
        }

        startForegroundServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_RESUME,
                language = language
            )
        )
    }

    /**
     * Requests the voice foreground service to stop.
     *
     * The service itself performs microphone,
     * Vosk engine and model cleanup.
     */
    fun stop() {

        startServiceSafely(
            serviceIntent(
                action = VoiceForegroundService.ACTION_STOP
            )
        )
    }

    /**
     * Starts a foreground service safely.
     *
     * Required for Android Oreo and newer.
     */
    private fun startForegroundServiceSafely(intent: Intent) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }

            Log.d(
                TAG,
                "Foreground service command sent: ${intent.action ?: "START"}"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to start foreground service: ${intent.action ?: "START"}",
                exception
            )
        }
    }

    /**
     * Sends commands to the existing service.
     *
     * Used for pause and stop operations.
     */
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
        }
    }
}
