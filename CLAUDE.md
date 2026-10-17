# Argon Stream

Native Android app (Kotlin, Jetpack Compose, Hilt, Room, Media3). Single module `:app`.

## Git workflow (owner's standing instruction)

- Remote: private GitHub repo `ArgonStream` (`origin`, branch `main`).
- After finishing each change, commit it with a clear message and `git push` to `origin main`. No need to ask first.
- Never commit signing secrets: `keystore.properties`, `*.jks`, `local.properties` are git-ignored and must stay that way.

## Build & verify (Windows)

- Gradle needs `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp\uds` on this machine, otherwise it fails with "Unable to establish loopback connection".
- `gradlew testDebugUnitTest` runs unit tests and renders every screen to `app/build/screenshots/*.png` (Robolectric); review these for layout changes.
- `gradlew assembleRelease` produces the signed APK (`app/build/outputs/apk/release/app-release.apk`) using `keystore.properties`.
- The Android emulator cannot run here (no hypervisor driver), so use the screenshot test for layout checks.
