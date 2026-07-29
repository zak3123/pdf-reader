# Render Engine Benchmark

## Current Engine

LitePDF currently uses Android `PdfRenderer` for page rendering and PDFBox Android for metadata/search-related support.

## Current Session Findings

- No renderer replacement was made.
- No Pdfium dependency was added.
- No runtime benchmark comparing PdfRenderer and Pdfium was completed in this session.
- The current patch keeps rendering behavior stable and focuses on fixing UI contrast, loading, and layout regressions.

## Existing Instrumentation

`AndroidPdfEngine` logs render timing with tag `PdfRenderTiming`, including:

- page number;
- target bitmap size;
- render time;
- total time;
- thread name.

## Benchmark Still Needed

Use these PDFs before choosing a new engine:

- short text PDF;
- long book PDF;
- scanned high-resolution PDF;
- mixed portrait/landscape PDF;
- PDF with `/Rotate 90`;
- PDF with `/Rotate 270`;
- large PDF if available.

Measure:

- open document time;
- first page visible time;
- median and p95 render time;
- scroll stability;
- memory peak;
- cache hit ratio;
- orientation-change recovery.

## Decision

Keep Android `PdfRenderer` until a measured Pdfium proof of concept is faster, more stable, and license-compatible.
