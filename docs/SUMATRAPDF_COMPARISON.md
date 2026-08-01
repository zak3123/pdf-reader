# SumatraPDF Comparison

This comparison is based on the official SumatraPDF source checkout at `D:\sumatrapdf-reference` and the LitePDF Android implementation in this repository. No SumatraPDF code, executable, icon, or branding asset was copied.

## Visual Alignment Implemented

- Home screen changed from a large Material dashboard into a compact file-oriented recent document list.
- Reader toolbar changed toward SumatraPDF's command order: close/open, file name, previous page, page number, total pages, next page, fit/zoom, search/sidebar, overflow.
- Document canvas uses a neutral gray background with white pages and a thin page shadow/border.
- Tablet navigation remains a persistent side panel instead of an overlay.
- Sidebar content already contains outline, bookmarks/favorites, and thumbnails.

## Behavior Alignment Already Present

- Recent documents and resume last page.
- Single page, continuous, and horizontal page modes.
- Dynamic page aspect ratio.
- Metadata rotation plus manual rotation.
- Fit width behavior and zoom re-rendering bounds.
- Search with highlighted results.
- Page thumbnails, outline, PDF links, and bookmarks.

## Android-Specific Differences

- Toolbar icons keep 48dp touch targets, so the bar is visually larger than a Windows toolbar.
- The desktop menubar is represented by Android toolbar/overflow actions.
- Multi-document tabs are not fully implemented yet to avoid keeping multiple `PdfRenderer` sessions and large bitmaps active on low-RAM devices.
- Facing pages and book view remain staged work.

## Screenshot Status

Screenshot comparison was not completed in this environment:

- SumatraPDF desktop was not launched here.
- ADB did not expose an Android device/emulator for runtime screenshots.

The next visual QA pass should capture:

- SumatraPDF open document window.
- LitePDF reader on phone portrait.
- LitePDF reader on tablet landscape.
- Sidebar outline/bookmarks/thumbnails.
- Search active state.
- Home/recent documents screen.
