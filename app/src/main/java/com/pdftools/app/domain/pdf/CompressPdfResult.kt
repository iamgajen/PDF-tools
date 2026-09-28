package com.pdftools.app.domain.pdf

import android.net.Uri
import java.io.File
import kotlin.math.roundToInt

sealed interface CompressPdfResult {
    data class Success(
        val file: File,
        val uri: Uri,
        val displayName: String,
        val originalSize: Long,
        val compressedSize: Long,
        val pageCount: Int
    ) : CompressPdfResult {
        val percentageSaved: Int
            get() = if (originalSize > 0) {
                (((originalSize - compressedSize).toDouble() / originalSize.toDouble()) * 100.0).roundToInt().coerceAtLeast(1)
            } else 0

        val formattedOriginalSize: String
            get() = PdfFile.formatFileSize(originalSize)

        val formattedCompressedSize: String
            get() = PdfFile.formatFileSize(compressedSize)
    }

    data class AlreadyOptimized(
        val file: File,
        val uri: Uri,
        val displayName: String,
        val originalSize: Long,
        val pageCount: Int,
        val message: String = "Your PDF is already well optimized."
    ) : CompressPdfResult {
        val formattedSize: String
            get() = PdfFile.formatFileSize(originalSize)
    }
}
