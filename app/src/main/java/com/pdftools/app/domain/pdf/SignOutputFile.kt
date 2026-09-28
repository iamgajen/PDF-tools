package com.pdftools.app.domain.pdf

import android.net.Uri
import java.io.File
import java.util.Locale

data class SignOutputFile(
    val file: File,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val pageCount: Int
) {
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
        }
}
