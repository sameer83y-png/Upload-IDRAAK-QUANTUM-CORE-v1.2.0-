# Idraak Quantum Core

An offline-first Android assistant foundation with Urdu-first command handling, local memory, Android actions, reminders, and a customizable dashboard.

## App capabilities
- Futuristic Idraak dashboard with three saved color themes and context-aware quick suggestions.
- Opens common installed apps: YouTube, Chrome, ChatGPT, Gemini, WhatsApp, Instagram, Facebook, Telegram, Maps, and Gmail.
- Opens phone, Wi-Fi, Bluetooth, display, sound, and battery settings.
- Searches YouTube and Google from Urdu or English commands.
- Opens the dialer for a supplied number; the user reviews and places the call.
- Sends text to Android's generic share chooser so the user can choose a supported messaging app and review the message before sending.
- Sets clock alarms by sending the time to the installed Clock app.
- Schedules local reminder notifications. Supported reminders are persisted and restored after device reboot.
- Raises, lowers, or mutes media volume through Android audio controls.
- Local SQLite memory supports explicit save, list, search, exact deletion, and correction history.
- Urdu text-to-speech is available when a compatible Urdu voice is installed.

## Example commands
- `یوٹیوب کھولو`
- `یوٹیوب پر آج کی ٹیک خبریں تلاش کرو`
- `وائی فائی سیٹنگز کھولو`
- `آواز بڑھاؤ`, `آواز کم کرو`, `آواز خاموش کرو`
- `الارم 19:30 لگاؤ`
- `مجھے 10 منٹ بعد پانی پینا یاد دلانا`
- `remind me to drink water in 10 minutes`
- `یہ پیغام بھیجو میں راستے میں ہوں`
- `call +911234567890` — opens the dialer; it does not place a call automatically.

## Local memory commands
- `remember that <fact>` — save a fact on this device.
- `what do you remember` — list saved memories.
- `search memory for <words>` — find matching entries.
- `forget memory <exact text>` — remove an exact entry.
- `correct memory: <old text> -> <corrected text>` — update an exact entry and record the correction.
- `show correction history` — review saved corrections.

Memory is stored locally and only changes through explicit commands. Do not save passwords, authentication codes, or other credentials.

## Voice input
The current strict Android speech-recognition path requires Android 12+ and a compatible offline Urdu speech pack. It does not silently switch to online recognition. On Android 9 devices, that built-in path is unavailable; a dedicated downloadable offline Urdu model is being evaluated for broader compatibility. Urdu speech output depends on the device's installed TTS voices.

## Android boundaries
Android controls app visibility, microphone access, notifications, background execution, and third-party UI access. Idraak uses supported Android intents and permissions; it does not silently send messages, place calls, or bypass system security. Reminder delivery can be delayed by Android battery-management policies, and notifications must be allowed.

## Build and verification
The GitHub Actions workflow uses JDK 17 and Gradle 8.9, assembles the debug APK, runs JVM command tests, and uploads the APK artifact.
