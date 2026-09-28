package com.pdftools.app.domain.pdf

sealed interface PageRangeParseResult {
    data class Success(val pageGroups: List<List<Int>>) : PageRangeParseResult
    data class Error(val message: String) : PageRangeParseResult
}

object PageRangeParser {

    fun parseRanges(input: String, totalPageCount: Int): PageRangeParseResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) {
            return PageRangeParseResult.Error("Page range input cannot be empty.")
        }

        if (totalPageCount <= 0) {
            return PageRangeParseResult.Error("Invalid document page count.")
        }

        val parts = trimmedInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return PageRangeParseResult.Error("Page range input cannot be empty.")
        }

        val groups = mutableListOf<List<Int>>()

        for (part in parts) {
            val rangeIndices = parsePart(part, totalPageCount)
            when (rangeIndices) {
                is PartParseResult.Error -> return PageRangeParseResult.Error(rangeIndices.message)
                is PartParseResult.Success -> groups.add(rangeIndices.indices)
            }
        }

        return PageRangeParseResult.Success(groups)
    }

    fun parseExtractPages(input: String, totalPageCount: Int): PageRangeParseResult {
        val parseResult = parseRanges(input, totalPageCount)
        return when (parseResult) {
            is PageRangeParseResult.Error -> parseResult
            is PageRangeParseResult.Success -> {
                val combinedIndices = parseResult.pageGroups.flatten().distinct()
                if (combinedIndices.isEmpty()) {
                    PageRangeParseResult.Error("No valid pages selected.")
                } else {
                    PageRangeParseResult.Success(listOf(combinedIndices))
                }
            }
        }
    }

    private sealed interface PartParseResult {
        data class Success(val indices: List<Int>) : PartParseResult
        data class Error(val message: String) : PartParseResult
    }

    private fun parsePart(part: String, totalPageCount: Int): PartParseResult {
        if (part.contains("-")) {
            val dashCount = part.count { it == '-' }
            if (dashCount > 1) {
                return PartParseResult.Error("Invalid range format: '$part'.")
            }

            val tokens = part.split("-").map { it.trim() }
            if (tokens.size != 2 || tokens[0].isEmpty() || tokens[1].isEmpty()) {
                return PartParseResult.Error("Invalid range format: '$part'.")
            }

            val start = tokens[0].toIntOrNull()
                ?: return PartParseResult.Error("Invalid input '$part': '${tokens[0]}' is not a valid number.")
            val end = tokens[1].toIntOrNull()
                ?: return PartParseResult.Error("Invalid input '$part': '${tokens[1]}' is not a valid number.")

            if (start <= 0 || end <= 0) {
                return PartParseResult.Error("Page numbers must be 1 or greater.")
            }

            if (start > end) {
                return PartParseResult.Error("Invalid range '$part': start page ($start) cannot be greater than end page ($end).")
            }

            if (start > totalPageCount || end > totalPageCount) {
                val maxExceeded = maxOf(start, end)
                return PartParseResult.Error("Page $maxExceeded exceeds total page count of $totalPageCount.")
            }

            val indices = (start..end).map { it - 1 }
            return PartParseResult.Success(indices)
        } else {
            val pageNum = part.toIntOrNull()
                ?: return PartParseResult.Error("Invalid input '$part': not a valid number.")

            if (pageNum <= 0) {
                return PartParseResult.Error("Page numbers must be 1 or greater.")
            }

            if (pageNum > totalPageCount) {
                return PartParseResult.Error("Page $pageNum exceeds total page count of $totalPageCount.")
            }

            return PartParseResult.Success(listOf(pageNum - 1))
        }
    }
}
