# LitePDF

LitePDF is a lightweight offline Android PDF reader built with Kotlin, Jetpack Compose, Material 3, Room, DataStore Preferences, and Android `PdfRenderer`.

The app is inspired by fast, minimal PDF readers, but it is an original Android application. It does not use the SumatraPDF name, branding, icons, or source code.

## Implemented Features

- Open local PDFs with Android Storage Access Framework.
- Accept `ACTION_VIEW` PDF intents from other apps through `content://` URIs.
- Persist read permission when the document provider allows it.
- Render PDF pages lazily in a vertical reader.
- Pinch-to-zoom, double-tap zoom, and panning while zoomed.
- Previous, next, and jump-to-page controls with validation.
- Phone/tablet adaptive navigation: drawer on phones and a persistent side panel on wide screens.
- Continuous and single-page reading modes.
- Reader controls for zoom in, zoom out, fit width, and page rotation.
- Offline text search for PDFs with extractable text, backed by PDFBox Android.
- Hide/show reader toolbar with a tap.
- Recent documents with last opened time, last page, page count, and file size.
- Graceful handling for unavailable, empty, corrupted, encrypted, or unsupported documents.
- Bookmarks stored per document.
- Light, dark, and system theme modes.
- DataStore settings for page spacing, keep-awake, resume page, and page controls.
- English and Indonesian string resources.
- No `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, advertising, or tracking permissions.

## Screenshots

Screenshots are not included yet because this environment does not provide an emulator or physical device for visual capture. Suggested placeholders:

- Home screen with recent documents.
- PDF reader with page controls.
- Bookmark list bottom sheet.
- Settings screen in dark mode.

## Architecture

LitePDF uses a small repository-based architecture:

- `data/database`: Room database, DAOs, and entities for recent documents and bookmarks.
- `data/datastore`: DataStore Preferences implementation for settings.
- `data/repository`: repository implementation that validates documents and persists metadata.
- `domain/model`: app-level models.
- `domain/repository`: repository contracts.
- `pdf`: replaceable PDF engine abstraction plus the Android `PdfRenderer` implementation and bitmap cache.
- `ui`: Compose screens, components, and theme.
- `navigation`: route definitions.
- `util`: page validation, stable document IDs, and formatting helpers.

The `PdfEngine` abstraction keeps the UI independent from Android `PdfRenderer`, so a future PDFium or MuPDF implementation can be added without rewriting the reader screen.

## Build Requirements

- Android Studio with Android SDK platform 36 installed.
- JDK 17 or newer.
- Gradle compatible with Android Gradle Plugin 8.12.0.
- Kotlin 2.2.0.

This repository was created in an environment that initially exposed only a Java 8 JRE and no Android SDK. For verification, a local JDK 17 and Android SDK were bootstrapped under `work/`; Android Studio users can instead use their normal installed SDK and JDK 17+.

## Android Studio Setup

1. Open this folder in Android Studio.
2. Install any prompted Android SDK components, especially API 36 and current build tools.
3. Ensure Gradle uses JDK 17 or newer.
4. Sync the project.

## Build a Debug APK

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install with ADB

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## File Permissions

LitePDF uses `ACTION_OPEN_DOCUMENT` and persisted read URI permissions where Android allows them. Documents are read from their original provider through `content://` URIs. LitePDF does not request broad storage permissions and does not duplicate every PDF into app storage.

If access to a recent document is revoked, LitePDF prompts the user to locate the file again.

## Privacy

LitePDF reads documents locally on this device. The application does not upload documents, display advertisements, or use analytics.

The manifest intentionally omits:

- `android.permission.INTERNET`
- `MANAGE_EXTERNAL_STORAGE`
- legacy broad external storage permissions
- advertising permissions

## Android PdfRenderer Limitations

Android `PdfRenderer` is compact and available on-device, but it is not a complete PDF platform. LitePDF adds text search through PDFBox Android for PDFs with extractable text, but scanned/image-only PDFs need OCR and encrypted PDFs still need a stronger engine. Version 1 does not support password entry, annotations, text selection, table of contents, or advanced PDF links. Some of those features require replacing or extending the rendering engine with PDFium or MuPDF.

LitePDF intentionally uses its own name, icon, code, and Android-native interface. It is not SumatraPDF and does not copy SumatraPDF branding or source code.

## Known Issues

- Zoom currently scales the rendered page bitmap rather than re-rendering at every zoom level.
- Page rotation is a display transform, not a saved document edit.
- Page aspect ratio uses a stable placeholder while the page is loading.
- Password-protected PDFs show an unsupported-document message.
- Search works only when text can be extracted from the PDF; image scans need OCR.
- No thumbnails or table of contents in version 1.

## Roadmap

See [ROADMAP.md](ROADMAP.md).
