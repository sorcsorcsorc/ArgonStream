# Argon Stream (Native Android — Kotlin + Jetpack Compose + Media3 + TorBox)

Argon Stream is a native Android streaming application built with **Kotlin 2.1**, **Jetpack Compose (Material 3)**, **Hilt**, **Room**, **DataStore**, **Retrofit/OkHttp**, and **AndroidX Media3 ExoPlayer**.

## What's new in 1.1.0

- Works without any API key: catalogs, search and episode lists come from Cinemeta (Stremio's free catalog). A TMDB key is optional and accepts both the v3 API key and the v4 Read Access Token.
- Five-tab bottom bar (Home, Search, Library, Add-ons, Settings) with Movies/Series filters on Home; side rail in landscape and on tablets.
- Proper edge-to-edge layout: status bar, gesture bar, notch and keyboard insets handled on every screen.
- Player: auto-hiding controls, pause when the app is backgrounded, per-episode resume, stream HTTP headers, preferred audio/subtitle language, speed picker, fit/zoom toggle.
- Continue Watching now stores the real title and poster and clears finished titles.
- Settings that did nothing before (default player, max quality, prefer cached, subtitles off) now work.
- Fixed dialogs that could not be dismissed, "uncached" streams shown as Instant, and TMDB people appearing as series.

### Layout screenshots without a device

`gradlew testDebugUnitTest` renders every screen at phone sizes with Robolectric and writes PNGs to `app/build/screenshots/`.

---

## 1. API Key Configuration & Terms

All API keys are entered inside the app (**Settings → 1. TorBox & TMDB Keystore Vault**) and encrypted at rest using hardware-backed **Android Keystore (`AES/GCM/NoPadding` 256-bit)**. No credentials are hardcoded in source code, `BuildConfig`, or logs.

1. **TMDB API Key (v3):**
   - Generate a free API v3 key at `https://www.themoviedb.org/settings/api`.
   - Paste it in **Settings → Save TMDB Key**.
   - *Attribution Notice:* Argon uses the TMDB API for catalog metadata, posters, cast, seasons, and `imdb_id` resolution, but is not endorsed or certified by TMDB.
   - *Important:* Metadata availability on TMDB does not guarantee stream availability from add-ons or debrid providers.
2. **TorBox API Key:**
   - Copy your API token from your TorBox dashboard (`https://torbox.app/settings`).
   - Paste it in **Settings → Save TorBox Key** and tap **Test Connection**.
   - Verified Official Endpoints used (`https://api.torbox.app/v1/api/`):
     - `GET /user/me?settings=true` (Account & subscription verification)
     - `GET /torrents/checkcached?hash=...&format=object` (Batch instant-cache verification)
     - `POST /torrents/createtorrent` (Multipart `magnet` submission)
     - `GET /torrents/mylist` (Torrent status & multi-file inspection)
     - `GET /torrents/requestdl` (Playable link resolution; `token` parameter is automatically redacted by `RedactingLoggingInterceptor`)

---

## 2. Building & Installing on Your Xiaomi Phone (HyperOS / MIUI)

### Option A: Android Studio (Recommended)
1. Open Android Studio (Ladybug / Meerkat or newer with **JDK 17**).
2. Click **Open** and select the `ArgonStream` folder.
3. Let Gradle sync finish.
4. On your Xiaomi phone:
   - Go to **Settings → About phone** and tap **OS version** (HyperOS / MIUI version) 7 times to enable Developer Options.
   - Go to **Settings → Additional settings → Developer options**.
   - Enable **USB debugging** AND **Install via USB** (Xiaomi requires both switches enabled for direct ADB install).
5. Connect your Xiaomi phone via USB and click **Run ('app')** in Android Studio, or run `build-and-install-xiaomi.bat`.

### Option B: Producing a Signed Release APK
1. Generate a release keystore:
   ```bash
   keytool -genkey -v -keystore argon-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias argon_key