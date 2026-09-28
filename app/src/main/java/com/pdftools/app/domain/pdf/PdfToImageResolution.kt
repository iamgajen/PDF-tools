package com.pdftools.app.domain.pdf

enum class PdfToImageResolution(val label: String, val dpi: Int) {
    HIGH_300("Original / High (300 DPI)", 300),
    DPI_200("200 DPI", 200),
    DPI_150("150 DPI", 150),
    DPI_100("100 DPI", 100);

    val scaleFactor: Float
        get() = dpi / 72.0f
}
