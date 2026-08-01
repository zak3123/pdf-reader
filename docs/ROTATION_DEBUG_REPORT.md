# Rotation Debug Report

## Current Implementation

- `AndroidPdfEngine` reads PDF page `/Rotate` metadata through PDFBox before opening `PdfRenderer`.
- `PdfRenderer.Page.render(...)` renders into an ARGB_8888 bitmap.
- The rendered bitmap is rotated in `AndroidPdfEngine.rotateForDisplay(pageRotation(pageIndex))`.
- The reader UI has a separate manual `pageRotation` state driven by the rotate action.
- Rotation normalization is centralized in `com.fatih.litepdf.util.normalizeRotation()`.
- `PageTransform` is the current page-geometry source for rotated dimensions, fit scale, and display size.
- `AndroidPdfEngine` logs `PdfRotationDebug` for source size, metadata rotation, rendered bitmap size, display bitmap size, and requested width.

## Double-Rotation Assessment

The current code separates metadata rotation and manual user rotation:

- Metadata rotation is applied in the engine bitmap.
- Manual user rotation is applied in Compose via `graphicsLayer { rotationZ = pageRotation.toFloat() }`.

This is not confirmed as a double-rotation bug from logs in this session. A physical/runtime check with a `/Rotate 90` and `/Rotate 270` PDF is still required before claiming final rotation correctness.

## Remaining Risk

Search highlight and link overlay geometry are drawn in the Compose page box. If the bitmap is already rotated by the engine and the user also rotates the page, overlays can become wrong unless the same page geometry model is used for bitmap, highlight, and links.

## Recommended Next Step

Wire `PageTransform` deeper into `ReaderScreen` overlays so bitmap, search highlight, and link hit testing all use the same geometry during manual rotation.
