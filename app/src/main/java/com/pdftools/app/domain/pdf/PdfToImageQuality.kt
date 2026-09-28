package com.pdftools.app.domain.pdf

enum class PdfToImageQuality(val label: String, val compressQuality: Int) {
    HIGH("High Quality", 90),
    BALANCED("Balanced", 75),
    SMALL("Small Size", 50)
}
