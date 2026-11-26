package com.idraak.quantum.core

data class CommandResult(val message: String, val action: CommandAction = CommandAction.None)

sealed interface CommandAction {
    data object None : CommandAction
    data class OpenUrl(val url: String) : CommandAction
    data object OpenSettings : CommandAction
    data object OpenCamera : CommandAction
}
