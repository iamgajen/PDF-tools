package com.pdftools.app.ui.pdftoimage

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.PdfToImageFormat
import com.pdftools.app.domain.pdf.PdfToImagePageSelectionMode
import com.pdftools.app.domain.pdf.PdfToImageQuality
import com.pdftools.app.domain.pdf.PdfToImageResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfToImageViewModel = viewModel(factory = PdfToImageViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val pdfFile by viewModel.selectedPdfFile.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val pageSelectionMode by viewModel.selectedPageSelectionMode.collectAsState()
    val rangeInput by viewModel.rangeInput.collectAsState()
    val selectedPagesSet by viewModel.selectedPagesSet.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val thumbnails by viewModel.pageThumbnails.collectAsState()

    val selectPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setPdfUri(context, uri)
        }
    }

    // Auto-launch picker on first launch if no PDF selected
    var hasLaunchedPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasLaunchedPicker && pdfFile == null) {
            hasLaunchedPicker = true
            selectPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    if (uiState is PdfToImageUiState.Success) {
        val successState = uiState as PdfToImageUiState.Success
        PdfToImageResultScreen(
            result = successState.result,
            onDone = {
                viewModel.reset()
                onBack()
            },
            modifier = modifier
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF to Image", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pdfFile == null) {
                // Empty State / Select File Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Extract Images from PDF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a PDF document to convert its pages into high-quality JPG or PNG images.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { selectPdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select PDF File", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val currentPdf = pdfFile!!
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // PDF File Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentPdf.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currentPdf.formattedSize} • ${currentPdf.pageCount ?: 0} ${if ((currentPdf.pageCount ?: 0) == 1) "page" else "pages"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                            ) {
                                Text("Change")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Export Options Section
                    Text(
                        text = "EXPORT OPTIONS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Format Options
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Format",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfToImageFormat.entries.forEach { format ->
                                    FilterChip(
                                        selected = selectedFormat == format,
                                        onClick = { viewModel.setFormat(format) },
                                        label = {
                                            Text(
                                                text = format.label,
                                                fontWeight = if (selectedFormat == format) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Quality Options (Only shown for JPG)
                            if (selectedFormat == PdfToImageFormat.JPG) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "JPG Quality",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PdfToImageQuality.entries.forEach { quality ->
                                        FilterChip(
                                            selected = selectedQuality == quality,
                                            onClick = { viewModel.setQuality(quality) },
                                            label = {
                                                Text(
                                                    text = quality.label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selectedQuality == quality) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Resolution Options
                            Text(
                                text = "Resolution / DPI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val resChunks = PdfToImageResolution.entries.chunked(2)
                                resChunks.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { res ->
                                            FilterChip(
                                                selected = selectedResolution == res,
                                                onClick = { viewModel.setResolution(res) },
                                                label = {
                                                    Text(
                                                        text = res.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selectedResolution == res) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Page Selection Mode
                            Text(
                                text = "Page Selection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfToImagePageSelectionMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = pageSelectionMode == mode,
                                        onClick = { viewModel.setPageSelectionMode(mode) },
                                        label = {
                                            Text(
                                                text = mode.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (pageSelectionMode == mode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (pageSelectionMode == PdfToImagePageSelectionMode.PAGE_RANGE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = rangeInput,
                                    onValueChange = { viewModel.updateRangeInput(it) },
                                    label = { Text("Page Range (e.g., 1-5, 8, 11-13)") },
                                    isError = validationError != null,
                                    supportingText = {
                                        if (validationError != null) {
                                            Text(validationError!!, color = MaterialTheme.colorScheme.error)
                                        } else {
                                            Text("Enter page ranges separated by commas")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Page Grid Header & Action Bar
                    val pageCount = currentPdf.pageCount ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PAGES (${viewModel.getPagesToRender().size} of $pageCount selected)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            TextButton(onClick = { viewModel.selectAllPages() }) {
                                Text("Select All", fontSize = 12.sp)
                            }
                            TextButton(onClick = { viewModel.clearPageSelection() }) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Thumbnail Grid
                    val pagesToRenderList = viewModel.getPagesToRender().toSet()
                    val gridHeight = calculateGridHeight(pageCount)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                        userScrollEnabled = false
                    ) {
                        items(pageCount) { pageIdx ->
                            val isSelected = when (pageSelectionMode) {
                                PdfToImagePageSelectionMode.ALL_PAGES -> true
                                PdfToImagePageSelectionMode.SELECTED_PAGES -> selectedPagesSet.contains(pageIdx)
                                PdfToImagePageSelectionMode.PAGE_RANGE -> pagesToRenderList.contains(pageIdx)
                            }
                            PageThumbnailCard(
                                pageIndex = pageIdx,
                                isSelected = isSelected,
                                thumbnail = thumbnails[pageIdx],
                                onClick = { viewModel.togglePageSelection(pageIdx) }
                            )
                        }
                    }

                    // Extra bottom padding so floating convert button doesn't cover contents or bottom navigation
                    Spacer(modifier = Modifier.height(140.dp))
                }

                // Floating Bottom Action Bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding()
                    ) {
                        val pagesToConvert = viewModel.getPagesToRender().size
                        Button(
                            onClick = { viewModel.convertPdfToImages() },
                            enabled = pagesToConvert > 0 && uiState !is PdfToImageUiState.Rendering,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pagesToConvert > 0) "Convert $pagesToConvert ${if (pagesToConvert == 1) "Page" else "Pages"} to Image" else "Select Pages to Convert",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // Rendering Progress Overlay Dialog
            if (uiState is PdfToImageUiState.Rendering) {
                val renderingState = uiState as PdfToImageUiState.Rendering
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Converting PDF to images",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Page ${renderingState.current} of ${renderingState.total}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { renderingState.current.toFloat() / renderingState.total.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.cancelConversion() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Error Message Dialog
            if (uiState is PdfToImageUiState.Error || userMessage != null) {
                val errorMessage = (uiState as? PdfToImageUiState.Error)?.message ?: userMessage ?: "An error occurred"
                AlertDialog(
                    onDismissRequest = {
                        viewModel.clearUserMessage()
                        viewModel.clearErrorAndKeepState()
                    },
                    title = { Text("Error") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearUserMessage()
                                viewModel.clearErrorAndKeepState()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    pageIndex: Int,
    isSelected: Boolean,
    thumbnail: Bitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // Selection Check Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp),
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Page Number Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun calculateGridHeight(itemCount: Int): Dp {
    val rows = (itemCount + 2) / 3
    val cardHeightDp = 140
    return (rows * cardHeightDp).dp
}
