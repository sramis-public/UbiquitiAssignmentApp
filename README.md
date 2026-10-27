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

- **Categories:** The project uses exact, case-sensitive UIDB `deviceType` values:
  Cameras includes `camera`; IoT includes `sensor`, `light`, `doorlock`, and `chime`.
  All includes every product, including unknown or missing types. This mapping is
  a project assumption. Category and product-line filters combine locally without
  extra network requests or database queries.
- **Caching:** Room persists the catalog across launches. Revalidation runs once
  per process and on manual refresh, using ETags when available. Failed refreshes
  preserve cached data, and failed reads retain the last observed products. Retry
  restarts failed reads and refreshes. There is no expiry or periodic refresh;
  offline images are not guaranteed.
- **Data quality:** Products without a valid ID or name are skipped; duplicate IDs
  keep the first valid record. Malformed JSON or incorrect field types fail the
  refresh and preserve the cache. Skipped-record reporting is not implemented.
- **UI:** Material 3 typography and components use the Figma color palette; dark
  mode is a project adaptation. Details show the image, name, line, and short names.
  Missing details display an em dash.
- **Navigation/state:** Navigation saves the selected product ID. Filters survive
  refreshes and configuration changes but reset after process death.

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
