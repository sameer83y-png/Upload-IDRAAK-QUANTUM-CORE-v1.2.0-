package com.idraak.quantum.core

data class CommandResult(val message: String, val action: CommandAction = CommandAction.None)

enum class VolumeDirection { UP, DOWN, MUTE }

sealed interface CommandAction {
    data object None : CommandAction
    data class OpenUrl(val url: String) : CommandAction
    data class OpenApp(val appName: String) : CommandAction
    data class ShareText(val text: String) : CommandAction
    data class ShareTextToApp(val text: String, val appName: String) : CommandAction
    data class DialNumber(val number: String) : CommandAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Idraak alarm") : CommandAction
    data class AdjustVolume(val direction: VolumeDirection) : CommandAction
    data class ScheduleReminder(val triggerAtMillis: Long, val message: String) : CommandAction
    data object OpenAlarm : CommandAction
    data object OpenSettings : CommandAction
    data object OpenWifiSettings : CommandAction
    data object OpenBluetoothSettings : CommandAction
    data object OpenDisplaySettings : CommandAction
    data object OpenSoundSettings : CommandAction
    data object OpenBatterySettings : CommandAction
    data object OpenCamera : CommandAction
}
