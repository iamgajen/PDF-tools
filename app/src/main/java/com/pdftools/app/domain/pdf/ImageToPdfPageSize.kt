package com.pdftools.app.domain.pdf

enum class ImageToPdfPageSize(
    val title: String,
    val description: String,
    val widthPoints: Float,
    val heightPoints: Float
) {
    A4(
        title = "A4",
        description = "Standard ISO A4 page (210 x 297 mm)",
        widthPoints = 595.28f,
        heightPoints = 841.89f
    ),
    LETTER(
        title = "Letter",
        description = "US Letter page (8.5 x 11 in)",
        widthPoints = 612f,
        heightPoints = 792f
    ),
    ORIGINAL(
        title = "Original / Fit",
        description = "Page size matches image dimensions",
        widthPoints = 0f,
        heightPoints = 0f
    )
}
