package com.pdftools.app.domain.pdf

import org.junit.Assert.*
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun testSimpleRange() {
        val result = PageRangeParser.parseRanges("1-5", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(1, groups.size)
        assertEquals(listOf(0, 1, 2, 3, 4), groups[0])
    }

    @Test
    fun testCommaSeparatedPages() {
        val result = PageRangeParser.parseRanges("1,3,5", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(3, groups.size)
        assertEquals(listOf(0), groups[0])
        assertEquals(listOf(2), groups[1])
        assertEquals(listOf(4), groups[2])
    }

    @Test
    fun testMultipleRanges() {
        val result = PageRangeParser.parseRanges("1-5,8-10", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2, 3, 4), groups[0])
        assertEquals(listOf(7, 8, 9), groups[1])
    }

    @Test
    fun testOverlappingRanges() {
        val result = PageRangeParser.parseRanges("1-3,3-5", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2), groups[0])
        assertEquals(listOf(2, 3, 4), groups[1])
    }

    @Test
    fun testRejectZeroPage() {
        val result = PageRangeParser.parseRanges("0-5", 10)
        assertTrue(result is PageRangeParseResult.Error)
        val message = (result as PageRangeParseResult.Error).message
        assertTrue(message.contains("1 or greater"))
    }

    @Test
    fun testRejectReversedRange() {
        val result = PageRangeParser.parseRanges("5-2", 10)
        assertTrue(result is PageRangeParseResult.Error)
        val message = (result as PageRangeParseResult.Error).message
        assertTrue(message.contains("cannot be greater than end page"))
    }

    @Test
    fun testExceedingMaxPages() {
        val result = PageRangeParser.parseRanges("1-100", 10)
        assertTrue(result is PageRangeParseResult.Error)
        val message = (result as PageRangeParseResult.Error).message
        assertTrue(message.contains("exceeds total page count of 10"))
    }

    @Test
    fun testInvalidNonNumericInput() {
        val result = PageRangeParser.parseRanges("abc", 10)
        assertTrue(result is PageRangeParseResult.Error)
        val message = (result as PageRangeParseResult.Error).message
        assertTrue(message.contains("not a valid number"))
    }

    @Test
    fun testEmptyInput() {
        val result = PageRangeParser.parseRanges("", 10)
        assertTrue(result is PageRangeParseResult.Error)
        val message = (result as PageRangeParseResult.Error).message
        assertTrue(message.contains("cannot be empty"))
    }

    @Test
    fun testWhitespaceHandling() {
        val result = PageRangeParser.parseRanges(" 1 - 3 , 5 - 7 ", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2), groups[0])
        assertEquals(listOf(4, 5, 6), groups[1])
    }

    @Test
    fun testExtractPagesMode() {
        val result = PageRangeParser.parseExtractPages("1, 3, 5-7", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(1, groups.size)
        assertEquals(listOf(0, 2, 4, 5, 6), groups[0])
    }

    @Test
    fun testDuplicatePagesInExtractMode() {
        val result = PageRangeParser.parseExtractPages("1, 1, 3, 3, 5", 10)
        assertTrue(result is PageRangeParseResult.Success)
        val groups = (result as PageRangeParseResult.Success).pageGroups
        assertEquals(1, groups.size)
        assertEquals(listOf(0, 2, 4), groups[0])
    }
}
