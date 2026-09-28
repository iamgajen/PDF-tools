package com.pdftools.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pdftools.app.ui.components.FloatingGlassBottomNavigation
import com.pdftools.app.ui.compress.CompressPdfScreen
import com.pdftools.app.ui.imagetopdf.ImageToPdfScreen
import com.pdftools.app.ui.merge.MergePdfScreen
import com.pdftools.app.ui.organize.OrganizePdfScreen
import com.pdftools.app.ui.pdftoimage.PdfToImageScreen
import com.pdftools.app.ui.protectunlock.ProtectUnlockPdfScreen
import com.pdftools.app.ui.sign.SignPdfScreen
import com.pdftools.app.ui.split.SplitPdfScreen
import com.pdftools.app.ui.theme.AppAccentColor
import com.pdftools.app.ui.wordtopdf.WordToPdfScreen
import com.pdftools.app.ui.pdftoword.PdfToWordScreen

@Composable
fun MainScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentColor: AppAccentColor,
    onAccentColorChange: (AppAccentColor) -> Unit,
    customColor: Color,
    onCustomColorChange: (Color) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val tabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("History", Icons.Default.History),
        TabItem("Settings", Icons.Default.Settings)
    )

    when (activeTool) {
        "Merge PDF" -> {
            MergePdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Split PDF" -> {
            SplitPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Organize PDF", "Organize Pages" -> {
            OrganizePdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Compress PDF" -> {
            CompressPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Image to PDF" -> {
            ImageToPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "PDF to Image" -> {
            PdfToImageScreen(
                onBack = { activeTool = null }
            )
        }
        "Protect & Unlock PDF", "Protect PDF", "Unlock PDF" -> {
            ProtectUnlockPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Sign PDF" -> {
            SignPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "Word to PDF" -> {
            WordToPdfScreen(
                onBack = { activeTool = null }
            )
        }
        "PDF to Word" -> {
            PdfToWordScreen(
                onBack = { activeTool = null }
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold { innerPadding ->
                    when (selectedTab) {
                        0 -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onToolClick = { toolTitle ->
                                if (toolTitle == "Merge PDF" || toolTitle == "Split PDF" || toolTitle == "Organize PDF" || toolTitle == "Organize Pages" || toolTitle == "Compress PDF" || toolTitle == "Image to PDF" || toolTitle == "PDF to Image" || toolTitle == "Protect & Unlock PDF" || toolTitle == "Protect PDF" || toolTitle == "Unlock PDF" || toolTitle == "Sign PDF" || toolTitle == "Word to PDF" || toolTitle == "PDF to Word") {
                                    activeTool = toolTitle
                                } else {
                                    Toast.makeText(context, "$toolTitle coming soon", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        1 -> PlaceholderScreen("History", modifier = Modifier.padding(innerPadding))
                        2 -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            accentColor = accentColor,
                            onAccentColorChange = onAccentColorChange,
                            customColor = customColor,
                            onCustomColorChange = onCustomColorChange,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                FloatingGlassBottomNavigation(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 112.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$name Screen (Placeholder)")
    }
}

data class TabItem(val title: String, val icon: ImageVector)
