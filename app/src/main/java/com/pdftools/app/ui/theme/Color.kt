package com.pdftools.app.ui.theme

import androidx.compose.ui.graphics.Color

val Blue80 = Color(0xFFD0E4FF)
val BlueGrey80 = Color(0xFFC2D1DC)
val Sky80 = Color(0xFFB8EBEF)

val Blue40 = Color(0xFF005FB0)
val BlueGrey40 = Color(0xFF55606B)
val Sky40 = Color(0xFF006874)

// Premium Preset Colors
val AppBlue = Color(0xFF0061A4)
val AppPurple = Color(0xFF6750A4)
val AppGreen = Color(0xFF006D39)
val AppOrange = Color(0xFF8B5000)
val AppRed = Color(0xFFBA1A1A)
val AppCyan = Color(0xFF006A6A)
val AppPink = Color(0xFF984061)
val AppMonochrome = Color(0xFF606060)

enum class AppAccentColor(val color: Color, val label: String) {
    BLUE(AppBlue, "Blue"),
    PURPLE(AppPurple, "Purple"),
    GREEN(AppGreen, "Green"),
    ORANGE(AppOrange, "Orange"),
    RED(AppRed, "Red"),
    CYAN(AppCyan, "Cyan"),
    PINK(AppPink, "Pink"),
    MONOCHROME(AppMonochrome, "Monochrome"),
    CUSTOM(Color.Gray, "Custom Color")
}
