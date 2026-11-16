package com.idraak.quantum

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale
import kotlin.math.roundToInt

class SystemController(context: Context) {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    fun setMediaVolume(percent: Int): Boolean = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return false
        val volume = (max * percent.coerceIn(0, 100) / 100f).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == volume
    } catch (_: Exception) {
        false
    }

    fun increaseMediaVolume(): Boolean = try {
        val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            0
        )
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > before
    } catch (_: Exception) {
        false
    }

    fun decreaseMediaVolume(): Boolean = try {
        val before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            0
        )
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < before
    } catch (_: Exception) {
        false
    }

    fun muteMedia(): Boolean = setMediaVolume(0)

    fun getMediaVolumePercent(): Int? = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return null
        (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max)
            .roundToInt().coerceIn(0, 100)
    } catch (_: Exception) {
        null
    }

    fun setRingVolume(percent: Int): Boolean = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (max <= 0) return false
        val volume = (max * percent.coerceIn(0, 100) / 100f).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_RING, volume, 0)
        audioManager.getStreamVolume(AudioManager.STREAM_RING) == volume
    } catch (_: Exception) {
        false
    }

    fun getRingVolumePercent(): Int? = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (max <= 0) return null
        (audioManager.getStreamVolume(AudioManager.STREAM_RING) * 100f / max)
            .roundToInt().coerceIn(0, 100)
    } catch (_: Exception) {
        null
    }

    fun canChangeRingerMode(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            notificationManager.isNotificationPolicyAccessGranted
    } catch (_: Exception) {
        false
    }

    fun openNotificationPolicyAccessSettings(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            false
        } else {
            appContext.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    } catch (_: Exception) {
        false
    }

    fun setRingerMode(mode: String): Boolean = try {
        val ringerMode = when (mode.trim().lowercase(Locale.ROOT)) {
            "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
            "vibrate", "vibration" -> AudioManager.RINGER_MODE_VIBRATE
            "normal", "ring", "loud" -> AudioManager.RINGER_MODE_NORMAL
            else -> return false
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ringerMode != AudioManager.RINGER_MODE_NORMAL &&
            !canChangeRingerMode()
        ) return false

        audioManager.ringerMode = ringerMode
        audioManager.ringerMode == ringerMode
    } catch (_: Exception) {
        false
    }

    fun getRingerMode(): String = try {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
    } catch (_: Exception) {
        "unknown"
    }

    fun canModifySystemSettings(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.System.canWrite(appContext)
    } catch (_: Exception) {
        false
    }

    fun openModifySystemSettingsPermission(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            false
        } else {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }
    } catch (_: Exception) {
        false
    }

    fun setBrightness(percent: Int): Boolean = try {
        if (!canModifySystemSettings()) return false
        val brightness =
            (255 * percent.coerceIn(0, 100) / 100f).roundToInt()
                .coerceIn(0, 255)

        val updated = Settings.System.putInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness
        )
        if (!updated) return false

        getBrightness() == (brightness * 100f / 255f).roundToInt()
    } catch (_: Exception) {
        false
    }

    fun getBrightness(): Int? = try {
        val brightness = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        )
        (brightness.coerceIn(0, 255) * 100f / 255f)
            .roundToInt().coerceIn(0, 100)
    } catch (_: Exception) {
        null
    }

    fun openInternetSettings(): Boolean =
        openSettingsIntent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
        )

    fun openBluetoothSettings(): Boolean =
        openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun openDisplaySettings(): Boolean =
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)

    fun openSoundSettings(): Boolean =
        openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)

    fun openBatterySettings(): Boolean =
        openSettingsIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    fun openLocationSettings(): Boolean =
        openSettingsIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    fun openDateTimeSettings(): Boolean =
        openSettingsIntent(Settings.ACTION_DATE_SETTINGS)

    fun openAccessibilitySettings(): Boolean =
        openSettingsIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openSettingsHome(): Boolean =
        openSettingsIntent(Settings.ACTION_SETTINGS)

    private fun openSettingsIntent(action: String): Boolean = try {
        appContext.startActivity(
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }
}
