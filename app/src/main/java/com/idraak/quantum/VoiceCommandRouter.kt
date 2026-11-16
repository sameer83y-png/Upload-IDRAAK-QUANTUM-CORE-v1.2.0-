
package com.idraak.quantum

/**
 * IDRAAK - Voice Command Router
 *
 * Responsibility:
 * Selects the processing path for recognized commands.
 *
 * Phone command parsing belongs to PhoneCommandParser.
 * Command execution belongs to CommandExecutionManager.
 * Offline AI processing belongs to OfflineCommandEngine.
 * Online/offline AI coordination belongs to AIEngine.
 *
 * This class does not recognize speech, parse commands,
 * communicate with Gemini, or execute actions.
 */
internal class VoiceCommandRouter {

    enum class Route {
        PHONE_COMMAND,
        OFFLINE_AI,
        ONLINE_AI
    }

    /**
     * Selects the next processing path.
     *
     * isPhoneCommand must be supplied by PhoneCommandParser.
     * isOfflineMode must be supplied by the application settings.
     */
    fun selectRoute(
        isPhoneCommand: Boolean,
        isOfflineMode: Boolean
    ): Route {

        return when {
            isPhoneCommand -> Route.PHONE_COMMAND
            isOfflineMode -> Route.OFFLINE_AI
            else -> Route.ONLINE_AI
        }
    }
}
