package com.pdftools.app.domain.pdf

import android.net.Uri

data class PdfToImageParams(
    val inputUri: Uri,
    val baseFileName: String,
    val format: PdfToImageFormat,
    val quality: PdfToImageQuality,
    val resolution: PdfToImageResolution,
    val pagesToRender: List<Int>
)
