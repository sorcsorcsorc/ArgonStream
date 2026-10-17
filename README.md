# Argon Stream

A native Android streaming app built with Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, Retrofit/OkHttp and AndroidX Media3 (ExoPlayer).

Created by **sorcsorcsorc**.

## Features

- Browse movies and series from Cinemeta (free, no key needed) or TMDB (optional key).
- Search, watchlist, favourites and Continue Watching with resume per episode.
- Find streams through Stremio-compatible add-ons and TorBox, with instant (cached) streams marked.
- Built-in player with subtitles, audio track selection, playback speed, fit/zoom, and external player hand-off.
- Keys are stored encrypted in the Android Keystore and redacted from logs.

## Requirements

- Android 8.0 (API 26) or newer.

## Build

1. Open the project in Android Studio (JDK 17+) and let Gradle sync.
2. Debug build: `./gradlew assembleDebug`
3. Signed release build: copy `keystore.properties.example` to `keystore.properties`, fill in your key details, then run `./gradlew assembleRelease`. Keep your `.jks` file and passwords out of version control.
4. Unit tests and layout screenshots: `./gradlew testDebugUnitTest` (screenshots are written to `app/build/screenshots/`).

## Using your own keys

- **TMDB (optional):** create a key at https://www.themoviedb.org/settings/api and paste it in Settings. Both the v3 API key and the v4 Read Access Token work.
- **TorBox (optional):** paste your API token from https://torbox.app/settings in Settings.
- **Stream add-ons:** install any Stremio-compatible manifest URL in Add-ons.

Argon does not include any keys, add-ons or content.

## Credits

- Metadata from [Cinemeta](https://v3-cinemeta.strem.io) and [TMDB](https://www.themoviedb.org). This product uses the TMDB API but is not endorsed or certified by TMDB.
- Playback by [AndroidX Media3](https://developer.android.com/media/media3).
- Built with the help of [Claude Code](https://claude.com/claude-code).

## Disclaimer

Argon is a player and catalog browser. You are responsible for the content you access and for complying with the laws where you live.
