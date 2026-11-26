# Idraak Quantum Core

An offline-first Android assistant foundation.

## Current foundation
- Native Android app shell with a dark command dashboard.
- Offline command routing separated from Android action execution.
- Initial actions: open phone settings, launch camera, open a website, and search YouTube or Google.
- GitHub Actions debug APK build.

## Architecture direction
1. Offline command understanding and intent classification.
2. Permission-aware Android capability layer.
3. Voice input/output and foreground voice session.
4. User-controlled local memory and correction history.
5. Context-aware dashboard, reminders, and accessibility-assisted workflows.
6. Optional online AI provider, never required for standard phone commands.

Android security, app availability, runtime permissions, and OS restrictions are handled explicitly. The app reports real execution outcomes rather than pretending an action succeeded.
