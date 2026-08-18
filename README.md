# Voicely

A gaming-first Android soundboard built with Kotlin + Jetpack Compose.

## Current build

- Premium dark gaming UI
- Local MP3/WAV/OGG importing through Android's document picker
- Persistent local sound library
- Instant soundboard playback using Android SoundPool
- Favorites
- Search
- Gaming / Memes / Reactions board views
- Studio microphone recording
- Floating Game Mode overlay foundation
- Foreground service for Game Mode
- Microphone and overlay permission flow
- Android 26+ / target SDK 35
- GitHub Actions Android build

## Important audio limitation

Voicely does not claim to inject software audio directly into another app's microphone stream. Normal Android apps do not receive a public virtual-microphone injection API. For true in-game voice-chat mixing, the project is designed to remain compatible with legitimate external USB/analogue audio-mixer setups where the phone receives the mixed signal as a physical microphone input.

## Development direction

1. Soundboard and library
2. Game Mode overlay
3. Recording/studio improvements
4. Hardware/USB audio compatibility layer
5. Performance and device testing
6. Release hardening

Use only audio you have permission to use and follow the rules of the games and platforms where Voicely is used.
