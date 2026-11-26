package com.idraak.quantum.core

data class CommandResult(val message: String, val action: CommandAction = CommandAction.None)

sealed interface CommandAction {
    data object None : CommandAction
    data class OpenUrl(val url: String) : CommandAction
    data class OpenApp(val appName: String) : CommandAction
    data class DialNumber(val number: String) : CommandAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Idraak alarm") : CommandAction
    data object OpenAlarm : CommandAction
    data object OpenSettings : CommandAction
    data object OpenCamera : CommandAction
}
