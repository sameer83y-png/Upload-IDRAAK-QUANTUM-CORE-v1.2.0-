
package com.idraak.quantum

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * IDRAAK TTS Language Manager
 *
 * जिम्मेदारी:
 * - TTS भाषा चुनना
 * - Locale mapping करना
 * - भाषा की उपलब्धता जाँचना
 * - Male/Female voice चुनना
 */
class TtsLanguageManager(context: Context) {

    companion object {
        private const val TAG = "IDRAAK_TTS_Language"
        private const val DEFAULT_LANGUAGE = "hi"
        private const val DEFAULT_GENDER = "Male"
    }

    private val appContext = context.applicationContext

    private var selectedLanguage = DEFAULT_LANGUAGE
    private var selectedGender = DEFAULT_GENDER
    private var configuredEngine: TextToSpeech? = null

    init {
        Log.d(TAG, "TtsLanguageManager initialized")
    }

    private fun normalizeLanguage(language: String): String {
        return when (language.trim().lowercase(Locale.ROOT)) {
            "hi", "hindi", "hi-in" -> "hi"
            "en", "english", "en-in", "en-us" -> "en"
            "ur", "urdu", "ur-pk" -> "ur"
            "ar", "arabic", "ar-sa" -> "ar"
            else -> ""
        }
    }

    private fun getLocale(language: String): Locale? {
        return when (normalizeLanguage(language)) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "en" -> Locale.forLanguageTag("en-IN")
            "ur" -> Locale.forLanguageTag("ur-PK")
            "ar" -> Locale.forLanguageTag("ar-SA")
            else -> null
        }
    }

    fun getSelectedLanguage(): String = selectedLanguage

    fun getSelectedGender(): String = selectedGender

    /**
     * TTS initialize होने के बाद configuration लागू करता है।
     */
    fun applyTo(engine: TextToSpeech): Boolean {
        configuredEngine = engine

        if (!applyLanguage(engine)) {
            Log.e(TAG, "Selected language unavailable: $selectedLanguage")
            return false
        }

        // Gender voice उपलब्ध न हो तो system की default voice रहने दें।
        applyGender(engine)

        Log.d(
            TAG,
            "TTS configuration applied: language=$selectedLanguage, gender=$selectedGender"
        )

        return true
    }

    fun setLanguage(
        language: String,
        engine: TextToSpeech?
    ): Boolean {
        val normalized = normalizeLanguage(language)

        if (normalized.isEmpty()) {
            Log.e(TAG, "Unsupported language: $language")
            return false
        }

        selectedLanguage = normalized

        val activeEngine = engine ?: configuredEngine

        if (activeEngine == null) {
            Log.d(TAG, "Language saved; TTS engine not ready")
            return true
        }

        configuredEngine = activeEngine
        return applyLanguage(activeEngine)
    }

    private fun applyLanguage(engine: TextToSpeech): Boolean {
        val locale = getLocale(selectedLanguage) ?: return false

        return try {
            val availability = engine.isLanguageAvailable(locale)

            if (availability < TextToSpeech.LANG_AVAILABLE) {
                Log.e(
                    TAG,
                    "Language unavailable: ${locale.toLanguageTag()}, status=$availability"
                )
                return false
            }

            val result = engine.setLanguage(locale)

            if (result < TextToSpeech.LANG_AVAILABLE) {
                Log.e(
                    TAG,
                    "setLanguage failed: ${locale.toLanguageTag()}, status=$result"
                )
                return false
            }

            Log.d(TAG, "Language applied: ${locale.toLanguageTag()}")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Language configuration error", exception)
            false
        }
    }

    fun setVoiceGender(
        gender: String,
        engine: TextToSpeech?
    ): Boolean {
        selectedGender = when (gender.trim().lowercase(Locale.ROOT)) {
            "female", "फीमेल", "महिला" -> "Female"
            "male", "मेल", "पुरुष" -> "Male"
            else -> {
                Log.e(TAG, "Unsupported voice gender: $gender")
                return false
            }
        }

        val activeEngine = engine ?: configuredEngine

        if (activeEngine == null) {
            Log.d(TAG, "Voice gender saved; TTS engine not ready")
            return true
        }

        configuredEngine = activeEngine
        return applyGender(activeEngine)
    }

    private fun applyGender(engine: TextToSpeech): Boolean {
        return try {
            val locale = getLocale(selectedLanguage) ?: return false

            val availableVoices = engine.voices
                ?.filter { voice ->
                    voice.locale.language.equals(
                        locale.language,
                        ignoreCase = true
                    ) && !voice.isNetworkConnectionRequired
                }
                ?: emptyList()

            if (availableVoices.isEmpty()) {
                Log.w(TAG, "No matching offline voices for $selectedLanguage")
                return false
            }

            val genderKeyword =
                if (selectedGender == "Female") "female" else "male"

            val selectedVoice = availableVoices.firstOrNull {
                it.name.contains(genderKeyword, ignoreCase = true)
            }

            if (selectedVoice == null) {
                Log.w(TAG, "Requested gender voice not found: $selectedGender")
                return false
            }

            engine.voice = selectedVoice

            Log.d(TAG, "Voice selected: ${selectedVoice.name}")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Voice selection failed", exception)
            false
        }
    }

    fun isLanguageAvailable(language: String): Boolean {
        val locale = getLocale(language) ?: return false
        val engine = configuredEngine ?: return false

        return try {
            engine.isLanguageAvailable(locale) >=
                TextToSpeech.LANG_AVAILABLE
        } catch (exception: Exception) {
            Log.e(TAG, "Language availability check failed", exception)
            false
        }
    }

    fun getAvailableVoices(): List<Voice> {
        val engine = configuredEngine ?: return emptyList()
        val locale = getLocale(selectedLanguage) ?: return emptyList()

        return try {
            engine.voices
                ?.filter {
                    it.locale.language.equals(
                        locale.language,
                        ignoreCase = true
                    )
                }
                ?: emptyList()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not retrieve voices", exception)
            emptyList()
        }
    }

    fun release() {
        configuredEngine = null
        Log.d(TAG, "TtsLanguageManager released")
    }
}
