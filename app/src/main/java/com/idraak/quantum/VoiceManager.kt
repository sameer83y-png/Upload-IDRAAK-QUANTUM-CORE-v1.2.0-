
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * IDRAAK Voice Manager
 *
 * Responsibilities:
 * - Start VoiceForegroundService
 * - Pause voice recognition
 * - Resume voice recognition
 * - Stop voice recognition service
 *
 * SpeechRecognizer creation and lifecycle management
 * belong exclusively to VoiceForegroundService.
 */
class VoiceManager(context: Context) {

    private val appContext: Context =
        context.applicationContext

    companion object {
        private const val TAG = "IDRAAK_VOICE_MANAGER"
    }

    /**
     * Creates an explicit intent for VoiceForegroundService.
     */
    private fun serviceIntent(
        action: String? = null,
        language: String? = null
    ): Intent {

        return Intent(
            appContext,
            VoiceForegroundService::class.java
        ).apply {

            if (action != null) {
                this.action = action
            }

            if (!language.isNullOrBlank()) {
                putExtra(
                    VoiceForegroundService.EXTRA_LANGUAGE,
                    language
                )
            }
        }
    }

    /**
     * Starts the voice foreground service.
     */
    fun start(language: String) {

        val intent = serviceIntent(
            language = language
        )

        startForegroundServiceSafely(intent)
    }

    /**
     * Pauses recognition without explicitly stopping
     * the foreground service.
     */
    fun pause() {

        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_PAUSE
        )

        startServiceSafely(intent)
    }

    /**
     * Resumes recognition with the requested language.
     */
    fun resume(language: String) {

        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_RESUME,
            language = language
        )

        startForegroundServiceSafely(intent)
    }

    /**
     * Stops the voice service.
     */
    fun stop() {

        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_STOP
        )

        startServiceSafely(intent)
    }

    /**
     * Starts a foreground service according to Android version.
     */
    private fun startForegroundServiceSafely(
        intent: Intent
    ) {

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

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Permission denied while starting voice service",
                e
            )

        } catch (e: IllegalStateException) {

            Log.e(
                TAG,
                "Voice service cannot be started in the current app state",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Could not start voice service",
                e
            )
        }
    }

    /**
     * Sends a command to an already running service.
     */
    private fun startServiceSafely(
        intent: Intent
    ) {

        try {

            appContext.startService(intent)

            Log.d(
                TAG,
                "Service command sent: ${intent.action}"
            )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Permission denied while sending service command",
                e
            )

        } catch (e: IllegalStateException) {

            Log.e(
                TAG,
                "Service command rejected by Android",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Could not send service command: ${intent.action}",
                e
            )
        }
    }
}
