
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * IDRAAK Voice Manager
 *
 * जिम्मेदारी:
 * - VoiceForegroundService शुरू करना
 * - Voice recognition pause करना
 * - Voice recognition resume करना
 * - Voice service बंद करना
 *
 * SpeechRecognizer का निर्माण और वास्तविक नियंत्रण
 * VoiceForegroundService की जिम्मेदारी है।
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
     * पहली बार Voice Foreground Service शुरू करता है।
     */
    fun start(language: String) {
        val intent = serviceIntent(
            language = language
        )

        startForegroundServiceSafely(intent)
    }

    /**
     * Recognition pause करता है।
     * Foreground service बंद नहीं होती।
     */
    fun pause() {
        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_PAUSE
        )

        startServiceSafely(intent)
    }

    /**
     * Recognition दोबारा शुरू करता है।
     */
    fun resume(language: String) {
        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_RESUME,
            language = language
        )

        startForegroundServiceSafely(intent)
    }

    /**
     * Voice Foreground Service बंद करता है।
     */
    fun stop() {
        val intent = serviceIntent(
            action = VoiceForegroundService.ACTION_STOP
        )

        startServiceSafely(intent)
    }

    /**
     * Android version के अनुसार foreground service शुरू करता है।
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

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Could not send foreground service command: ${intent.action ?: "START"}",
                e
            )
        }
    }

    /**
     * पहले से चल रही service को command भेजता है।
     */
    private fun startServiceSafely(intent: Intent) {
        try {
            appContext.startService(intent)

            Log.d(
                TAG,
                "Service command sent: ${intent.action}"
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
