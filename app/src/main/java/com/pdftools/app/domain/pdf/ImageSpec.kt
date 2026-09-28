package com.pdftools.app.domain.pdf

import android.net.Uri

data class ImageSpec(
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val name: String = "image"
)
