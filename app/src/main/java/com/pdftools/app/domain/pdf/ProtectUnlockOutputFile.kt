package com.pdftools.app.domain.pdf

import android.net.Uri
import java.io.File

data class ProtectUnlockOutputFile(
    val file: File,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val pageCount: Int,
    val isUnlocked: Boolean
)
