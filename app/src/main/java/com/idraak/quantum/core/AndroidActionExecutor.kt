package com.idraak.quantum.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.Manifest
import android.provider.ContactsContract
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
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
            is CommandAction.OpenUrlInApp -> {
                val packageName = AppPluginRegistry.findConnectedPackage(context, action.appName)
                when {
                    packageName == null ->
                        "یہ ایپ Idraak Plugin Manager میں connected نہیں۔ پہلے اسے connect کریں۔"
                    !AppPluginRegistry.supportsWebLinks(context, packageName) ->
                        "یہ ایپ Android کے ذریعے ویب لنکس کھولنے کی سہولت نہیں دیتی۔"
                    else -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                            .setPackage(packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "لنک ${action.appName} میں کھول دیا گیا۔"
                    }
                }
            }
            is CommandAction.OpenApp -> openApp(action.appName)
            is CommandAction.ShareText -> {
                val shareIntent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, action.text)
                context.startActivity(
                    Intent.createChooser(shareIntent, "پیغام بھیجنے کے لیے ایپ منتخب کریں")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "شیئر مینو کھل گیا ہے۔ ایپ منتخب کرکے بھیجنے سے پہلے پیغام چیک کریں۔"
            }
            is CommandAction.ShareTextToApp -> {
                val packageName = AppPluginRegistry.findConnectedPackage(context, action.appName)
                when {
                    packageName == null ->
                        "یہ ایپ ابھی Idraak Plugin Manager میں connected نہیں۔ پہلے اسے connect کریں۔"
                    !AppPluginRegistry.supportsTextShare(context, packageName) ->
                        "یہ ایپ Android کے ذریعے text sharing سپورٹ نہیں کرتی۔ عام share menu یا Accessibility استعمال کریں۔"
                    else -> {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .setPackage(packageName)
                            .putExtra(Intent.EXTRA_TEXT, action.text)
                        context.startActivity(
                            Intent.createChooser(shareIntent, "${action.appName} میں بھیجنے سے پہلے چیک کریں")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "ایپ کا شیئر مینو کھل گیا ہے۔ پیغام چیک کرکے خود Send کریں۔"
                    }
                }
            }
            is CommandAction.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(action.number)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "فون ڈائلر کھول دیا گیا ہے۔ کال کرنے سے پہلے نمبر چیک کریں۔"
            }
            is CommandAction.DialContact -> dialContact(action.contactName)
            is CommandAction.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "الارم بنانے کی درخواست گھڑی کی ایپ کو بھیج دی گئی۔ اگر تصدیق مانگی جائے تو اسے منظور کریں۔"
            }
            is CommandAction.ScheduleReminder -> {
                ReminderReceiver.schedule(context, action.triggerAtMillis, action.message)
                "یاد دہانی محفوظ کر دی گئی ہے۔ مقررہ وقت پر نوٹیفکیشن آئے گا۔"
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
            CommandAction.OpenWifiSettings -> openSettingsPage(Settings.ACTION_WIFI_SETTINGS, "وائی فائی سیٹنگز")
            CommandAction.OpenBluetoothSettings -> openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, "بلوٹوتھ سیٹنگز")
            CommandAction.OpenDisplaySettings -> openSettingsPage(Settings.ACTION_DISPLAY_SETTINGS, "ڈسپلے سیٹنگز")
            CommandAction.OpenSoundSettings -> openSettingsPage(Settings.ACTION_SOUND_SETTINGS, "ساؤنڈ سیٹنگز")
            CommandAction.OpenBatterySettings -> openSettingsPage(Settings.ACTION_BATTERY_SAVER_SETTINGS, "بیٹری سیٹنگز")
            CommandAction.OpenCamera -> CameraController(context).openPhotoCamera()
            CommandAction.OpenVideoCamera -> CameraController(context).openVideoCamera()
        }
    } catch (_: ActivityNotFoundException) {
        "اس کام کے لیے کوئی compatible ایپ نہیں ملی۔"
    } catch (_: SecurityException) {
        "Android نے مطلوبہ اجازت نہ ہونے کی وجہ سے یہ کام روک دیا۔"
    } catch (error: Exception) {
        "یہ کام مکمل نہیں ہو سکا: " + (error.localizedMessage ?: "نامعلوم خرابی")
    }

    private fun dialContact(requestedName: String): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "رابطے تلاش کرنے کے لیے Idraak کو Contacts permission دیں، پھر یہی حکم دوبارہ کہیں۔"
        }
        val wanted = normalizeContactName(requestedName)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val matches = linkedMapOf<String, String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$requestedName%"),
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex >= 0 && numberIndex >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex).orEmpty()
                        val number = cursor.getString(numberIndex).orEmpty()
                        if (normalizeContactName(name) == wanted && number.isNotBlank()) {
                            matches[number] = name
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            return "Android نے Contacts permission روک دی۔ Settings میں اجازت فعال کرکے دوبارہ کہیں۔"
        } catch (error: Exception) {
            return "رابطے تلاش نہیں ہو سکے: " + (error.localizedMessage ?: "نامعلوم خرابی")
        }
        return when {
            matches.isEmpty() -> "اس نام کا رابطہ نہیں ملا۔ نام واضح بتائیں یا Contacts permission چیک کریں۔"
            matches.size > 1 -> "اس رابطے کے ایک سے زیادہ نمبر ہیں۔ براہ کرم نمبر واضح کرکے دوبارہ کہیں۔"
            else -> {
                val number = matches.keys.first()
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "رابطے کا نمبر ڈائلر میں کھول دیا ہے۔ کال کرنے سے پہلے نمبر چیک کریں۔"
            }
        }
    }

    private fun normalizeContactName(value: String): String =
        value.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun openSettingsPage(action: String, label: String): String {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "$label کھول دی گئی ہیں۔"
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
            target.contains("whatsapp") || target.contains("واٹس ایپ") ->
                listOf("com.whatsapp")
            target.contains("instagram") || target.contains("انسٹاگرام") ->
                listOf("com.instagram.android")
            target.contains("facebook") || target.contains("فیس بک") ->
                listOf("com.facebook.katana")
            target.contains("telegram") || target.contains("ٹیلیگرام") ->
                listOf("org.telegram.messenger")
            target.contains("maps") || target.contains("نقشہ") ->
                listOf("com.google.android.apps.maps")
            target.contains("gmail") ->
                listOf("com.google.android.gm")

            else -> emptyList()
        }
        if (target.contains("settings") || target.contains("سیٹنگ")) return execute(CommandAction.OpenSettings)
        val packageManager = context.packageManager
        val knownIntent = candidates.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        val launchIntent = knownIntent ?: findInstalledLauncherApp(target)
            ?: return "یہ ایپ نہیں ملی۔ ایپ کا پورا نام بتائیں یا پہلے اسے انسٹال کریں۔"
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return "ایپ کھول دی گئی۔"
    }

    /**
     * Finds user-visible launcher apps without requesting QUERY_ALL_PACKAGES.
     * If more than one app matches a partial name, asks for a clearer app name.
     */
    private fun findInstalledLauncherApp(requestedName: String): Intent? {
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(
            launcherQuery,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        ).mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
            if (label.isBlank()) null else label to resolveInfo
        }
        val normalized = requestedName.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        val exact = matches.filter {
            it.first.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "") == normalized
        }
        val candidates = if (exact.isNotEmpty()) exact else matches.filter {
            val label = it.first.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            label.startsWith(normalized) || normalized.startsWith(label)
        }
        if (candidates.size != 1) return null
        val info = candidates.first().second
        return context.packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)
    }
}
