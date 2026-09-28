package com.pdftools.app.domain.pdf

enum class PdfToImageFormat(val label: String, val extension: String, val mimeType: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png")
}
