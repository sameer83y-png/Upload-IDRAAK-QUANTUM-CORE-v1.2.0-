package com.idraak.quantum

data class CommandExecutionResult(
    val success: Boolean,
    val message: String
)

/**
 * Parsed phone commands को संबंधित controller तक पहुँचाता है।
 * Parsing और voice recognition इस class की जिम्मेदारी नहीं हैं।
 */
internal class CommandExecutionManager(
    private val softwareController: SoftwareController,
    private val systemController: SystemController,
    private val screenController: ScreenController,
    private val hardwareController: HardwareController
) {

    fun execute(command: PhoneCommand): CommandExecutionResult {
        return try {
            when (command.type) {
                PhoneCommandType.OPEN_APP -> {
                    val opened = softwareController.openAppByName(
                        command.target
                    )
                    result(
                        opened,
                        if (opened) {
                            "${command.target} खोल दिया गया।"
                        } else {
                            "${command.target} ऐप नहीं मिला या उसे खोला नहीं जा सका।"
                        }
                    )
                }

                PhoneCommandType.OPEN_PHONE_SETTINGS -> {
                    val opened = systemController.openSettingsHome()
                    result(opened, "फोन की सेटिंग खोलने में समस्या हुई।", "फोन की सेटिंग खोल रहा हूँ।")
                }

                PhoneCommandType.OPEN_IDRAAK_SETTINGS -> {
                    result(
                        false,
                        "IDRAAK Settings स्क्रीन अभी UI integration में जोड़ी जानी है।"
                    )
                }

                PhoneCommandType.OPEN_APP_SETTINGS -> {
                    val app = softwareController.searchApps(command.target)
                        .firstOrNull()

                    val opened = app?.let {
                        softwareController.openAppSettings(it.packageName)
                    } ?: false

                    result(
                        opened,
                        "ऐप की सेटिंग नहीं खुल सकी।",
                        "ऐप की सेटिंग खोल रहा हूँ।"
                    )
                }

                PhoneCommandType.UNINSTALL_APP -> {
                    val app = softwareController.searchApps(command.target)
                        .firstOrNull()

                    val opened = app?.let {
                        softwareController.requestUninstall(it.packageName)
                    } ?: false

                    result(
                        opened,
                        "ऐप हटाने का confirmation पेज नहीं खुल सका।",
                        "Android का uninstall confirmation पेज खोल रहा हूँ।"
                    )
                }

                PhoneCommandType.VOLUME_UP ->
                    result(
                        systemController.increaseMediaVolume(),
                        "मीडिया वॉल्यूम और नहीं बढ़ सका।",
                        "मीडिया वॉल्यूम बढ़ा दिया।"
                    )

                PhoneCommandType.VOLUME_DOWN ->
                    result(
                        systemController.decreaseMediaVolume(),
                        "मीडिया वॉल्यूम और कम नहीं हो सका।",
                        "मीडिया वॉल्यूम कम कर दिया।"
                    )

                PhoneCommandType.VOLUME_MUTE ->
                    result(
                        systemController.muteMedia(),
                        "मीडिया वॉल्यूम mute नहीं हो सका।",
                        "मीडिया वॉल्यूम mute कर दिया।"
                    )

                PhoneCommandType.VOLUME_SET -> {
                    val value = command.value ?: return failure("वॉल्यूम स्तर नहीं मिला।")
                    result(
                        systemController.setMediaVolume(value),
                        "मीडिया वॉल्यूम नहीं बदला जा सका।",
                        "मीडिया वॉल्यूम $value प्रतिशत कर दिया।"
                    )
                }

                PhoneCommandType.RINGER_SILENT ->
                    result(
                        systemController.setRingerMode("silent"),
                        "फोन को silent नहीं किया जा सका। DND access जाँचें।",
                        "फोन silent mode में कर दिया।"
                    )

                PhoneCommandType.RINGER_VIBRATE ->
                    result(
                        systemController.setRingerMode("vibrate"),
                        "फोन vibration mode में नहीं जा सका।",
                        "फोन vibration mode में कर दिया।"
                    )

                PhoneCommandType.RINGER_NORMAL ->
                    result(
                        systemController.setRingerMode("normal"),
                        "फोन का ring mode नहीं बदला जा सका।",
                        "फोन normal ring mode में कर दिया।"
                    )

                PhoneCommandType.BRIGHTNESS_SET -> {
                    val value = command.value ?: return failure("Brightness स्तर नहीं मिला।")
                    result(
                        systemController.setBrightness(value),
                        "Brightness नहीं बदली। Write System Settings permission जाँचें।",
                        "Brightness $value प्रतिशत कर दी।"
                    )
                }

                PhoneCommandType.FLASHLIGHT_ON ->
                    result(
                        hardwareController.flashlightOn(),
                        "टॉर्च चालू नहीं हो सकी। Camera permission जाँचें।",
                        "टॉर्च चालू कर दी।"
                    )

                PhoneCommandType.FLASHLIGHT_OFF ->
                    result(
                        hardwareController.flashlightOff(),
                        "टॉर्च बंद नहीं हो सकी।",
                        "टॉर्च बंद कर दी।"
                    )

                PhoneCommandType.CAMERA_OPEN ->
                    result(
                        hardwareController.openCamera(),
                        "कैमरा नहीं खुल सका।",
                        "कैमरा खोल रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_READ -> {
                    if (!screenController.isAccessibilityEnabled()) {
                        failure("पहले Android Settings में IDRAAK Accessibility Service चालू करें।")
                    } else {
                        val text = screenController.readScreen()
                        if (text.isBlank()) {
                            failure("स्क्रीन से पढ़ने योग्य text नहीं मिला।")
                        } else {
                            CommandExecutionResult(true, text)
                        }
                    }
                }

                PhoneCommandType.SCREEN_CLICK_TEXT ->
                    result(
                        screenController.clickText(command.target),
                        "'${command.target}' स्क्रीन पर नहीं मिला या click नहीं हुआ।",
                        "'${command.target}' पर click कर दिया।"
                    )

                PhoneCommandType.SCREEN_BACK ->
                    result(
                        screenController.goBack(),
                        "Back action नहीं हो सका। Accessibility Service जाँचें।",
                        "वापस जा रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_HOME ->
                    result(
                        screenController.goHome(),
                        "Home action नहीं हो सका। Accessibility Service जाँचें।",
                        "Home screen खोल रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_RECENTS ->
                    result(
                        screenController.openRecentApps(),
                        "Recent Apps नहीं खुल सके।",
                        "Recent Apps खोल रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_NOTIFICATIONS ->
                    result(
                        screenController.openNotifications(),
                        "Notification panel नहीं खुल सका।",
                        "Notifications खोल रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_QUICK_SETTINGS ->
                    result(
                        screenController.openQuickSettings(),
                        "Quick Settings नहीं खुल सकीं।",
                        "Quick Settings खोल रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_SCROLL_UP ->
                    result(
                        screenController.scrollUp(),
                        "स्क्रीन ऊपर scroll नहीं हुई।",
                        "ऊपर scroll कर रहा हूँ।"
                    )

                PhoneCommandType.SCREEN_SCROLL_DOWN ->
                    result(
                        screenController.scrollDown(),
                        "स्क्रीन नीचे scroll नहीं हुई।",
                        "नीचे scroll कर रहा हूँ।"
                    )
            }
        } catch (error: Exception) {
            failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Command execute नहीं हो सकी।"
            )
        }
    }

    private fun result(
        success: Boolean,
        failureMessage: String,
        successMessage: String = "काम पूरा हो गया।"
    ): CommandExecutionResult {
        return if (success) {
            CommandExecutionResult(true, successMessage)
        } else {
            CommandExecutionResult(false, failureMessage)
        }
    }

    private fun failure(message: String) =
        CommandExecutionResult(false, message)
}
