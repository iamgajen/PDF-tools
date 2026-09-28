package com.pdftools.app.domain.pdf

import android.graphics.Bitmap

data class SignatureSpec(
    val id: String,
    val pageIndex: Int,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val rotationDegrees: Float = 0f,
    val bitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignatureSpec) return false
        return id == other.id &&
                pageIndex == other.pageIndex &&
                normalizedX == other.normalizedX &&
                normalizedY == other.normalizedY &&
                normalizedWidth == other.normalizedWidth &&
                normalizedHeight == other.normalizedHeight &&
                rotationDegrees == other.rotationDegrees
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + pageIndex
        result = 31 * result + normalizedX.hashCode()
        result = 31 * result + normalizedY.hashCode()
        result = 31 * result + normalizedWidth.hashCode()
        result = 31 * result + normalizedHeight.hashCode()
        result = 31 * result + rotationDegrees.hashCode()
        return result
    }
}
