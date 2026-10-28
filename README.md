# InstaMediaSaver — Native Android Instagram Photo, Reel & Carousel Downloader

A native Android application built with **Kotlin** and **Jetpack Compose (Material 3)** that previews and downloads public Instagram photos, Reels, and multi-item carousel posts that you own or have permission to save.

## Key Features

- **Paste or Share Instagram Links**: Paste any public `/p/{shortcode}` or `/reel/{shortcode}` URL directly, or share directly from the Instagram Android app via **Android's Share menu (`Intent.ACTION_SEND`)**.
- **Carousel & Multi-Item Preview**: Previews post metadata (`@username`, caption, media type) and all items in single or `CAROUSEL_ALBUM` posts with checkboxes to **Download Selected** or **Download All**.
- **Original-Quality Gallery Saving (Scoped Storage)**: Streams original image (`.jpg`) and video (`.mp4`) files directly into `Pictures/InstaMediaSaver` and `Movies/InstaMediaSaver` via Android `MediaStore` (`IS_PENDING = 1` -> `0`). Requires **zero storage permissions** on Android 10–15 (API 29–35).
- **Real-Time Progress & Honest Error Reporting**: Displays byte-level download progress, completion summaries, and clear explanations when Meta's API omits `media_url` (such as videos containing copyrighted Instagram Audio Library tracks or disabled Reel download permissions).
- **Download History & Open File**: Persists downloaded items in a local **Room SQLite database** with one-tap **Open File** (`Intent.ACTION_VIEW`) and **Share** buttons.
- **Dark Mode, Large Buttons & English + Arabic (`RTL`)**: High-contrast dark theme by default, minimum `54dp–56dp` primary action buttons, and full English (`values/strings.xml`) and Arabic (`values-ar/strings.xml`) localization with an in-app language switcher.

## Compliance & Official API Limitations

1. **No Password or Session Cookie Harvesting**: The app never asks for your Instagram login credentials, never embeds a WebView login scraper, and never stores session cookies.
2. **Official Meta Instagram Graph API (`v26.0`)**:
   - Meta shut down the consumer *Instagram Basic Display API* on December 4, 2024. Official API access now requires an **Instagram Professional account (Business or Creator)** via the **Instagram Graph API** (`GET /{ig-user-id}/media`) or **Business Discovery API**.
   - **Personal Accounts & Private Posts**: Cannot be accessed via official Meta APIs.
   - **Copyrighted Audio & Reel Download Controls**: Meta omits `media_url` on video/Reel responses if the video uses licensed audio from the Instagram Audio Library or if another creator disabled Reel downloads. The app detects this and shows a clear `RESTRICTED` badge on affected items rather than faking a download.
3. **Keeping Secret API Keys on the Server (`backend-proxy/`)**:
   - Embedding long-lived Graph API tokens or third-party API keys inside an APK is insecure because APKs can be decompiled.
   - Use the included `backend-proxy/` server to store `IG_ACCESS_TOKEN` and `IG_USER_ID` in server environment variables and connect the Android app via Settings -> **Backend Proxy Server**.

## How to Build & Install the APK

### Method 1: Automated Build via GitHub Actions (No Local Android Studio Required)
1. Create a new repository on GitHub (can be private or public) and push this project folder.
2. Open your repository on GitHub and click the **Actions** tab.
3. Select the **Build Android APK** workflow (`.github/workflows/build-apk.yml`) and click **Run workflow** (it also runs automatically on every push to `main`).
4. Once the run completes (~2–3 minutes), scroll down to the **Artifacts** section at the bottom of the workflow summary page and download **`InstaMediaSaver-Installable-APK.zip`**.
5. Extract `InstaMediaSaver-debug.apk`, transfer it to your Android phone, tap the file in your phone's Files app, allow *Install unknown apps* if prompted, and tap **Install**.

### Method 2: Local Build with Android Studio / Gradle CLI
1. Install **Android Studio Ladybug (or newer)** with **JDK 17** and **Android SDK Platform 35**.
2. Open the `InstaMediaSaver` project folder in Android Studio, or run from terminal:
   ```bash
   ./gradlew assembleDebug
   ```
3. The installable APK will be generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
4. Install onto a connected Android phone via USB debugging:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
