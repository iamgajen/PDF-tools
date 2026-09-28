package com.pdftools.app.ui.split

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftools.app.domain.pdf.PdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplitPdfViewModel = viewModel(factory = SplitPdfViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val pdfFile by viewModel.selectedPdfFile.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val rangeInput by viewModel.rangeInput.collectAsState()
    val extractPagesInput by viewModel.extractPagesInput.collectAsState()
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

    if (uiState is SplitUiState.Success) {
        val successState = uiState as SplitUiState.Success
        SplitPdfResultScreen(
            outputFiles = successState.outputFiles,
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
                title = { Text("Split PDF", fontWeight = FontWeight.Bold) },
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
            val currentPdf = pdfFile
            if (currentPdf == null) {
                EmptySplitPdfSelectionContent(
                    onSelectFile = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    SelectedPdfHeaderCard(
                        pdfFile = currentPdf,
                        onChangeFile = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedMode == SplitModeType.EVERY_PAGE,
                            onClick = { viewModel.setSelectedMode(SplitModeType.EVERY_PAGE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Every Page", fontSize = 12.sp, maxLines = 1)
                        }
                        SegmentedButton(
                            selected = selectedMode == SplitModeType.BY_RANGES,
                            onClick = { viewModel.setSelectedMode(SplitModeType.BY_RANGES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("By Ranges", fontSize = 12.sp, maxLines = 1)
                        }
                        SegmentedButton(
                            selected = selectedMode == SplitModeType.EXTRACT_PAGES,
                            onClick = { viewModel.setSelectedMode(SplitModeType.EXTRACT_PAGES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Extract Pages", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedMode) {
                            SplitModeType.EVERY_PAGE -> EveryPageModeContent(
                                pageCount = currentPdf.pageCount ?: 0,
                                thumbnails = thumbnails
                            )
                            SplitModeType.BY_RANGES -> ByRangesModeContent(
                                rangeInput = rangeInput,
                                totalPages = currentPdf.pageCount ?: 0,
                                validationError = validationError,
                                thumbnails = thumbnails,
                                onRangeInputChange = { viewModel.updateRangeInput(it) }
                            )
                            SplitModeType.EXTRACT_PAGES -> ExtractPagesModeContent(
                                extractInput = extractPagesInput,
                                totalPages = currentPdf.pageCount ?: 0,
                                selectedPagesSet = selectedPagesSet,
                                validationError = validationError,
                                thumbnails = thumbnails,
                                onInputChange = { viewModel.updateExtractPagesInput(it) },
                                onTogglePage = { viewModel.togglePageSelection(it) }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            if (validationError != null) {
                                Text(
                                    text = validationError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = { viewModel.splitPdf() },
                                enabled = validationError == null && uiState !is SplitUiState.Processing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Split PDF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Processing Overlay
            if (uiState is SplitUiState.Processing) {
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Splitting PDF...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please wait, processing locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error Dialog
            if (uiState is SplitUiState.Error) {
                val errorMsg = (uiState as SplitUiState.Error).message
                AlertDialog(
                    onDismissRequest = { viewModel.clearErrorAndKeepFile() },
                    title = { Text("Split Failed", fontWeight = FontWeight.Bold) },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearErrorAndKeepFile() }) {
                            Text("OK")
                        }
                    }
                )
            }

            // User Message Alert Dialog
            if (userMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearUserMessage() },
                    title = { Text("Notice") },
                    text = { Text(userMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearUserMessage() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptySplitPdfSelectionContent(
    onSelectFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No PDF file selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a PDF file to split into individual pages, specific page ranges, or extracted pages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSelectFile,
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
}

@Composable
private fun SelectedPdfHeaderCard(
    pdfFile: PdfFile,
    onChangeFile: () -> Unit
) {
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
                    text = pdfFile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = pdfFile.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pdfFile.pageCount != null) {
                        Text(
                            text = " • ${pdfFile.pageCount} ${if (pdfFile.pageCount == 1) "page" else "pages"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onChangeFile,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Change", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EveryPageModeContent(
    pageCount: Int,
    thumbnails: Map<Int, Bitmap>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Each page will be saved as an individual PDF file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Will generate $pageCount PDF files (page 1 to page $pageCount).",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        PageThumbnailsGrid(
            pageCount = pageCount,
            thumbnails = thumbnails,
            selectedPages = (0 until pageCount).toSet(),
            isSelectable = false,
            onTogglePage = {}
        )
    }
}

@Composable
private fun ByRangesModeContent(
    rangeInput: String,
    totalPages: Int,
    validationError: String?,
    thumbnails: Map<Int, Bitmap>,
    onRangeInputChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Split PDF into multiple files by specifying page ranges.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = rangeInput,
            onValueChange = onRangeInputChange,
            label = { Text("Page Ranges") },
            placeholder = { Text("e.g. 1-5, 6-10, 11-$totalPages") },
            isError = validationError != null,
            supportingText = {
                if (validationError != null) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Separate ranges with commas (e.g. 1-3, 4-8)")
                }
            },
            trailingIcon = {
                if (rangeInput.isNotEmpty()) {
                    IconButton(onClick = { onRangeInputChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        PageThumbnailsGrid(
            pageCount = totalPages,
            thumbnails = thumbnails,
            selectedPages = emptySet(),
            isSelectable = false,
            onTogglePage = {}
        )
    }
}

@Composable
private fun ExtractPagesModeContent(
    extractInput: String,
    totalPages: Int,
    selectedPagesSet: Set<Int>,
    validationError: String?,
    thumbnails: Map<Int, Bitmap>,
    onInputChange: (String) -> Unit,
    onTogglePage: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select pages to extract into a single PDF document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = extractInput,
            onValueChange = onInputChange,
            label = { Text("Selected Pages") },
            placeholder = { Text("e.g. 1, 3, 5-8") },
            isError = validationError != null,
            supportingText = {
                if (validationError != null) {
                    Text(validationError, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Tap page thumbnails below or type page numbers")
                }
            },
            trailingIcon = {
                if (extractInput.isNotEmpty()) {
                    IconButton(onClick = { onInputChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        PageThumbnailsGrid(
            pageCount = totalPages,
            thumbnails = thumbnails,
            selectedPages = selectedPagesSet,
            isSelectable = true,
            onTogglePage = onTogglePage
        )
    }
}

@Composable
private fun PageThumbnailsGrid(
    pageCount: Int,
    thumbnails: Map<Int, Bitmap>,
    selectedPages: Set<Int>,
    isSelectable: Boolean,
    onTogglePage: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(pageCount) { index ->
            val isSelected = selectedPages.contains(index)
            val bitmap = thumbnails[index]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .then(
                        if (isSelectable) {
                            Modifier.clickable { onTogglePage(index) }
                        } else Modifier
                    )
                    .then(
                        if (isSelectable && isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.medium
                            )
                        } else Modifier
                    ),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Page ${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Page number badge bottom
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topEnd = 6.dp),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Selection Checkmark top-right
                    if (isSelectable && isSelected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
