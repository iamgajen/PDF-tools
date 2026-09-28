package com.pdftools.app.domain.pdf

import android.net.Uri
import java.util.Locale
import java.util.UUID

data class PdfFile(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val pageCount: Int? = null,
    val isEncrypted: Boolean = false,
    val isValid: Boolean = true,
    val errorMessage: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(size)

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
            return "$formatted ${units[digitGroups]}"
        }
    }
}
