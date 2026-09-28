package com.pdftools.app.domain.pdf

data class PdfToImageResult(
    val outputFiles: List<PdfToImageOutputFile>,
    val totalInputPages: Int
)
