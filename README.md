# Idraak Quantum Core

An offline-first Android assistant foundation, built to grow into a permission-aware mobile control assistant.

## Current foundation
- Native Android app shell with a dark command dashboard.
- Offline command routing separated from Android action execution.
- Initial actions: open phone settings, launch camera, open a website, and search YouTube or Google.
- Local SQLite memory with explicit user-triggered saving, searching, exact deletion, and correction history.
- GitHub Actions debug APK build.

## Local memory commands
- `remember that <fact>` — save a fact on this device.
- `what do you remember` — list saved memories.
- `search memory for <words>` — find matching entries.
- `forget memory <exact text>` — remove an exact entry.
- `correct memory: <old text> -> <corrected text>` — update an exact entry and record the correction.
- `show correction history` — review saved corrections.

Memory is local to the app installation. It is not silently collected from conversations. Do not save passwords, authentication codes, or other credentials.

## Build
The CI workflow uses JDK 17, Gradle 8.9, and Android Gradle Plugin 8.7.3.

## Architecture direction
1. Offline command understanding and intent classification.
2. Permission-aware Android capability layer.
3. Voice input/output and foreground voice session.
4. User-controlled local memory and correction history.
5. Context-aware dashboard, reminders, and accessibility-assisted workflows.
6. Optional online AI provider, never required for standard phone commands.

Android security, app availability, runtime permissions, and OS restrictions are handled explicitly. The app reports real execution outcomes rather than pretending an action succeeded.
