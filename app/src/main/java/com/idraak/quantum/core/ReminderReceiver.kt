package com.idraak.quantum.core

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Base64

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_MESSAGE = "com.idraak.quantum.REMINDER_MESSAGE"
        const val EXTRA_ID = "com.idraak.quantum.REMINDER_ID"
        private const val CHANNEL_ID = "idraak_reminders"
        private const val PREFS = "idraak_reminders"
        private const val RECORDS = "scheduled"
        private const val MAX_REMINDER_LENGTH = 500

        fun schedule(context: Context, triggerAtMillis: Long, message: String) {
            val cleanMessage = message.trim()
            require(cleanMessage.isNotEmpty()) { "Reminder text cannot be empty." }
            require(cleanMessage.length <= MAX_REMINDER_LENGTH) {
                "Reminder text must be $MAX_REMINDER_LENGTH characters or fewer."
            }
            require(!LocalMemoryStore.containsCredentialMaterial(cleanMessage)) {
                "Passwords, PINs, one-time codes, API keys, and access tokens cannot be saved in reminders."
            }
            val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
            val encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(cleanMessage.toByteArray(Charsets.UTF_8))
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val records = prefs.getStringSet(RECORDS, emptySet()).orEmpty().toMutableSet()
            records.add("$id|$triggerAtMillis|$encoded")
            prefs.edit().putStringSet(RECORDS, records).apply()
            scheduleStored(context, id, triggerAtMillis, cleanMessage)
        }

        private fun scheduleStored(context: Context, id: Int, triggerAtMillis: Long, message: String) {
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_MESSAGE, message)
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1000L),
                pending
            )
        }

        private fun restoreAfterBoot(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val records = prefs.getStringSet(RECORDS, emptySet()).orEmpty().toList()
            for (record in records) {
                val parts = record.split("|", limit = 3)
                if (parts.size != 3) continue
                val id = parts[0].toIntOrNull() ?: continue
                val trigger = parts[1].toLongOrNull() ?: continue
                val message = try {
                    String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8)
                } catch (_: Exception) {
                    continue
                }
                scheduleStored(context, id, trigger, message)
            }
        }

        private fun removeStored(context: Context, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val records = prefs.getStringSet(RECORDS, emptySet()).orEmpty()
                .filterNot { it.substringBefore("|").toIntOrNull() == id }
                .toSet()
            prefs.edit().putStringSet(RECORDS, records).apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            restoreAfterBoot(context)
            return
        }
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
            ?: "آپ کی یاد دہانی کا وقت ہو گیا ہے۔"
        removeStored(context, id)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Idraak reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Idraak Reminder")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        manager.notify(id, builder.build())
    }
}
