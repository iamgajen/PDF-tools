package com.pdftools.app.domain.pdf

import android.net.Uri

data class UnlockPdfParams(
    val inputUri: Uri,
    val baseFileName: String,
    val password: String
)
