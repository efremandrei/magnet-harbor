# Magnet Harbor

Magnet Harbor is a privacy-friendly Android torrent metasearch client scaffold. It searches enabled providers concurrently, normalizes their results, and hands magnet links to a torrent client installed by the user. It does **not** download or stream torrent content itself.

This first development build uses deterministic demonstration providers. The complete search, filter, sort, favorite, history, voice-search, copy/share, and external-client workflows can therefore be developed and tested without depending on a third-party index.

## Current features

- Kotlin and Jetpack Compose Material 3 UI
- Concurrent, failure-isolated multi-source search
- Result normalization and info-hash deduplication
- Category filters and five sort modes
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

## Adding a real source

Implement `TorrentSource` and register it in `AppContainer`. Prefer documented JSON/XML APIs or a user-controlled Torznab endpoint over HTML scraping. Keep provider-specific parsing inside its adapter so one changed source cannot break the application.

```kotlin
class ExampleSource : TorrentSource {
    override val id = "example"
    override val displayName = "Example"

    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        // Call a documented endpoint, parse it, and return normalized results.
        TODO()
    }
}
```

Do not commit API keys. Store user-supplied endpoints and credentials locally, and add encrypted storage before introducing secrets.

## Responsible use

BitTorrent is a general-purpose distribution protocol. Search and download only material you are legally permitted to access. Do not copy the branding, artwork, or proprietary code of another application.

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
