package com.pdftools.app.domain.pdf

enum class CompressionLevel(
    val title: String,
    val description: String,
    val targetMaxDimension: Int,
    val jpegQuality: Float
) {
    HIGH_QUALITY(
        title = "High Quality",
        description = "Minimal quality reduction, gentle compression",
        targetMaxDimension = 1800,
        jpegQuality = 0.82f
    ),
    BALANCED(
        title = "Balanced",
        description = "Moderate compression, good balance of size and quality",
        targetMaxDimension = 1200,
        jpegQuality = 0.65f
    ),
    SMALLEST_SIZE(
        title = "Smallest Size",
        description = "Maximum compression, image quality may decrease",
        targetMaxDimension = 800,
        jpegQuality = 0.45f
    )
}
