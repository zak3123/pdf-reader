# Open Source References

Reference repositories were cloned outside the LitePDF repo at `D:\pdf-reader-references` using shallow filtered clones. No reference repository `.git` directory is inside this project, and no source code was copied into LitePDF.

## Reviewed Repositories

| Repository | Local path | License finding | Usage in this change |
| --- | --- | --- | --- |
| PdfReaderPro | `D:\pdf-reader-references\PdfReaderPro` | GPLv3 (`LICENSE`) | Studied only for reader/home UX direction. No code copied. |
| AhmerPdfium | `D:\pdf-reader-references\AhmerPdfium` | README states Apache 2.0; no root LICENSE file found | Studied as possible future Pdfium engine reference. No code copied. |
| Infomaniak AndroidPdfView | `D:\pdf-reader-references\android-pdfview` | Apache 2.0 (`LICENSE`) | Studied fit/page navigation concepts. No code copied. |
| Android Compose Samples | `D:\pdf-reader-references\compose-samples` | Apache 2.0 (`LICENSE`) | Studied theme/token and adaptive Compose patterns. No code copied. |
| MuPDF Android Viewer | `D:\pdf-reader-references\mupdf-android-viewer` | AGPLv3 (`COPYING`) | Studied only at architecture level. No code copied because of AGPL obligations. |
| SumatraPDF | `D:\pdf-reader-references\sumatrapdf` | GPLv3 (`COPYING`) | Studied only for simple reader behavior. No code copied. |

## Adaptation Summary

- Adapted conceptually: controlled app colors, smaller reader chrome, document-first reader layout, non-desktop recent list behavior.
- Copied: none.
- Direct dependency added: none.

## License Constraints

GPLv3/AGPLv3 references are not used as source for implementation. Any future Pdfium migration should use a license-compatible dependency and add attribution/notice before merging.
