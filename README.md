# IDRAAK QUANTUM CORE v1.2.0

## Build

1. Open this folder in Android Studio.
2. Install Android SDK 35 and JDK 17.
3. Sync Gradle.
4. Build > Build APK(s).

## Features in this foundation

- Android Home launcher intent.
- Python AI module through Chaquopy.
- Gemini API request.
- Hindi, English, Urdu and Arabic language choices.
- Male/Female avatar presentation.
- Android TextToSpeech voice selection where installed voices support it.
- Visible microphone foreground service.
- User-controlled microphone permission.
- Accessibility service that must be enabled manually.
- Globe long press opens IDRAAK settings.
- GitHub Actions debug APK artifact.

## Important limitations

The microphone service is user-started and displays a persistent notification.
Android may stop or restrict microphone use under its background policies.
Speech recognition language support depends on the installed recognition provider.

Accessibility service only reads available visible UI text in this foundation.
It does not bypass app security, OTP, payment confirmations, or login protections.

For release APKs, configure your own signing key and keep it safe.
Never publish your Gemini API key in a public repository.
A backend proxy is recommended for production API-key protection.
