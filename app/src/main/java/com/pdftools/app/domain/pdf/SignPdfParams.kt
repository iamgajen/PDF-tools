package com.pdftools.app.domain.pdf

import android.net.Uri

data class SignPdfParams(
    val inputUri: Uri,
    val baseFileName: String,
    val signatures: List<SignatureSpec>
)
