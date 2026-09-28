package com.pdftools.app.ui.imagetopdf

import android.net.Uri
import java.util.UUID

data class ImageToPdfItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val exifRotation: Int = 0,
    val userRotation: Int = 0,
    val isSelected: Boolean = false
) {
    val totalRotation: Int get() = (exifRotation + userRotation) % 360
}
