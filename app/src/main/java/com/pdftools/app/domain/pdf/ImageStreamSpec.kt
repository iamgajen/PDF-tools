package com.pdftools.app.domain.pdf

import java.io.InputStream

data class ImageStreamSpec(
    val inputStreamProvider: () -> InputStream?,
    val rotationDegrees: Int = 0,
    val name: String = "image"
)
