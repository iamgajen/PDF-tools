package com.pdftools.app.domain.pdf

enum class ImageToPdfQuality(
    val title: String,
    val description: String,
    val targetMaxDimension: Int,
    val jpegQuality: Float
) {
    HIGH(
        title = "High",
        description = "Preserve original image quality",
        targetMaxDimension = 2560,
        jpegQuality = 0.92f
    ),
    BALANCED(
        title = "Balanced",
        description = "Moderate optimization for size and clarity",
        targetMaxDimension = 1600,
        jpegQuality = 0.80f
    ),
    SMALL(
        title = "Small",
        description = "Reduced image resolution and higher compression",
        targetMaxDimension = 1080,
        jpegQuality = 0.60f
    )
}
