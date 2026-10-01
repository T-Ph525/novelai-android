# NovelAI Android 2.0

A modern Android WebView client for NovelAI, rebuilt from the original AmazingGabriel16/novelai project.

## Improvements

- Kotlin + Jetpack Compose.
- Material 3 UI with full-screen immersive mode.
- Custom vector NovelAI app icon.
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

## GitHub Actions Build & Publish

The repository includes `.github/workflows/android.yml`.

On every push or pull request, GitHub Actions:

1. Checks out the source.
2. Installs JDK 17 and Gradle.
3. Builds both Debug and Release APKs (`assembleDebug` and `assembleRelease`).
4. Uploads the APKs as workflow artifacts (`NovelAI-debug-apk` and `NovelAI-release-apk`).
5. Automatically publishes a GitHub Release with the compiled APKs whenever a new version tag (e.g. `v1.0.0`) is pushed.

You can also start the workflow manually from the **Actions** tab with **Run workflow**.
