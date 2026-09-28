package com.pdftools.app.domain.pdf

data class OrganizePageSpec(
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0
)
