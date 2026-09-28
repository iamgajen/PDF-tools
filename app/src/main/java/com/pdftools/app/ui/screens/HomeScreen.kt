package com.pdftools.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.pdftools.app.ui.components.QuickActionCard
import com.pdftools.app.ui.components.RecentFileItem
import com.pdftools.app.ui.components.ToolCard
import com.pdftools.app.ui.theme.CaacupeOne
import com.pdftools.app.ui.theme.PDFToolsTheme

data class ToolInfo(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onToolClick: (String) -> Unit = {}
) {
    val quickActions = listOf(
        ToolInfo("Merge PDF", "Combine PDFs", Icons.AutoMirrored.Filled.MergeType),
        ToolInfo("Split PDF", "Extract pages", Icons.AutoMirrored.Filled.CallSplit),
        ToolInfo("Compress PDF", "Reduce size", Icons.Default.Compress),
        ToolInfo("Organize PDF", "Rearrange", Icons.AutoMirrored.Filled.Sort)
    )

    val allTools = listOf(
        ToolInfo("Merge PDF", "Combine multiple files", Icons.AutoMirrored.Filled.MergeType),
        ToolInfo("Split PDF", "Split into multiple files", Icons.AutoMirrored.Filled.CallSplit),
        ToolInfo("Compress PDF", "Reduce file size", Icons.Default.Compress),
        ToolInfo("Organize Pages", "Rearrange or delete pages", Icons.AutoMirrored.Filled.Sort),
        ToolInfo("Image to PDF", "Convert images to PDF", Icons.Default.AddPhotoAlternate),
        ToolInfo("PDF to Image", "Extract images from PDF", Icons.Default.Collections),
        ToolInfo("Protect & Unlock PDF", "Password protect or unlock PDF", Icons.Default.EnhancedEncryption),
        ToolInfo("Word to PDF", "Convert Word documents to PDF", Icons.Default.Description),
        ToolInfo("PDF to Word", "Convert PDF files to Word", Icons.AutoMirrored.Filled.Article),
        ToolInfo("Edit PDF", "Edit text, images and pages", Icons.Default.Edit),
        ToolInfo("Sign PDF", "Add your signature", Icons.Default.Draw)
    )

    val recentFiles = listOf(
        Pair("invoice.pdf", "2.4 MB"),
        Pair("document.pdf", "1.1 MB"),
        Pair("report.pdf", "4.8 MB")
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 100.dp
        )
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PDF Tools",
                    fontFamily = CaacupeOne,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "All PDF Tools. One App.",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Files stay on your device",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onToolClick("Merge PDF") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            SectionHeader(title = "Quick Actions")
            
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val actionChunks = quickActions.chunked(2)
                actionChunks.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { action ->
                            QuickActionCard(
                                title = action.title,
                                icon = action.icon,
                                onClick = { onToolClick(action.title) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "All Tools")
        }

        val chunks = allTools.chunked(2)
        items(chunks) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { tool ->
                    ToolCard(
                        title = tool.title,
                        description = tool.description,
                        icon = tool.icon,
                        onClick = { onToolClick(tool.title) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            SectionHeader(title = "Recent Files")
        }

        items(recentFiles) { file ->
            RecentFileItem(
                fileName = file.first,
                fileSize = file.second,
                onClick = { }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    PDFToolsTheme {
        HomeScreen()
    }
}
