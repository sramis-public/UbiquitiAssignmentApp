# Ubiquiti test assignment

A Kotlin/Jetpack Compose app for browsing Ubiquiti products and viewing product details.

## How to run

Open the project in Android Studio with support for AGP 9.4.0, install
Android SDK 37, and sync Gradle. Gradle is configured to use JDK 25. Run the `app`
configuration on an Android 7.0 (API 24) or newer device/emulator. The first catalog
load requires internet access.

Build and check using the included wrapper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:lintDebug
```

## Assumptions and tradeoffs

- **Categories:** All includes every product, including missing or unknown device types.
  Categories use the top-level UIDB `deviceType`: Cameras matches `camera`; IoT
  matches `sensor`, `light`, `doorlock`, or `chime`. Other, missing, and blank types
  appear only in All. This exact, case-sensitive mapping is a project decision.
  Category changes filter the loaded snapshot immediately in source order without
  network requests or additional Room queries.
  The separate product-line filter intersects with the category and matches
  `unifi-network`, `unifi-lte`, `unifi-protect`, and `unifi-access` locally.
  Selection survives refreshes and configuration changes.
- **Caching:** Room stores the catalog across launches. Each application process
  starts one background revalidation using ETags when available. Failed refreshes
  retain cached data; empty/error states offer Refresh/Retry. There is no expiry
  or periodic refresh, and offline image availability is not guaranteed.
  Overlapping refresh requests are ignored by the repository; a later explicit
  refresh revalidates normally. Room observation runs for the ViewModel's lifetime,
  including while the UI is inactive, and is cancelled when the ViewModel is cleared.
  Read failures retain the last observed products; Retry restarts failed observation
  and requests a refresh. UI state collection remains lifecycle-aware.
- **Invalid products:** Records with missing, null, or blank IDs/names (including a
  missing or null product) are skipped. Duplicate IDs keep the first valid record
  in source order. The remaining products are cached; if none remain, the catalog
  is empty. Malformed JSON or incorrect field types still fail the refresh and
  preserve the cache. Logging skipped records and reporting them to the backend
  remains a TODO.
- **UI:** Default Material 3 typography and components use the Figma color palette.
  Dark mode is a project adaptation. Details show the product name and image.
- **Navigation/state:** Two-screen navigation uses a saveable product ID. Filters
  survive configuration changes but reset after process death.

## Libraries used and why

- **Compose Material 3:** declarative UI and standard components.
- **Lifecycle ViewModel/Compose:** screen state and lifecycle-aware collection.
- **Coroutines/Flow:** asynchronous work and observable state.
- **Retrofit + OkHttp:** HTTP requests and conditional cache validation.
- **kotlinx.serialization:** JSON parsing with unknown-field tolerance.
- **Room + KSP:** persistent catalog storage and generated database code.
- **Hilt:** dependency injection and application-scoped repository ownership.
- **Coil:** asynchronous image loading and image caching.
- **JUnit, Robolectric, MockWebServer, coroutines-test, Compose UI testing:**
  deterministic data/state tests and fixture-based UI tests.
