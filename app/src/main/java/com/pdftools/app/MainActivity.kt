package com.pdftools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.pdftools.app.ui.screens.MainScreen
import com.pdftools.app.ui.theme.AppAccentColor
import com.pdftools.app.ui.theme.AppBlue
import com.pdftools.app.ui.theme.PDFToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf("System default") }
            var accentColor by remember { mutableStateOf(AppAccentColor.PURPLE) }
            var customColor by remember { mutableStateOf(Color(0xFF0061A4)) }

            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            val currentAccentColor = if (accentColor == AppAccentColor.CUSTOM) customColor else accentColor.color

            PDFToolsTheme(
                darkTheme = darkTheme,
                accentColor = currentAccentColor
            ) {
                MainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    accentColor = accentColor,
                    onAccentColorChange = { accentColor = it },
                    customColor = customColor,
                    onCustomColorChange = { customColor = it }
                )
            }
        }
    }
}
