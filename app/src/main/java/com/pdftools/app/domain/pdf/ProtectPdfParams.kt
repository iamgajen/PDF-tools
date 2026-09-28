package com.pdftools.app.domain.pdf

import android.net.Uri

data class ProtectPdfParams(
    val inputUri: Uri,
    val baseFileName: String,
    val password: String,
    val preventPrinting: Boolean = false,
    val preventCopying: Boolean = false,
    val preventModifying: Boolean = false
)
