# UI Before/After Report

## Bad Screenshots Reviewed

The user provided screenshots showing:

- Home toolbar rendered as a white/light bar in dark theme, making title and icons nearly invisible.
- Recent document rows overlapping because row height was fixed while filenames could wrap to two lines.
- Reader loading state displayed on a flat gray canvas with the spinner/text near the upper-left area, while sidebar handle remained visible.
- Sidebar handle appeared as a large black block over the PDF canvas.
- Reader bottom bar and top bar used light colors even when the app was in dark mode.

## Root Causes

- `HomeScreen` and `ReaderScreen` used `SumatraLikeColors.ToolbarLight` and `CanvasLight` directly instead of theme-aware colors.
- Recent document rows used a fixed `height(78.dp)`, so two-line filenames could draw into the divider and next row.
- Loading reused the normal reader document area and `LoadingMessage` did not fill/center inside the canvas.
- Sidebar handle used a narrow but full-height dark surface and stayed visible during opening.
- Bottom controls used a `background(...)` modifier, which does not provide a matching local content color for icons/text.

## Changes Made

- Added centralized dimensions in `LitePdfDimensions`.
- Strengthened light/dark `ColorScheme` values, including `onSurface`, `onBackground`, and `onSurfaceVariant`.
- Updated home top bar, divider, recent text, and recent item sizing to use `MaterialTheme.colorScheme`.
- Changed recent rows from fixed height to `heightIn(min = 88.dp)` with content-driven vertical padding.
- Added a centered `ReaderOpeningState` and hid the sidebar handle while opening.
- Updated reader top and bottom chrome to use themed `Surface` with matching content color.
- Replaced the black sidebar block with a smaller rounded handle that uses theme surface colors.

## Verification Status

- Build verification is recorded in the final task summary.
- Runtime screenshot verification was not performed in this session unless a device/emulator is explicitly available.
