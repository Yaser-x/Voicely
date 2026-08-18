# Voicely

**Sound. React. Dominate.**

Voicely is a gaming-first Android soundboard for quick meme/reaction playback, personal audio libraries and an in-game floating controller.

## What is implemented

- Kotlin + Jetpack Compose UI
- Voicely dark gaming visual identity
- Custom launcher/adaptive icon
- MP3, WAV and OGG import through the Android document picker
- Persistent local sound library
- SoundPool playback with multiple simultaneous streams
- Search and favorites
- Gaming, Memes and Reactions board views
- Local microphone recording studio
- Floating Game Mode service
- Microphone and overlay permission flow
- Foreground-service support for the floating controller
- Android 26+ / target SDK 35
- GitHub Actions Android build pipeline

## Audio architecture

The normal soundboard is completely usable without USB hardware.

Voicely intentionally does **not** pretend that a normal Android app can create a universal virtual microphone and inject arbitrary PCM audio into another game's microphone stream. Android does not expose that public API to ordinary apps.

For users who need true voice + soundboard mixing into a game's physical microphone input, the project keeps a hardware-audio path in its architecture. That is optional and depends on the phone, game and external audio hardware.

## Privacy

- Imported sounds stay local to the app unless the user explicitly exports/shares them.
- Microphone access is requested only for recording features.
- Overlay access is requested only for Game Mode.
- No account or cloud backend is required for the core soundboard.

## Build

Open the repository in Android Studio or use the included GitHub Actions workflow to build the debug APK.

## Project direction

The product is intentionally being built in layers: core soundboard first, then richer board editing, studio/export improvements, Game Mode polish, optional hardware-audio compatibility, performance testing and release hardening.

Use only audio you have permission to use and follow the rules of the games and platforms where Voicely is used.
