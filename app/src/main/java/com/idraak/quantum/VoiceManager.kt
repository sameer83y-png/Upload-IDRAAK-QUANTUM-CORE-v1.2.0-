package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build

/**

- IDRAAK Voice Manager

- 

- जिम्मेदारी:

- - Voice service शुरू करना

- - Listening pause करना

- - Listening resume करना

- - Voice service बंद करना

- 

- SpeechRecognizer का निर्माण और नियंत्रण

- VoiceForegroundService की जिम्मेदारी है।
  */
  class VoiceManager(context: Context) {

  private val appContext = context.applicationContext

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

  - पहली बार Voice Service शुरू करता है।
    */
    fun start(language: String) {
    val intent = serviceIntent(
    language = language
    )

    startForegroundServiceSafely(intent)
    }

  /**

  - Recognition रोकता है, लेकिन foreground service

  - को बंद नहीं करता।
    */
    fun pause() {
    val intent = serviceIntent(
    action = VoiceForegroundService.ACTION_PAUSE
    )

    appContext.startService(intent)
    }

  /**

  - Recognition दोबारा शुरू करता है।
    */
    fun resume(language: String) {
    val intent = serviceIntent(
    action = VoiceForegroundService.ACTION_RESUME,
    language = language
    )

    startForegroundServiceSafely(intent)
    }

  /**

  - Voice Service पूरी तरह बंद करता है।
    */
    fun stop() {
    val intent = serviceIntent(
    action = VoiceForegroundService.ACTION_STOP
    )

    appContext.startService(intent)
    }

  private fun startForegroundServiceSafely(intent: Intent) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
  appContext.startForegroundService(intent)
  } else {
  appContext.startService(intent)
  }
  }
  }
