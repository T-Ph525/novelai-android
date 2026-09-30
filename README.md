# NovelAI Android 2.0

A modern Android WebView client for NovelAI, rebuilt from the original AmazingGabriel16/novelai project.

## Improvements

- Kotlin + Jetpack Compose.
- Material 3 UI.
- Android 8.0+ baseline.
- Edge-friendly modern activity architecture.
- Persistent WebView cookies and storage.
- Safe HTTPS-only WebView configuration.
- External links open outside the app.
- Android DownloadManager integration.
- Story/file downloads no longer require legacy storage permissions.
- Android back navigation works with WebView history.
- Hardware acceleration enabled.
- Modern SDK/Gradle configuration.

## Build

Open the project in Android Studio and sync Gradle. Then run the `app` configuration.

This project is a fan-made client and is not affiliated with NovelAI.

## GitHub Actions build

The repository includes `.github/workflows/android.yml`.

On every push or pull request, GitHub Actions:

1. Checks out the source.
2. Installs JDK 17.
3. Installs Gradle 8.11.1.
4. Generates `gradlew`, `gradlew.bat`, and the Gradle wrapper files.
5. Builds the debug APK with `assembleDebug`.
6. Uploads the APK as the `NovelAI-debug-apk` workflow artifact.

You can also start the workflow manually from the **Actions** tab with **Run workflow**.

The generated wrapper is intentionally not committed to the source ZIP because CI creates it reproducibly on every build.
