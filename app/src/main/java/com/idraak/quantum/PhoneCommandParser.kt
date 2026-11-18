package com.idraak.quantum

import java.util.Locale

enum class PhoneCommandType {
    OPEN_APP,
    OPEN_PHONE_SETTINGS,
    OPEN_IDRAAK_SETTINGS,
    OPEN_APP_SETTINGS,
    UNINSTALL_APP,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    VOLUME_SET,
    RINGER_SILENT,
    RINGER_VIBRATE,
    RINGER_NORMAL,
    BRIGHTNESS_SET,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    CAMERA_OPEN,
    SCREEN_READ,
    SCREEN_CLICK_TEXT,
    SCREEN_BACK,
    SCREEN_HOME,
    SCREEN_RECENTS,
    SCREEN_NOTIFICATIONS,
    SCREEN_QUICK_SETTINGS,
    SCREEN_SCROLL_UP,
    SCREEN_SCROLL_DOWN
}

data class PhoneCommand(
    val type: PhoneCommandType,
    val target: String = "",
    val value: Int? = null
)

class PhoneCommandParser(
    private val normalizer: SpeechTextNormalizer =
        SpeechTextNormalizer()
) {

    fun parse(spokenText: String): PhoneCommand? {
        var text = normalizer.normalize(spokenText)
        if (text.isBlank()) return null

        text = removePolitePrefix(text)

        if (matchesCommand(text,
                "idraak settings", "आईड्राक सेटिंग",
                "इद्राक सेटिंग", "अपनी सेटिंग")) {
            return PhoneCommand(PhoneCommandType.OPEN_IDRAAK_SETTINGS)
        }

        if (matchesCommand(text,
                "फोन की सेटिंग", "मोबाइल सेटिंग",
                "phone settings", "system settings",
                "open settings", "सेटिंग खोलो")) {
            return PhoneCommand(PhoneCommandType.OPEN_PHONE_SETTINGS)
        }

        if (matchesCommand(text,
                "वॉल्यूम बढ़ाओ", "आवाज बढ़ाओ", "आवाज़ बढ़ाओ",
                "volume up", "increase volume",
                "turn up volume", "raise volume",
                "آواز بڑھاؤ")) {
            return PhoneCommand(PhoneCommandType.VOLUME_UP)
        }

        if (matchesCommand(text,
                "वॉल्यूम कम करो", "आवाज कम करो", "आवाज़ कम करो",
                "volume down", "decrease volume",
                "turn down volume", "lower volume",
                "آواز کم کرو")) {
            return PhoneCommand(PhoneCommandType.VOLUME_DOWN)
        }

        if (matchesCommand(text,
                "आवाज बंद करो", "आवाज़ बंद करो",
                "म्यूट करो", "वॉल्यूम म्यूट",
                "mute volume", "mute media",
                "mute the volume", "آواز بند کرو")) {
            return PhoneCommand(PhoneCommandType.VOLUME_MUTE)
        }

        parseVolumeSet(text)?.let { return it }

        if (matchesCommand(text,
                "silent mode", "फोन साइलेंट",
                "फोन को साइलेंट करो", "साइलेंट कर दो",
                "make phone silent", "set silent mode")) {
            return PhoneCommand(PhoneCommandType.RINGER_SILENT)
        }

        if (matchesCommand(text,
                "vibrate mode", "वाइब्रेशन मोड",
                "वाइब्रेट कर दो", "फोन वाइब्रेट करो",
                "set vibrate mode")) {
            return PhoneCommand(PhoneCommandType.RINGER_VIBRATE)
        }

        if (matchesCommand(text,
                "normal mode", "रिंग मोड",
                "सामान्य मोड", "नॉर्मल मोड",
                "ring mode", "turn on ringtone")) {
            return PhoneCommand(PhoneCommandType.RINGER_NORMAL)
        }

        parseBrightness(text)?.let { return it }

        if (matchesCommand(text,
                "टॉर्च चालू", "टॉर्च ऑन",
                "फ्लैशलाइट चालू", "flashlight on",
                "torch on", "turn on flashlight",
                "turn on torch")) {
            return PhoneCommand(PhoneCommandType.FLASHLIGHT_ON)
        }

        if (matchesCommand(text,
                "टॉर्च बंद", "टॉर्च ऑफ",
                "फ्लैशलाइट बंद", "flashlight off",
                "torch off", "turn off flashlight",
                "turn off torch")) {
            return PhoneCommand(PhoneCommandType.FLASHLIGHT_OFF)
        }

        if (matchesCommand(text,
                "कैमरा खोलो", "ओपन कैमरा",
                "कैमरा चालू करो", "open camera",
                "start camera", "launch camera")) {
            return PhoneCommand(PhoneCommandType.CAMERA_OPEN)
        }

        if (matchesCommand(text,
                "स्क्रीन पढ़ो", "स्क्रीन पर क्या है",
                "स्क्रीन पढ़कर बताओ", "read screen",
                "what is on screen", "read the screen")) {
            return PhoneCommand(PhoneCommandType.SCREEN_READ)
        }

        // Back केवल स्पष्ट Back Command पर चलेगा।
        // सामान्य बातचीत में "back" शब्द मिलने से Action नहीं चलेगा।
        if (matchesCommand(text,
                "वापस जाओ", "पीछे जाओ", "गो बैक",
                "go back", "back", "navigate back")) {
            return PhoneCommand(PhoneCommandType.SCREEN_BACK)
        }

        if (matchesCommand(text,
                "होम स्क्रीन", "होम पर जाओ",
                "होम खोलो", "go home",
                "home screen", "open home screen")) {
            return PhoneCommand(PhoneCommandType.SCREEN_HOME)
        }

        if (matchesCommand(text,
                "recent apps", "हाल के ऐप",
                "ओपन रिसेंट", "रिसेंट ऐप खोलो",
                "show recent apps")) {
            return PhoneCommand(PhoneCommandType.SCREEN_RECENTS)
        }

        if (matchesCommand(text,
                "notification panel", "नोटिफिकेशन खोलो",
                "नोटिफिकेशन दिखाओ", "open notifications",
                "show notifications")) {
            return PhoneCommand(PhoneCommandType.SCREEN_NOTIFICATIONS)
        }

        if (matchesCommand(text,
                "quick settings", "क्विक सेटिंग खोलो",
                "क्विक सेटिंग्स खोलो",
                "open quick settings")) {
            return PhoneCommand(PhoneCommandType.SCREEN_QUICK_SETTINGS)
        }

        if (matchesCommand(text,
                "नीचे स्क्रॉल", "नीचे स्क्रोल",
                "scroll down", "स्क्रॉल डाउन")) {
            return PhoneCommand(PhoneCommandType.SCREEN_SCROLL_DOWN)
        }

        if (matchesCommand(text,
                "ऊपर स्क्रॉल", "ऊपर स्क्रोल",
                "scroll up", "स्क्रॉल अप")) {
            return PhoneCommand(PhoneCommandType.SCREEN_SCROLL_UP)
        }

        parseClickText(text)?.let { return it }
        parseAppSettings(text)?.let { return it }
        parseUninstall(text)?.let { return it }
        parseOpenApp(text)?.let { return it }

        return null
    }

    private fun parseVolumeSet(text: String): PhoneCommand? {
        val regex = Regex(
            "^(?:set )?(?:the )?(?:media )?" +
                "(?:volume|वॉल्यूम|आवाज|आवाज़)" +
                "(?: (?:set|सेट))?" +
                "(?: (?:to|at|पर|को))? " +
                "(\\d{1,3})" +
                "(?: ?(?:percent|प्रतिशत|परसेंट))?$"
        )

        val value = regex.find(text)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull() ?: return null

        return PhoneCommand(
            PhoneCommandType.VOLUME_SET,
            value = value.coerceIn(0, 100)
        )
    }

    private fun parseBrightness(text: String): PhoneCommand? {
        val regex = Regex(
            "^(?:set )?(?:the )?" +
                "(?:brightness|ब्राइटनेस|रोशनी)" +
                "(?: (?:set|सेट))?" +
                "(?: (?:to|at|पर|को))? " +
                "(\\d{1,3})" +
                "(?: ?(?:percent|प्रतिशत|परसेंट))?$"
        )

        val value = regex.find(text)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull() ?: return null

        return PhoneCommand(
            PhoneCommandType.BRIGHTNESS_SET,
            value = value.coerceIn(0, 100)
        )
    }

    private fun parseClickText(text: String): PhoneCommand? {
        val prefixes = listOf(
            "क्लिक करो ",
            "दबाओ ",
            "click text ",
            "click on ",
            "click "
        )

        val prefix = prefixes.firstOrNull {
            text.startsWith(it)
        } ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            PhoneCommandType.SCREEN_CLICK_TEXT,
            target = target
        )
    }

    private fun parseAppSettings(text: String): PhoneCommand? {
        val prefixes = listOf(
            "open app settings ",
            "open settings for ",
            "ऐप सेटिंग खोलो ",
            "ऐप की सेटिंग खोलो ",
            "ऐप सेटिंग्स खोलो "
        )

        val prefix = prefixes.firstOrNull {
            text.startsWith(it)
        } ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            PhoneCommandType.OPEN_APP_SETTINGS,
            target = target
        )
    }

    private fun parseUninstall(text: String): PhoneCommand? {
        val prefixes = listOf(
            "uninstall app ",
            "uninstall ",
            "remove app ",
            "ऐप अनइंस्टॉल करो ",
            "ऐप को अनइंस्टॉल करो ",
            "ऐप हटाओ "
        )

        val prefix = prefixes.firstOrNull {
            text.startsWith(it)
        } ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            PhoneCommandType.UNINSTALL_APP,
            target = target
        )
    }

    private fun parseOpenApp(text: String): PhoneCommand? {
        val prefixes = listOf(
            "open app ",
            "open ",
            "launch ",
            "start ",
            "खोलो ",
            "खोल दो ",
            "ओपन ",
            "चालू करो "
        )

        val prefix = prefixes.firstOrNull {
            text.startsWith(it)
        } ?: return null

        var target = text.removePrefix(prefix).trim()

        target = target
            .removePrefix("app ")
            .removePrefix("ऐप ")
            .trim()

        if (target.isBlank()) return null

        if (target in listOf(
                "settings",
                "सेटिंग",
                "सेटिंग्स",
                "फोन सेटिंग"
            )) {
            return PhoneCommand(PhoneCommandType.OPEN_PHONE_SETTINGS)
        }

        return PhoneCommand(
            PhoneCommandType.OPEN_APP,
            target = target
        )
    }

    private fun matchesCommand(
        text: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { phrase ->
            text == phrase ||
                text.startsWith("$phrase ")
        }
    }

    private fun removePolitePrefix(text: String): String {
        val prefixes = listOf(
            "please ",
            "kindly ",
            "कृपया ",
            "जरा ",
            "ज़रा ",
            "प्लीज "
        )

        return prefixes.firstOrNull {
            text.startsWith(it)
        }?.let {
            text.removePrefix(it).trim()
        } ?: text
    }
}
