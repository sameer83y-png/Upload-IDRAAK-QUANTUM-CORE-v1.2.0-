package com.idraak.quantum.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

class AndroidActionExecutor(private val context: Context) {
    fun execute(action: CommandAction): String = try {
        when (action) {
            CommandAction.None -> "کوئی اضافی فون ایکشن درکار نہیں تھا۔"
            is CommandAction.OpenUrl -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "لنک کھول دیا گیا۔"
            }
            is CommandAction.OpenApp -> openApp(action.appName)
            is CommandAction.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(action.number)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "فون ڈائلر کھول دیا گیا ہے۔ کال کرنے سے پہلے نمبر چیک کریں۔"
            }
            is CommandAction.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "الارم بنانے کی درخواست گھڑی کی ایپ کو بھیج دی گئی۔ اگر تصدیق مانگی جائے تو اسے منظور کریں۔"
            }
            is CommandAction.AdjustVolume -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = when (action.direction) {
                    VolumeDirection.UP -> AudioManager.ADJUST_RAISE
                    VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
                    VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
                }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                when (action.direction) {
                    VolumeDirection.UP -> "میڈیا کی آواز بڑھا دی گئی۔"
                    VolumeDirection.DOWN -> "میڈیا کی آواز کم کر دی گئی۔"
                    VolumeDirection.MUTE -> "میڈیا کی آواز خاموش کر دی گئی۔"
                }
            }
            CommandAction.OpenAlarm -> {
                context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "الارم کھول دیا گیا۔"
            }
            CommandAction.OpenSettings -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "فون کی سیٹنگز کھول دی گئی ہیں۔"
            }
            CommandAction.OpenCamera -> {
                context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "کیمرہ کھول دیا گیا۔"
            }
        }
    } catch (_: ActivityNotFoundException) {
        "اس کام کے لیے کوئی compatible ایپ نہیں ملی۔"
    } catch (_: SecurityException) {
        "Android نے مطلوبہ اجازت نہ ہونے کی وجہ سے یہ کام روک دیا۔"
    } catch (error: Exception) {
        "یہ کام مکمل نہیں ہو سکا: " + (error.localizedMessage ?: "نامعلوم خرابی")
    }

    private fun openApp(requestedName: String): String {
        val target = requestedName.trim().lowercase()
        val candidates = when {
            target.contains("youtube") || target.contains("یوٹیوب") ->
                listOf("com.google.android.youtube")
            target.contains("chrome") || target.contains("browser") || target.contains("براوزر") ->
                listOf("com.android.chrome")
            target.contains("camera") || target.contains("کیمرہ") ->
                listOf("com.android.camera2", "com.google.android.GoogleCamera")
            target.contains("settings") || target.contains("سیٹنگ") ->
                emptyList()
            target.contains("chatgpt") ->
                listOf("com.openai.chatgpt")
            target.contains("gemini") ->
                listOf("com.google.android.apps.bard")
            else -> emptyList()
        }
        if (target.contains("settings") || target.contains("سیٹنگ")) return execute(CommandAction.OpenSettings)
        val launchIntent = candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return "یہ ایپ انسٹال نہیں یا اس کا لانچر دستیاب نہیں۔"
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return "ایپ کھول دی گئی۔"
    }
}
