package com.pdftools.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

@Composable
fun PDFToolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = AppBlue,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accentColor,
            onPrimary = if (isColorDark(accentColor)) Color.White else Color.Black,
            primaryContainer = accentColor.copy(alpha = 0.2f),
            onPrimaryContainer = accentColor,
            secondary = accentColor.copy(alpha = 0.7f),
            onSecondary = Color.White,
            surfaceVariant = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0xFFC7C7CC)
        )
        else -> lightColorScheme(
            primary = accentColor,
            onPrimary = if (isColorDark(accentColor)) Color.White else Color.Black,
            primaryContainer = accentColor.copy(alpha = 0.1f),
            onPrimaryContainer = accentColor,
            secondary = accentColor.copy(alpha = 0.7f),
            onSecondary = Color.White,
            surfaceVariant = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFF3A3A3C)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun isColorDark(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance < 0.5
}
