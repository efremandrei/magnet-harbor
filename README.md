# Magnet Harbor

Magnet Harbor is a privacy-friendly Android torrent metasearch client scaffold. It searches enabled providers concurrently, normalizes their results, and hands magnet links to a torrent client installed by the user. It does **not** download or stream torrent content itself.

Magnet Harbor searches only the live sources that you configure. It ships with no preconfigured trackers, credentials, or hardcoded site scrapers.

## Current features

- Kotlin and Jetpack Compose Material 3 UI
- Concurrent, failure-isolated multi-source search
- Torznab/Jackett and Prowlarr API adapters with connection testing
- Persistent source manager with enable/disable controls and source health diagnostics
- Result normalization, multi-source info-hash deduplication, category and advanced filters
- Room-backed favorites and search history
- DataStore-backed zero-seeder and dark-theme preferences
- Android voice search
- Open, copy, and share magnet links
- Unit coverage for source selection, failure isolation, and deduplication

## Build on the remote PC

Requirements:

- Android Studio with Android SDK 36
- JDK 17 (Android Studio's bundled JDK is suitable)
- Internet access for the first Gradle dependency sync

Steps:

1. Clone the repository.
2. Open the repository root in Android Studio.
3. Allow Gradle sync to finish.
4. Select the `app` configuration and your phone or emulator.
5. Use **Run** for an installed debug build.
6. Use **Build > Build APK(s)** to create an APK.

Command-line equivalent:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK will be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows PowerShell, run `./gradlew.bat testDebugUnitTest assembleDebug`.

## Adding a live source

Use the **Sources** tab in the application. Add either:

- **Torznab / Jackett**: a full Torznab endpoint or a server URL. A bare server URL is completed with `/api`.
- **Prowlarr API**: the Prowlarr server URL and API key. Magnet Harbor uses Prowlarr's `/api/v1/search` endpoint.

Use **Test** before enabling a source. API keys are encrypted locally with the Android Keystore and are only sent to the source you configured. Do not include keys in commits or screenshots.

## Responsible use

BitTorrent is a general-purpose distribution protocol. Search and download only material you are legally permitted to access. Do not copy the branding, artwork, or proprietary code of another application.

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
