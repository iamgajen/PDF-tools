package com.pdftools.app.domain.pdf

enum class PdfToImagePageSelectionMode(val label: String) {
    ALL_PAGES("All pages"),
    SELECTED_PAGES("Selected pages"),
    PAGE_RANGE("Page range")
}
