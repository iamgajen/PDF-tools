package com.pdftools.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdftools.app.ui.theme.AppAccentColor

@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentColor: AppAccentColor,
    onAccentColorChange: (AppAccentColor) -> Unit,
    customColor: Color,
    onCustomColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            SettingsRow(
                title = "Theme",
                subtitle = themeMode,
                onClick = { showThemeDialog = true }
            )
        }

        item {
            SettingsRow(
                title = "Icon Color",
                subtitle = accentColor.label,
                onClick = { showColorDialog = true }
            )
        }

        if (accentColor == AppAccentColor.CUSTOM) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomColorPicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Custom Color", style = MaterialTheme.typography.bodyLarge)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(customColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        item {
            Text(
                text = "Behavior",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            SettingsSwitchRow(title = "Haptic Feedback", initialValue = true)
        }
        item {
            SettingsSwitchRow(title = "Auto-delete temporary", initialValue = true)
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item { SettingsRow(title = "Privacy", onClick = {}) }
        item { SettingsRow(title = "Rate PDF Tools", onClick = {}) }
        item { SettingsRow(title = "About", onClick = {}) }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    listOf("System default", "Light", "Dark").forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Choose Icon Color") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(AppAccentColor.values().size) { index ->
                        val color = AppAccentColor.values()[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAccentColorChange(color)
                                    showColorDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = accentColor == color, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (color == AppAccentColor.CUSTOM) Color.Gray else color.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = color.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomColorPicker) {
        AlertDialog(
            onDismissRequest = { showCustomColorPicker = false },
            title = { Text("Custom Color Picker") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select a shade (UI Placeholder)")
                    Spacer(modifier = Modifier.height(16.dp))
                    val colors = listOf(
                        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
                        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        colors.chunked(3).forEach { chunk ->
                            Column {
                                chunk.forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .clickable {
                                                onCustomColorChange(c)
                                                showCustomColorPicker = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomColorPicker = false }) { Text("Done") }
            }
        )
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsSwitchRow(title: String, initialValue: Boolean) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}
