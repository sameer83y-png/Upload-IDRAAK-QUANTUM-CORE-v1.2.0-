
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

class SystemController(context: Context) {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Set media volume from 0 to 100.
     */
    fun setMediaVolume(percent: Int): Boolean {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(
                AudioManager.STREAM_MUSIC
            )

            val volume = (
                maxVolume * percent.coerceIn(0, 100) / 100f
            ).toInt()

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volume,
                0
            )

            true
        } catch (_: Exception) {
            false
        }
    }

    fun increaseMediaVolume(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                0
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun decreaseMediaVolume(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                0
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun muteMedia(): Boolean {
        return setMediaVolume(0)
    }

    /**
     * Set notification/ring volume from 0 to 100.
     */
    fun setRingVolume(percent: Int): Boolean {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(
                AudioManager.STREAM_RING
            )

            val volume = (
                maxVolume * percent.coerceIn(0, 100) / 100f
            ).toInt()

            audioManager.setStreamVolume(
                AudioManager.STREAM_RING,
                volume,
                0
            )

            true
        } catch (_: Exception) {
            false
        }
    }

    fun setRingerMode(mode: String): Boolean {
        return try {
            val ringerMode = when (mode.lowercase()) {
                "silent", "mute" ->
                    AudioManager.RINGER_MODE_SILENT

                "vibrate" ->
                    AudioManager.RINGER_MODE_VIBRATE

                "normal", "ring" ->
                    AudioManager.RINGER_MODE_NORMAL

                else -> return false
            }

            audioManager.ringerMode = ringerMode
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getRingerMode(): String {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
    }

    /**
     * Change screen brightness.
     * Android requires the user to grant Modify System Settings access.
     */
    fun canModifySystemSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(appContext)
        } else {
            true
        }
    }

    fun openModifySystemSettingsPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${appContext.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                appContext.startActivity(intent)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun setBrightness(percent: Int): Boolean {
        return try {
            if (!canModifySystemSettings()) {
                return false
            }

            val brightness = (
                255 * percent.coerceIn(0, 100) / 100f
            ).toInt()

            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
        } catch (_: Exception) {
            false
        }
    }

    fun getBrightness(): Int? {
        return try {
            val brightness = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )

            (brightness * 100) / 255
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Open Android's Internet/Wi-Fi controls.
     * Direct Wi-Fi switching is restricted on newer Android versions.
     */
    fun openInternetSettings(): Boolean {
        return openSettingsIntent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
        )
    }

    fun openBluetoothSettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_BLUETOOTH_SETTINGS
        )
    }

    fun openDisplaySettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_DISPLAY_SETTINGS
        )
    }

    fun openSoundSettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_SOUND_SETTINGS
        )
    }

    fun openBatterySettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_BATTERY_SAVER_SETTINGS
        )
    }

    fun openLocationSettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_LOCATION_SOURCE_SETTINGS
        )
    }

    fun openDateTimeSettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_DATE_SETTINGS
        )
    }

    fun openAccessibilitySettings(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
    }

    fun openSettingsHome(): Boolean {
        return openSettingsIntent(
            Settings.ACTION_SETTINGS
        )
    }

    private fun openSettingsIntent(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
