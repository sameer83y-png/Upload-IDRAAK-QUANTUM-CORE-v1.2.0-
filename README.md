# Idraak Quantum Core

An offline-first Android assistant foundation designed around local command execution, explicit local memory, and permission-aware phone actions.

## Current capabilities
- Native Android dashboard with original Idraak Quantum Core vector branding and adaptive launcher icon.
- Offline command router with Urdu and English phrase coverage.
- Opens phone settings and camera.
- Opens supported installed apps: YouTube, Chrome, ChatGPT, and Gemini.
- Opens YouTube and Google search results for spoken or typed search requests.
- Opens a phone dialer with a requested number; the user reviews and places the call.
- Opens the device alarm screen and sends numeric-time alarm requests to the Android Clock app (for example, `set alarm for 7:30 PM` or `الارم 19:30 لگاؤ`). The Clock app may ask the user to confirm.
- Local SQLite memory: explicit save, list, search, exact deletion, and correction history.
- Urdu text-to-speech when the device has a compatible Urdu voice.
- On-device speech recognition when Android 12+ and a compatible offline Urdu speech pack are available. No automatic network-recognition fallback is used.

## Local memory commands
- `remember that <fact>` — save a fact on this device.
- `what do you remember` — list saved memories.
- `search memory for <words>` — find matching entries.
- `forget memory <exact text>` — remove an exact entry.
- `correct memory: <old text> -> <corrected text>` — update an exact entry and record the correction.
- `show correction history` — review saved corrections.

Memory is local to the app installation. It is not silently collected from conversations. Never save passwords, authentication codes, or other credentials.

## Voice and privacy
Voice input requests microphone permission at runtime. The strict on-device recognition path requires Android 12 or newer and an installed Urdu offline recognition pack. If the device does not provide this capability, Idraak reports that it is unavailable rather than silently sending audio to an online service. Urdu speech output also depends on installed device TTS voices.

## Build
The CI workflow uses JDK 17, Gradle 8.9, and Android Gradle Plugin 8.7.3. It assembles and uploads a debug APK.

## Architecture direction
1. Broader deterministic offline intent recognition and command coverage.
2. Permission-aware Android capability layer.
3. Voice input/output with explicit local-processing requirements.
4. User-controlled local memory and correction history.
5. Context-aware dashboard, reminders, and accessibility-assisted workflows.
6. Optional online AI provider, never required for standard phone commands.

Android security, app availability, runtime permissions, and OS restrictions are handled explicitly. Idraak reports execution outcomes rather than pretending an action succeeded.
