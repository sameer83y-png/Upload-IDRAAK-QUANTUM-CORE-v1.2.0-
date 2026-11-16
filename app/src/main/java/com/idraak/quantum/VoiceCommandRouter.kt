package com.idraak.quantum

import android.content.Context

/**
 * Recognized speech को सही subsystem तक route करता है।
 *
 * प्राथमिकता:
 * 1. Local phone command
 * 2. Offline conversational response
 * 3. Online Gemini AI
 *
 * यह class SpeechRecognizer या Accessibility Service को सीधे नियंत्रित नहीं करती।
 */
internal class VoiceCommandRouter(context: Context) {

    private val appContext = context.applicationContext

    private val settingsManager = SettingsManager(appContext)
    private val normalizer = SpeechTextNormalizer()
    private val phoneCommandParser = PhoneCommandParser(normalizer)
    private val offlineCommandEngine = OfflineCommandEngine(normalizer)
    private val aiEngine = AIEngine(appContext)

    private val commandExecutionManager = CommandExecutionManager(
        softwareController = SoftwareController(appContext),
        systemController = SystemController(appContext),
        screenController = ScreenController(),
        hardwareController = HardwareController(appContext)
    )

    @Volatile
    private var routingInProgress = false

    suspend fun routeRecognizedText(recognizedText: String): String {
        val text = normalizer.normalize(recognizedText)

        if (text.isBlank()) {
            return "मैं आपकी बात समझ नहीं पाया। कृपया दोबारा बोलें।"
        }

        synchronized(this) {
            if (routingInProgress) {
                return "एक command पहले से process हो रही है।"
            }
            routingInProgress = true
        }

        return try {
            val command = phoneCommandParser.parse(text)

            if (command != null) {
                val result = commandExecutionManager.execute(command)
                result.message
            } else {
                val offlineResponse = offlineCommandEngine.respond(text)

                if (settingsManager.offlineMode) {
                    offlineResponse
                        ?: "यह काम offline mode में उपलब्ध नहीं है।"
                } else {
                    aiEngine.generateResponse(recognizedText)
                }
            }
        } catch (error: Exception) {
            error.message
                ?.takeIf { it.isNotBlank() }
                ?.let { "Command process नहीं हो सकी: ${it.take(250)}" }
                ?: "Command process नहीं हो सकी। कृपया दोबारा कोशिश करें।"
        } finally {
            synchronized(this) {
                routingInProgress = false
            }
        }
    }

    fun clearConversation() {
        aiEngine.clearConversation()
    }

    fun isProcessing(): Boolean =
        routingInProgress
}
