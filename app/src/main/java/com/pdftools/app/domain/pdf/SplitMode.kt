package com.pdftools.app.domain.pdf

sealed interface SplitMode {
    object EveryPage : SplitMode
    data class ByRanges(val rangeString: String) : SplitMode
    data class ExtractPages(val pagesString: String) : SplitMode
}
