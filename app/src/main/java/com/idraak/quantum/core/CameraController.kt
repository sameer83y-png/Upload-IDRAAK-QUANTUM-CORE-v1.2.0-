package com.idraak.quantum.core

import android.content.Context
import android.content.Intent
import android.provider.MediaStore

/**
 * Safe camera hand-off controller.
 *
 * Idraak opens the device's camera app; the user reviews and starts/saves
 * capture inside that app. It does not record in the background.
 */
class CameraController(context: Context) {
    private val appContext = context.applicationContext

    fun openPhotoCamera(): String {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return "کیمرہ کھول دیا گیا ہے۔ تصویر لینے اور محفوظ کرنے کا عمل کیمرہ ایپ میں مکمل کریں۔"
    }

    fun openVideoCamera(): String {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return "ویڈیو کیمرہ کھول دیا گیا ہے۔ ریکارڈنگ شروع اور محفوظ کرنے کا اختیار آپ کے پاس رہے گا۔"
    }
}
