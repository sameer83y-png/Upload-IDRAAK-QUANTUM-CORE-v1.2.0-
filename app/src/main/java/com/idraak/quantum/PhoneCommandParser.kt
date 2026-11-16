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

/**
 * Spoken text को structured phone command में बदलता है।
 * यह class कोई Android action execute नहीं करती।
 */
class PhoneCommandParser(
    private val normalizer: SpeechTextNormalizer =
        SpeechTextNormalizer()
) {

    fun parse(spokenText: String): PhoneCommand? {
        val text = normalizer.normalize(spokenText)
        if (text.isBlank()) return null

        if (containsAny(text, "idraak settings", "आईड्राक सेटिंग", "अपनी सेटिंग")) {
            return PhoneCommand(PhoneCommandType.OPEN_IDRAAK_SETTINGS)
        }

        if (containsAny(text, "फोन की सेटिंग", "मोबाइल सेटिंग", "phone settings", "system settings")) {
            return PhoneCommand(PhoneCommandType.OPEN_PHONE_SETTINGS)
        }

        if (containsAny(text, "वॉल्यूम बढ़ाओ", "आवाज बढ़ाओ", "volume up", "increase volume", "آواز بڑھاؤ")) {
            return PhoneCommand(PhoneCommandType.VOLUME_UP)
        }

        if (containsAny(text, "वॉल्यूम कम करो", "आवाज कम करो", "volume down", "decrease volume", "آواز کم کرو")) {
            return PhoneCommand(PhoneCommandType.VOLUME_DOWN)
        }

        if (containsAny(text, "आवाज बंद करो", "म्यूट करो", "mute volume", "mute media", "آواز بند کرو")) {
            return PhoneCommand(PhoneCommandType.VOLUME_MUTE)
        }

        parseVolumeSet(text)?.let { return it }

        if (containsAny(text, "silent mode", "फोन साइलेंट", "साइलेंट कर दो")) {
            return PhoneCommand(PhoneCommandType.RINGER_SILENT)
        }

        if (containsAny(text, "vibrate mode", "वाइब्रेशन मोड", "वाइब्रेट कर दो")) {
            return PhoneCommand(PhoneCommandType.RINGER_VIBRATE)
        }

        if (containsAny(text, "normal mode", "रिंग मोड", "सामान्य मोड")) {
            return PhoneCommand(PhoneCommandType.RINGER_NORMAL)
        }

        parseBrightness(text)?.let { return it }

        if (containsAny(text, "टॉर्च चालू", "फ्लैशलाइट चालू", "flashlight on", "torch on")) {
            return PhoneCommand(PhoneCommandType.FLASHLIGHT_ON)
        }

        if (containsAny(text, "टॉर्च बंद", "फ्लैशलाइट बंद", "flashlight off", "torch off")) {
            return PhoneCommand(PhoneCommandType.FLASHLIGHT_OFF)
        }

        if (containsAny(text, "कैमरा खोलो", "ओपन कैमरा", "open camera", "start camera")) {
            return PhoneCommand(PhoneCommandType.CAMERA_OPEN)
        }

        if (containsAny(text, "स्क्रीन पढ़ो", "स्क्रीन पर क्या है", "read screen", "what is on screen")) {
            return PhoneCommand(PhoneCommandType.SCREEN_READ)
        }

        if (containsAny(text, "वापस जाओ", "गो बैक", "go back", "back")) {
            return PhoneCommand(PhoneCommandType.SCREEN_BACK)
        }

        if (containsAny(text, "होम स्क्रीन", "होम पर जाओ", "go home", "home screen")) {
            return PhoneCommand(PhoneCommandType.SCREEN_HOME)
        }

        if (containsAny(text, "recent apps", "हाल के ऐप", "ओपन रिसेंट")) {
            return PhoneCommand(PhoneCommandType.SCREEN_RECENTS)
        }

        if (containsAny(text, "notification panel", "नोटिफिकेशन खोलो", "open notifications")) {
            return PhoneCommand(PhoneCommandType.SCREEN_NOTIFICATIONS)
        }

        if (containsAny(text, "quick settings", "क्विक सेटिंग खोलो")) {
            return PhoneCommand(PhoneCommandType.SCREEN_QUICK_SETTINGS)
        }

        if (containsAny(text, "नीचे स्क्रॉल", "scroll down", "स्क्रॉल डाउन")) {
            return PhoneCommand(PhoneCommandType.SCREEN_SCROLL_DOWN)
        }

        if (containsAny(text, "ऊपर स्क्रॉल", "scroll up", "स्क्रॉल अप")) {
            return PhoneCommand(PhoneCommandType.SCREEN_SCROLL_UP)
        }

        parseClickText(text)?.let { return it }

        parseAppSettings(text)?.let { return it }
        parseUninstall(text)?.let { return it }
        parseOpenApp(text)?.let { return it }

        return null
    }

    private fun parseVolumeSet(text: String): PhoneCommand? {
        val match = Regex(
            "(?:volume|वॉल्यूम|आवाज)\\s*(?:set|सेट|करो|कर दो)?\\s*(\\d{1,3})"
        ).find(text) ?: return null

        val value = match.groupValues[1].toIntOrNull() ?: return null
        return PhoneCommand(
            PhoneCommandType.VOLUME_SET,
            value = value.coerceIn(0, 100)
        )
    }

    private fun parseBrightness(text: String): PhoneCommand? {
        val match = Regex(
            "(?:brightness|ब्राइटनेस|रोशनी)\\s*(?:set|सेट|करो|कर दो)?\\s*(\\d{1,3})"
        ).find(text) ?: return null

        val value = match.groupValues[1].toIntOrNull() ?: return null
        return PhoneCommand(
            PhoneCommandType.BRIGHTNESS_SET,
            value = value.coerceIn(0, 100)
        )
    }

    private fun parseClickText(text: String): PhoneCommand? {
        val patterns = listOf(
            "क्लिक करो ",
            "दबाओ ",
            "click text ",
            "click "
        )

        val prefix = patterns.firstOrNull { text.startsWith(it) }
            ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            type = PhoneCommandType.SCREEN_CLICK_TEXT,
            target = target
        )
    }

    private fun parseAppSettings(text: String): PhoneCommand? {
        val prefixes = listOf(
            "open app settings ",
            "ऐप सेटिंग खोलो ",
            "ऐप की सेटिंग खोलो "
        )

        val prefix = prefixes.firstOrNull { text.startsWith(it) }
            ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            PhoneCommandType.OPEN_APP_SETTINGS,
            target = target
        )
    }

    private fun parseUninstall(text: String): PhoneCommand? {
        val prefixes = listOf(
            "uninstall ",
            "remove app ",
            "ऐप अनइंस्टॉल करो ",
            "ऐप हटाओ "
        )

        val prefix = prefixes.firstOrNull { text.startsWith(it) }
            ?: return null

        val target = text.removePrefix(prefix).trim()
        if (target.isBlank()) return null

        return PhoneCommand(
            PhoneCommandType.UNINSTALL_APP,
            target = target
        )
    }

    private fun parseOpenApp(text: String): PhoneCommand? {
        val prefixes = listOf(
            "open ",
            "launch ",
            "start ",
            "खोलो ",
            "खोल दो ",
            "ओपन ",
            "चालू करो "
        )

        val prefix = prefixes.firstOrNull { text.startsWith(it) }
            ?: return null

        var target = text.removePrefix(prefix).trim()

        target = target
            .removePrefix("app ")
            .removePrefix("ऐप ")
            .trim()

        if (target.isBlank()) return null

        if (
            target in listOf(
                "settings",
                "सेटिंग",
                "सेटिंग्स",
                "फोन सेटिंग"
            )
        ) {
            return PhoneCommand(PhoneCommandType.OPEN_PHONE_SETTINGS)
        }

        return PhoneCommand(
            PhoneCommandType.OPEN_APP,
            target = target
        )
    }

    private fun containsAny(
        text: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { phrase ->
            text.contains(phrase.lowercase(Locale.ROOT))
        }
    }
}
