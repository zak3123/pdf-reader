# SumatraPDF UI Reference

This document records the SumatraPDF source review used to shape LitePDF's Android UI. The implementation in LitePDF is a Kotlin/Jetpack Compose reimplementation; no SumatraPDF source code was copied or translated.

## Source Reviewed

- Reference checkout: `D:\sumatrapdf-reference`
- License files: `COPYING`, `COPYING.BSD`, `AUTHORS`
- UI files reviewed:
  - `src/Toolbar.cpp`
  - `src/Toolbar.h`
  - `src/Menu.cpp`
  - `src/DisplayMode.cpp`
  - `src/DisplayMode.h`
  - `src/FindWindow.cpp`
  - `src/Tabs.cpp`
  - `src/MainWindow.cpp`

## License Notes

SumatraPDF source is primarily GPLv3, with BSD-licensed parts under `src/base` and `src/mui`. Its MuPDF dependency introduces AGPLv3 section 13 considerations. LitePDF does not copy SumatraPDF code, assets, icons, or branding. The LitePDF UI follows behavior and layout patterns only.

## Toolbar Structure

`src/Toolbar.cpp` defines a compact desktop toolbar with small icon buttons and separators. The built-in order includes open, print, page controls, previous/next page, navigation back/forward, layout shortcuts, rotate left/right, zoom out/in, and search.

Android adaptation:

- Keep a low-height toolbar.
- Use 48dp touch targets but visually compact icons.
- Put page navigation near the left/middle: previous, editable current page, total pages, next.
- Put zoom and fit controls after page controls.
- Keep search and sidebar close to the right side.
- Move desktop-only commands into an overflow menu on narrow screens.

## Menu Structure

`src/Menu.cpp` organizes commands around File, View, Zoom, Go To, Favorites, and Settings-like actions. LitePDF mirrors this in an Android overflow menu instead of a desktop menubar.

Android adaptation:

- File: open/share/print/properties/close.
- View: single page, continuous, facing/book modes where available, toolbar/sidebar/theme.
- Zoom: fit page, fit width, actual size, zoom in/out, common percentages.
- Go To: first/previous/next/last/go to page.
- Rotate: left/right/reset.

## Display Modes

`src/DisplayMode.cpp` distinguishes single, continuous, facing, and book view variants. LitePDF currently supports continuous, single page, and horizontal page mode. Facing/book view should be added as a dedicated layout mode before claiming parity.

## Sidebar

SumatraPDF uses a functional left sidebar for table of contents and favorites, with thumbnails as a dense navigation aid. LitePDF's Android sidebar should avoid Material cards and use compact rows:

- Table of Contents
- Bookmarks/Favorites
- Page thumbnails

Phone adaptation: drawer from the left, about 80-88% width.

Tablet adaptation: persistent side rail beside the document canvas.

## Canvas

The document canvas is utilitarian: neutral gray background, white pages, thin shadow, centered pages, no decorative cards. LitePDF uses a gray canvas and white page surface; page corners should remain square or minimally rounded.

## Search

Search is a small command surface rather than a full page. Android should expose a compact search row with query field, previous/next result, result count, and close. Highlight geometry must share the page transform used by bitmap/link overlays.

## Android Differences

- Touch targets need to be larger than desktop toolbar icons.
- Narrow screens require overflow controls.
- Multi-document tabs are possible but should be staged carefully because each active PDF renderer and bitmap cache increases memory pressure.
- Android `PdfRenderer` has password and rendering limitations compared with SumatraPDF's desktop engine stack.

## Current LitePDF Gaps

- Multi-document tabs are not complete.
- Facing pages and book view are not complete.
- Search UI is still mostly sidebar based.
- Overflow menu is not yet a full desktop menu equivalent.
- No screenshot comparison was possible in this environment because SumatraPDF desktop was not launched and ADB did not expose an Android device.
