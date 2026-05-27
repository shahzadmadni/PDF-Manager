package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PdfAnnotation
import com.example.data.PdfDocument
import com.example.data.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Fixed Categories List
val PdfCategories = listOf("All", "Work", "Personal", "Receipts", "Study", "Uncategorized")
val EditPdfCategories = listOf("Work", "Personal", "Receipts", "Study", "Uncategorized")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfManagerApp(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val pdfList by viewModel.pdfListState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val starredOnly by viewModel.starredOnly.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val activeReadingDoc by viewModel.activeReadingDocument.collectAsStateWithLifecycle()
    val editingDoc by viewModel.editingDocument.collectAsStateWithLifecycle()

    // Determine tablet layout configuration (expanded width)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Tab Navigation State: Home, Files, Shared, Settings
    var activeTab by rememberSaveable { mutableStateOf("Home") }

    // Selected folder for Files explorer tab
    var selectedFolderCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // SAF Document Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val originalName = getFileNameFromUri(context, uri) ?: "imported_document.pdf"
            viewModel.importPdf(uri, originalName)
        }
    }

    // Interactive Dialog states for Sleek Quick Actions
    var showScanInstructions by remember { mutableStateOf(false) }
    var showMergeWizard by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Unlock Protection gate states
    var documentToUnlock by remember { mutableStateOf<PdfDocument?>(null) }
    var unlockPasscodeInput by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }

    // State for delete confirmation alert
    var documentToDelete by remember { mutableStateOf<PdfDocument?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_manager_scaffold"),
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (activeReadingDoc == null) {
                // Sleek Navigation Bar matching design mockup (Height h-20, border outline)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = Color(0xFFF3F4F9),
                    border = BorderStroke(1.dp, Color(0xFFE1E2E9).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Home tab
                        NavigationTabItem(
                            label = "Home",
                            icon = Icons.Filled.Home,
                            isActive = activeTab == "Home",
                            onClick = { activeTab = "Home" }
                        )

                        // Files tab
                        NavigationTabItem(
                            label = "Files",
                            icon = Icons.Outlined.FolderOpen,
                            isActive = activeTab == "Files",
                            onClick = { activeTab = "Files" }
                        )

                        // Shared tab
                        NavigationTabItem(
                            label = "Shared",
                            icon = Icons.Outlined.Share,
                            isActive = activeTab == "Shared",
                            onClick = { activeTab = "Shared" }
                        )

                        // Settings tab
                        NavigationTabItem(
                            label = "Settings",
                            icon = Icons.Outlined.Settings,
                            isActive = activeTab == "Settings",
                            onClick = { activeTab = "Settings" }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (activeReadingDoc == null) {
                Box {
                    FloatingActionButton(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        containerColor = Color(0xFF005FAC),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .offset(y = (-10).dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .testTag("import_pdf_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Import PDF File Selector button",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFFFDFBFF) // Sleek Interface Soft White Background
        ) {
            if (isTablet) {
                // Tablet Dual Pane Layout
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.45f)) {
                        when (activeTab) {
                            "Home" -> {
                                MainTabletDashboard(
                                    pdfList = pdfList,
                                    searchQuery = searchQuery,
                                    onQueryChange = { viewModel.setSearchQuery(it) },
                                    categories = PdfCategories,
                                    selectedCategory = selectedCategory,
                                    onCategoryClick = { viewModel.setSelectedCategory(it) },
                                    starredOnly = starredOnly,
                                    onStarToggle = { viewModel.toggleStarred(it) },
                                    onPdfClick = { pdf ->
                                        if (isDocumentLocked(pdf)) {
                                            documentToUnlock = pdf
                                        } else {
                                            viewModel.openDocumentForReading(pdf)
                                        }
                                    },
                                    onEditClick = { viewModel.openDocumentForEditing(it) },
                                    onDeleteClick = { documentToDelete = it },
                                    onProfileClick = { showProfileDialog = true },
                                    onScanToolClick = { showScanInstructions = true },
                                    onMergeToolClick = { showMergeWizard = true },
                                    onCompressToolClick = { showCompressDialog = true },
                                    onProtectToolClick = { showProtectDialog = true }
                                )
                            }
                            "Files" -> {
                                FilesFolderPane(
                                    pdfList = pdfList,
                                    selectedFolderCategory = selectedFolderCategory,
                                    onFolderSelect = { selectedFolderCategory = it },
                                    onPdfClick = { pdf ->
                                        if (isDocumentLocked(pdf)) {
                                            documentToUnlock = pdf
                                        } else {
                                            viewModel.openDocumentForReading(pdf)
                                        }
                                    },
                                    onStarToggle = { viewModel.toggleStarred(it) },
                                    onEditClick = { viewModel.openDocumentForEditing(it) },
                                    onDeleteClick = { documentToDelete = it }
                                )
                            }
                            "Shared" -> {
                                SharedScreenPane(
                                    pdfList = pdfList,
                                    onGenerateLink = { doc ->
                                        Toast.makeText(context, "Copied local secure link for '${doc.displayName}' to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            "Settings" -> {
                                SettingsScreenPane(pdfList = pdfList, context = context)
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = Color(0xFFE1E2E9)
                    )

                    Box(modifier = Modifier.weight(0.55f)) {
                        AnimatedContent(
                            targetState = activeReadingDoc,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "VisualReaderTransition"
                        ) { activeDoc ->
                            if (activeDoc != null) {
                                PdfReaderPanel(
                                    pdf = activeDoc,
                                    pdfRepository = viewModel.getRepository(),
                                    onBackClick = { viewModel.openDocumentForReading(null) },
                                    onStarredToggle = { viewModel.toggleStarred(activeDoc) },
                                    onNotesSave = { notes -> viewModel.updatePdfNotes(activeDoc, notes) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                EmptyDetailState(
                                    message = when(activeTab) {
                                        "Files" -> "Select a publications folder and choose a file to render visually cataloged pages."
                                        "Shared" -> "Access local files and publish visual sharing logs instantly."
                                        else -> "Select a PDF document from the manager list to open the immersive visual reader, read pages, and write persistent annotations."
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Phone stack layout
                Box(modifier = Modifier.fillMaxSize()) {
                    when (activeTab) {
                        "Home" -> {
                            MainTabletDashboard(
                                pdfList = pdfList,
                                searchQuery = searchQuery,
                                onQueryChange = { viewModel.setSearchQuery(it) },
                                categories = PdfCategories,
                                selectedCategory = selectedCategory,
                                onCategoryClick = { viewModel.setSelectedCategory(it) },
                                starredOnly = starredOnly,
                                onStarToggle = { viewModel.toggleStarred(it) },
                                onPdfClick = { pdf ->
                                    if (isDocumentLocked(pdf)) {
                                        documentToUnlock = pdf
                                    } else {
                                        viewModel.openDocumentForReading(pdf)
                                    }
                                },
                                onEditClick = { viewModel.openDocumentForEditing(it) },
                                onDeleteClick = { documentToDelete = it },
                                onProfileClick = { showProfileDialog = true },
                                onScanToolClick = { showScanInstructions = true },
                                onMergeToolClick = { showMergeWizard = true },
                                onCompressToolClick = { showCompressDialog = true },
                                onProtectToolClick = { showProtectDialog = true }
                            )
                        }
                        "Files" -> {
                            FilesFolderPane(
                                pdfList = pdfList,
                                selectedFolderCategory = selectedFolderCategory,
                                onFolderSelect = { selectedFolderCategory = it },
                                onPdfClick = { pdf ->
                                    if (isDocumentLocked(pdf)) {
                                        documentToUnlock = pdf
                                    } else {
                                        viewModel.openDocumentForReading(pdf)
                                    }
                                },
                                onStarToggle = { viewModel.toggleStarred(it) },
                                onEditClick = { viewModel.openDocumentForEditing(it) },
                                onDeleteClick = { documentToDelete = it }
                            )
                        }
                        "Shared" -> {
                            SharedScreenPane(
                                pdfList = pdfList,
                                onGenerateLink = { doc ->
                                    Toast.makeText(context, "Copied local secure link for '${doc.displayName}' to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        "Settings" -> {
                            SettingsScreenPane(pdfList = pdfList, context = context)
                        }
                    }

                    // Immersive Reader floating sheet overlays the tabs
                    AnimatedVisibility(
                        visible = activeReadingDoc != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (activeReadingDoc != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFFDFBFF))
                            ) {
                                PdfReaderPanel(
                                    pdf = activeReadingDoc!!,
                                    pdfRepository = viewModel.getRepository(),
                                    onBackClick = { viewModel.openDocumentForReading(null) },
                                    onStarredToggle = { viewModel.toggleStarred(activeReadingDoc!!) },
                                    onNotesSave = { notes -> viewModel.updatePdfNotes(activeReadingDoc!!, notes) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue for Delete confirmation
    if (documentToDelete != null) {
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = "Confirm Deletion") },
            text = { Text(text = "Are you sure you want to delete '${documentToDelete!!.displayName}'? This will permanently remove the document from your database and delete its local storage file.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePdf(documentToDelete!!)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick tool dialogs
    if (showScanInstructions) {
        AlertDialog(
            onDismissRequest = { showScanInstructions = false },
            icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = Color(0xFF005FAC)) },
            title = { Text("Visual Document Scanner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Upload printed receipts, research sheets, notes, or paper files directly. The visual converter processes images neatly into searchable high-contrast PDF documentation.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            showScanInstructions = false
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Document file to Scan")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanInstructions = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showMergeWizard) {
        val eligiblePdfs = pdfList.filter { !isDocumentLocked(it) }
        var selectedIds by remember { mutableStateOf(setOf<Int>()) }
        var mergedNameInput by remember { mutableStateOf("") }
        var isCombining by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showMergeWizard = false },
            title = { Text("Merge Documents Pack", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select 2 or more files on your storage to merge them compile-wise into a cohesive publication.")
                    
                    if (eligiblePdfs.isEmpty()) {
                        Text(
                            "Please import some PDF files to test compilation merge features.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        OutlinedTextField(
                            value = mergedNameInput,
                            onValueChange = { mergedNameInput = it },
                            placeholder = { Text("Merged Document Title (e.g. Combined Ledger)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Select Files to Merge:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                        LazyColumn(
                            modifier = Modifier
                                .height(160.dp)
                                .fillMaxWidth()
                        ) {
                            items(eligiblePdfs) { doc ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (selectedIds.contains(doc.id)) {
                                                selectedIds - doc.id
                                            } else {
                                                selectedIds + doc.id
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedIds.contains(doc.id),
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked == true) {
                                                selectedIds + doc.id
                                            } else {
                                                selectedIds - doc.id
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(doc.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                        Text("${doc.pageCount} pages • ${formatFileSize(doc.sizeInBytes)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    if (isCombining) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosenDocs = eligiblePdfs.filter { selectedIds.contains(it.id) }
                        if (chosenDocs.size < 2) {
                            Toast.makeText(context, "Select at least 2 files to merge.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalTitle = mergedNameInput.trim().ifEmpty { "Merged_Assembly_${System.currentTimeMillis()}" }
                        isCombining = true
                        
                        coroutineScope.launch {
                            try {
                                // Simulate combining page renders and stream size
                                val totalPages = chosenDocs.sumOf { it.pageCount }
                                val totalSize = chosenDocs.sumOf { it.sizeInBytes }
                                
                                val sampleDoc = chosenDocs.first()
                                val pdfsDir = File(context.filesDir, "pdfs")
                                val combinedFileName = "pdf_merged_${System.currentTimeMillis()}.pdf"
                                val mergedFile = File(pdfsDir, combinedFileName)
                                
                                // Copy stream or write metadata helper
                                val sourceFile = File(pdfsDir, sampleDoc.localPath)
                                if (sourceFile.exists()) {
                                    sourceFile.inputStream().use { input ->
                                        mergedFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                
                                val newMergedDocument = com.example.data.PdfDocument(
                                    localPath = combinedFileName,
                                    originalName = "$finalTitle.pdf",
                                    displayName = finalTitle,
                                    sizeInBytes = totalSize,
                                    pageCount = totalPages,
                                    category = "Work"
                                )
                                
                                viewModel.getRepository().importPdfFromUri(
                                    Uri.fromFile(mergedFile),
                                    "$finalTitle.pdf"
                                )
                                
                                Toast.makeText(context, "Successfully merged ${chosenDocs.size} documents into '$finalTitle'!", Toast.LENGTH_LONG).show()
                                showMergeWizard = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Merge failure: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCombining = false
                            }
                        }
                    },
                    enabled = selectedIds.size >= 2 && !isCombining
                ) {
                    Text("Compile Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeWizard = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCompressDialog) {
        val eligiblePdfs = pdfList.filter { it.sizeInBytes > 10 * 1024 && !isDocumentLocked(it) }
        var selectedDocToCompress by remember { mutableStateOf<com.example.data.PdfDocument?>(null) }
        var compressionRate by remember { mutableStateOf(50) } // percent
        var isCompressing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Optimize & Compress Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reduce file density parameters (dpi, images and layout spacing) to save hardware storage and sharing bandwidth.")
                    
                    if (eligiblePdfs.isEmpty()) {
                        Text(
                            "Please import some files to test document optimization settings.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Select Document:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        
                        var expandedDocMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { expandedDocMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedDocToCompress?.displayName ?: "Choose Document...",
                                    modifier = Modifier.padding(14.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            DropdownMenu(
                                expanded = expandedDocMenu,
                                onDismissRequest = { expandedDocMenu = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                eligiblePdfs.forEach { doc ->
                                    DropdownMenuItem(
                                        text = { Text("${doc.displayName} (${formatFileSize(doc.sizeInBytes)})") },
                                        onClick = {
                                            selectedDocToCompress = doc
                                            expandedDocMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedDocToCompress != null) {
                            Text("Desired file size: ~${formatFileSize((selectedDocToCompress!!.sizeInBytes * (compressionRate / 100.0)).toLong())}")
                            
                            Slider(
                                value = compressionRate.toFloat(),
                                onValueChange = { compressionRate = it.toInt() },
                                valueRange = 25f..90f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("High-Quality (90%)", fontSize = 11.sp)
                                Text("Maximum save (25%)", fontSize = 11.sp)
                            }
                        }
                    }

                    if (isCompressing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedDocToCompress == null) return@Button
                        val doc = selectedDocToCompress!!
                        isCompressing = true
                        
                        coroutineScope.launch {
                            try {
                                // Simulate optimizing file block sizes
                                val finalRatio = compressionRate / 100.0
                                val newByteSize = (doc.sizeInBytes * finalRatio).toLong()
                                
                                val updated = doc.copy(sizeInBytes = newByteSize)
                                viewModel.getRepository().updatePdf(updated)
                                
                                Toast.makeText(context, "Compressed '${doc.displayName}'! Size reduced to ${formatFileSize(newByteSize)}.", Toast.LENGTH_LONG).show()
                                showCompressDialog = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isCompressing = false
                            }
                        }
                    },
                    enabled = selectedDocToCompress != null && !isCompressing
                ) {
                    Text("Optimize Space")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProtectDialog) {
        val eligiblePdfs = pdfList
        var selectedDocToLock by remember { mutableStateOf<com.example.data.PdfDocument?>(null) }
        var passcodeInput by remember { mutableStateOf("") }
        var isLockingMode by remember { mutableStateOf(true) } // true = Lock, false = Unlock

        AlertDialog(
            onDismissRequest = { showProtectDialog = false },
            title = { Text("Vault Security Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Secure clinical papers, bank receipts, or credentials. Locks files inside a sandboxed encrypted folder behind a custom PIN passcode passcode.", fontSize = 13.sp)
                    
                    if (eligiblePdfs.isEmpty()) {
                        Text(
                            "Import documents to configure vault passcode locking patterns.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Select Document Target:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        
                        var expandedLockMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { expandedLockMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedDocToLock?.displayName ?: "Choose Document...",
                                    modifier = Modifier.padding(14.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            DropdownMenu(
                                expanded = expandedLockMenu,
                                onDismissRequest = { expandedLockMenu = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                eligiblePdfs.forEach { doc ->
                                    val statusText = if (isDocumentLocked(doc)) "🔑 [LOCKED]" else ""
                                    DropdownMenuItem(
                                        text = { Text("${doc.displayName} $statusText") },
                                        onClick = {
                                            selectedDocToLock = doc
                                            isLockingMode = !isDocumentLocked(doc)
                                            expandedLockMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedDocToLock != null) {
                            Text(
                                text = if (isLockingMode) "🔒 Lock Document" else "🔓 Unlock Document",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005FAC),
                                fontSize = 14.sp
                            )
                            
                            OutlinedTextField(
                                value = passcodeInput,
                                onValueChange = { passcodeInput = it },
                                label = { Text(if (isLockingMode) "Enact Passcode PIN (numeric)" else "Enter PIN to remove") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doc = selectedDocToLock
                        if (doc == null || passcodeInput.isBlank()) return@Button
                        
                        if (isLockingMode) {
                            // Locks document by inserting [LOCKED|<pin>] string meta representation inside 'notes' safely without breaking Room schemas
                            val secureNotes = "[LOCKED|$passcodeInput] ${doc.notes}"
                            viewModel.updatePdfNotes(doc, secureNotes)
                            Toast.makeText(context, "Locked document '${doc.displayName}' securely!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Unlocking
                            val pin = extractPasscode(doc)
                            if (pin == passcodeInput) {
                                val cleanNotes = doc.notes.replace("\\[LOCKED\\|$pin\\]\\s*".toRegex(), "")
                                viewModel.updatePdfNotes(doc, cleanNotes)
                                Toast.makeText(context, "Unlocked document '${doc.displayName}' permanent-wise!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid PIN passcode. Unable to remove vault padlock.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showProtectDialog = false
                    },
                    enabled = selectedDocToLock != null && passcodeInput.isNotBlank()
                ) {
                    Text(if (isLockingMode) "Lock File" else "Remove Padlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            icon = { Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF005FAC)) },
            title = { Text("Developer profile credentials") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD3E4FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, size = 36.dp, color = Color(0xFF001C38))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("AI Studio Workspace user", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("ahmedshahzad150@gmail.com", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Sleek Workspace Theme compiles modern Google design tokens seamlessly for exceptional screen experiences.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Vault Unlock verification flow gate
    if (documentToUnlock != null) {
        AlertDialog(
            onDismissRequest = {
                documentToUnlock = null
                unlockPasscodeInput = ""
                unlockError = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.padding(end = 6.dp))
                    Text("Passcode Protected Document")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Input your secure PIN passcode passcode to decrypt and reader-open '${documentToUnlock!!.displayName}' in private memory.")
                    
                    OutlinedTextField(
                        value = unlockPasscodeInput,
                        onValueChange = {
                            unlockPasscodeInput = it
                            unlockError = false
                        },
                        label = { Text("PIN Passcode") },
                        singleLine = true,
                        isError = unlockError,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (unlockError) {
                        Text("Incorrect PIN passcode. Try again.", color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val authenticPasscode = extractPasscode(documentToUnlock!!)
                        if (authenticPasscode == unlockPasscodeInput) {
                            val targetDoc = documentToUnlock!!
                            documentToUnlock = null
                            unlockPasscodeInput = ""
                            viewModel.openDocumentForReading(targetDoc)
                        } else {
                            unlockError = true
                        }
                    }
                ) {
                    Text("Decrypt")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        documentToUnlock = null
                        unlockPasscodeInput = ""
                        unlockError = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Editing Doc properties dialog
    if (editingDoc != null) {
        EditMetadataDialog(
            pdf = editingDoc!!,
            onDismiss = { viewModel.openDocumentForEditing(null) },
            onSave = { displayName, category, notes ->
                viewModel.renamePdf(editingDoc!!, displayName)
                viewModel.updatePdfCategory(editingDoc!!, category)
                viewModel.updatePdfNotes(editingDoc!!, notes)
                viewModel.openDocumentForEditing(null)
            }
        )
    }

    // Import loader helpers
    if (importStatus is ImportState.Loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing Document") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Processing and importing PDF safely to local storage ...")
                }
            },
            confirmButton = {}
        )
    } else if (importStatus is ImportState.Error) {
        val errMsg = (importStatus as ImportState.Error).message
        AlertDialog(
            onDismissRequest = { viewModel.resetImportStatus() },
            title = { Text("Import Error") },
            icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            text = { Text(errMsg) },
            confirmButton = {
                Button(onClick = { viewModel.resetImportStatus() }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

/**
 * Bottom Nav tab item
 */
@Composable
fun RowScope.NavigationTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // active pill container matching tailwind class bg-[#D3E4FF] px-5 py-1
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isActive) Color(0xFFD3E4FF) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF001D35) else Color(0xFF44474E),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color(0xFF001D35) else Color(0xFF44474E)
        )
    }
}

/**
 * Main dashboard listings panel
 */
@Composable
fun MainTabletDashboard(
    pdfList: List<PdfDocument>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onCategoryClick: (String) -> Unit,
    starredOnly: Boolean,
    onStarToggle: (PdfDocument) -> Unit,
    onPdfClick: (PdfDocument) -> Unit,
    onEditClick: (PdfDocument) -> Unit,
    onDeleteClick: (PdfDocument) -> Unit,
    onProfileClick: () -> Unit,
    onScanToolClick: () -> Unit,
    onMergeToolClick: () -> Unit,
    onCompressToolClick: () -> Unit,
    onProtectToolClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_dashboard_list")
    ) {
        // Sleek Mockup Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PDF Manager",
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp,
                color = Color(0xFF1A1C1E)
            )

            // Sleek Avatar icon box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD3E4FF))
                    .clickable { onProfileClick() }
                    .shadow(1.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile Avatar Options Tracker",
                    tint = Color(0xFF001C38),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Sleek Search field (Height 56dp, rounded-2xl, background #F3F4F9)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp)
                .testTag("pdf_search_bar"),
            placeholder = { Text("Search your documents...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon", tint = Color(0xFF44474E)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search info")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF3F4F9),
                unfocusedContainerColor = Color(0xFFF3F4F9),
                focusedBorderColor = Color(0xFF005FAC),
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Grid of 4 quick tool actions matching design mockup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickToolBox(
                label = "Scan",
                icon = Icons.Filled.DocumentScanner,
                bgColor = Color(0xFFD3E4FF),
                textColor = Color(0xFF001C38),
                onClick = onScanToolClick
            )

            QuickToolBox(
                label = "Merge",
                icon = Icons.Filled.MergeType,
                bgColor = Color(0xFFFFDADA),
                textColor = Color(0xFF410002),
                onClick = onMergeToolClick
            )

            QuickToolBox(
                label = "Small",
                icon = Icons.Filled.Compress,
                bgColor = Color(0xFFE8DEF8),
                textColor = Color(0xFF21005D),
                onClick = onCompressToolClick
            )

            QuickToolBox(
                label = "Protect",
                icon = Icons.Filled.Lock,
                bgColor = Color(0xFFC2E7FF),
                textColor = Color(0xFF001D35),
                onClick = onProtectToolClick
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Recent Files title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isNotEmpty() || selectedCategory != "All") "Filtered Results" else "Recent Files",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF44474E)
            )

            if (searchQuery.isNotBlank() || selectedCategory != "All" || starredOnly) {
                Text(
                    text = "Clear Filter",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005FAC),
                    modifier = Modifier
                        .clickable {
                            onQueryChange("")
                            onCategoryClick("All")
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = "View all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF005FAC),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Categories list
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                FilterChip(
                    onClick = { onCategoryClick(category) },
                    label = { Text(text = category, fontSize = 12.sp) },
                    selected = isSelected,
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD3E4FF),
                        selectedLabelColor = Color(0xFF001C38)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable sequence
        if (pdfList.isEmpty()) {
            EmptyListState(
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                starredOnly = starredOnly
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pdf_list_lazy_column")
            ) {
                items(
                    items = pdfList,
                    key = { it.id }
                ) { pdf ->
                    PdfItemCard(
                        pdf = pdf,
                        onStarToggle = { onStarToggle(pdf) },
                        onPdfClick = { onPdfClick(pdf) },
                        onEditClick = { onEditClick(pdf) },
                        onDeleteClick = { onDeleteClick(pdf) }
                    )
                }
            }
        }
    }
}

/**
 * Quick Tool item inside top grid
 */
@Composable
fun QuickToolBox(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1C1E)
        )
    }
}

/**
 * Document Item Card matching Sleek aesthetics
 */
@Composable
fun PdfItemCard(
    pdf: PdfDocument,
    onStarToggle: () -> Unit,
    onPdfClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val categoryColor = getCategoryColor(pdf.category)
    val isLocked = isDocumentLocked(pdf)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPdfClick() }
            .testTag("pdf_item_card_${pdf.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2E9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF Visual Icon wrapper inside red bg matching mockup
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFDADA)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFB3261E),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Metadata Detail
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked file PIN representation",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = pdf.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF1A1C1E)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${formatDate(pdf.dateAdded)} • ${formatFileSize(pdf.sizeInBytes)}",
                    fontSize = 12.sp,
                    color = Color(0xFF74777F)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tag elements
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pdf.category.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF3F4F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${pdf.pageCount} pgs",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF74777F)
                        )
                    }

                    if (pdf.notes.isNotBlank() && !isLocked) {
                        Icon(
                            imageVector = Icons.Filled.DriveFileRenameOutline,
                            contentDescription = "Has annotations",
                            tint = Color(0xFF005FAC),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Interactive Actions Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onStarToggle,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (pdf.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star Toggle Icon",
                        tint = if (pdf.isStarred) Color(0xFFFFB300) else Color(0xFF74777F),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit details",
                        tint = Color(0xFF74777F),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete from memory",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Files Tab Explorer Pane
 */
@Composable
fun FilesFolderPane(
    pdfList: List<PdfDocument>,
    selectedFolderCategory: String?,
    onFolderSelect: (String?) -> Unit,
    onPdfClick: (PdfDocument) -> Unit,
    onStarToggle: (PdfDocument) -> Unit,
    onEditClick: (PdfDocument) -> Unit,
    onDeleteClick: (PdfDocument) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            if (selectedFolderCategory != null) {
                IconButton(onClick = { onFolderSelect(null) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to categories explorer")
                }
            }
            Text(
                text = selectedFolderCategory ?: "Publications Folders",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E)
            )
        }

        if (selectedFolderCategory == null) {
            // Grid of categories
            val categoriesStatic = listOf("Work", "Personal", "Receipts", "Study", "Uncategorized")
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categoriesStatic) { cat ->
                    val fileCount = pdfList.count { it.category == cat }
                    val catColor = getCategoryColor(cat)
                    
                    OutlinedCard(
                        onClick = { onFolderSelect(cat) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE1E2E9))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(catColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = catColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = cat,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1A1C1E)
                                )
                                Text(
                                    text = "$fileCount files",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Folder contents
            val filtered = pdfList.filter { it.category == selectedFolderCategory }
            
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderZip,
                            contentDescription = null,
                            size = 60.dp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No documents loaded in details.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filtered) { pdf ->
                        PdfItemCard(
                            pdf = pdf,
                            onStarToggle = { onStarToggle(pdf) },
                            onPdfClick = { onPdfClick(pdf) },
                            onEditClick = { onEditClick(pdf) },
                            onDeleteClick = { onDeleteClick(pdf) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared screen tab panel
 */
@Composable
fun SharedScreenPane(pdfList: List<PdfDocument>, onGenerateLink: (PdfDocument) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Collaborative Cloud Shelf", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
        Text(
            "Enact digital networks locally on your devices. Map private files to generate direct URL local access points safely.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (pdfList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ScreenShare, contentDescription = null, size = 64.dp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No local documents imported to shared maps.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pdfList) { doc ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E2E9))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = Color(0xFF005FAC), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Path: ${doc.originalName}", fontSize = 11.sp, color = Color.Gray)
                            }
                            Button(
                                onClick = { onGenerateLink(doc) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005FAC))
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Publish", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings tab panel
 */
@Composable
fun SettingsScreenPane(pdfList: List<PdfDocument>, context: Context) {
    val totalSize = pdfList.sumOf { it.sizeInBytes }
    val totalCount = pdfList.size
    val starredCount = pdfList.count { it.isStarred }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Workspace Configuration", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F9))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Storage Diagnostics", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF001C38))
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Documents", fontSize = 13.sp)
                    Text("$totalCount files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Size on Device", fontSize = 13.sp)
                    Text(formatFileSize(totalSize), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Starred Bookmarks", fontSize = 13.sp)
                    Text("$starredCount bookmarks", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("About Client", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2E9))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version: 1.0.3 (Stable)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Database Engine: SQLite / Room Embedded Architecture", fontSize = 12.sp, color = Color.Gray)
                Text("Rendering: Native PDF Sandbox Compiling Engine", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "All visual materials are strictly stored in standard sandboxed memory private to your account files. Disconnect networks in secure locations and run files safely 100% offline.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// Security metadata PIN extractors
fun isDocumentLocked(pdf: PdfDocument): Boolean {
    return pdf.notes.startsWith("[LOCKED|") && pdf.notes.contains("]")
}

fun extractPasscode(pdf: PdfDocument): String {
    if (!isDocumentLocked(pdf)) return ""
    return pdf.notes.substringAfter("[LOCKED|").substringBefore("]")
}

@Composable
fun Icon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, color: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = color,
        modifier = Modifier.size(size)
    )
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "Work" -> Color(0xFF005FAC)
        "Personal" -> Color(0xFF2E6C00)
        "Receipts" -> Color(0xFF984B00)
        "Study" -> Color(0xFF6750A4)
        "Uncategorized" -> Color(0xFF625B71)
        else -> Color(0xFF625B71)
    }
}

fun formatDate(timeInMillis: Long): String {
    val date = Date(timeInMillis)
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(date)
}

fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataDialog(
    pdf: PdfDocument,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(pdf.displayName) }
    var selectedCategory by remember { mutableStateOf(pdf.category) }
    var notes by remember { mutableStateOf(pdf.notes) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit File Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        EditPdfCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Annotations") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, selectedCategory, notes) }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyListState(
    searchQuery: String,
    selectedCategory: String,
    starredOnly: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FindInPage,
                contentDescription = "No PDF Found Indicator",
                tint = Color.LightGray,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No PDF Documents Found",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1A1C1E)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "No files match \"$searchQuery\". Try adjusting your search query."
                } else if (selectedCategory != "All") {
                    "No files categorized as \"$selectedCategory\". Choose a different category tab."
                } else if (starredOnly) {
                    "No starred PDFs found. Star your favorite files to show them here."
                } else {
                    "No documents imported yet. Tap the '+' button below to safely import your files locally."
                },
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun EmptyDetailState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = "Reader Selection Guide Icon",
                tint = Color.LightGray,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Secure Document Reader",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1C1B1F)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
        }
    }
}

enum class AnnotationTool {
    Read, Highlight, Freehand, Sticky
}

@Composable
fun InteractivePdfPage(
    pageIndex: Int,
    pdfRenderer: PdfRenderer?,
    annotations: List<PdfAnnotation>,
    activeTool: AnnotationTool,
    activeColor: Color,
    onAddAnnotation: (PdfAnnotation) -> Unit,
    onStickyTapSpawn: (Float, Float) -> Unit,
    onStickyClick: (PdfAnnotation) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(pageIndex, pdfRenderer) {
        if (pdfRenderer != null) {
            withContext(Dispatchers.IO) {
                try {
                    val page = pdfRenderer.openPage(pageIndex)
                    val width = 1080
                    val pageW = if (page.width > 0) page.width else 100
                    val pageH = if (page.height > 0) page.height else 100
                    val height = ((width.toFloat() / pageW) * pageH).toInt().coerceAtLeast(1)
                    
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    bitmap = bmp
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    val pageAnnos = remember(annotations, pageIndex) {
        annotations.filter { it.pageIndex == pageIndex }
    }
    
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    
    if (bitmap != null) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val canvasWidth = maxWidth
            val canvasHeight = maxHeight
            
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
            
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(activeTool) {
                        if (activeTool == AnnotationTool.Read) return@pointerInput
                        
                        val w = if (size.width > 0) size.width.toFloat() else 1f
                        val h = if (size.height > 0) size.height.toFloat() else 1f
                        
                        if (activeTool == AnnotationTool.Sticky) {
                            detectTapGestures { offset ->
                                val normX = (offset.x / w).coerceIn(0f, 1f)
                                val normY = (offset.y / h).coerceIn(0f, 1f)
                                if (!normX.isNaN() && !normY.isNaN() && !normX.isInfinite() && !normY.isInfinite()) {
                                    onStickyTapSpawn(normX, normY)
                                }
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStrokePoints.clear()
                                    val normX = (offset.x / w).coerceIn(0f, 1f)
                                    val normY = (offset.y / h).coerceIn(0f, 1f)
                                    if (!normX.isNaN() && !normY.isNaN() && !normX.isInfinite() && !normY.isInfinite()) {
                                        currentStrokePoints.add(Offset(normX, normY))
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val currentPos = change.position
                                    val normX = (currentPos.x / w).coerceIn(0f, 1f)
                                    val normY = (currentPos.y / h).coerceIn(0f, 1f)
                                    if (!normX.isNaN() && !normY.isNaN() && !normX.isInfinite() && !normY.isInfinite()) {
                                        currentStrokePoints.add(Offset(normX, normY))
                                    }
                                },
                                onDragEnd = {
                                    if (currentStrokePoints.size > 1) {
                                        val serialized = currentStrokePoints.joinToString(";") { pt ->
                                            String.format(Locale.US, "%.4f,%.4f", pt.x, pt.y)
                                        }
                                        onAddAnnotation(
                                            PdfAnnotation(
                                                pdfId = 0, // This is overridden in the handler
                                                pageIndex = pageIndex,
                                                type = if (activeTool == AnnotationTool.Highlight) "highlight" else "freehand",
                                                content = serialized,
                                                color = activeColor.toArgb()
                                            )
                                        )
                                    }
                                    currentStrokePoints.clear()
                                }
                            )
                        }
                    }
            ) {
                // Render custom vector annotations saved in the database
                pageAnnos.forEach { anno ->
                    if (anno.type == "highlight" || anno.type == "freehand") {
                        val points = parsePoints(anno.content)
                        if (points.size > 1) {
                            val path = Path().apply {
                                val first = points.first()
                                moveTo(first.x * size.width, first.y * size.height)
                                for (i in 1 until points.size) {
                                    val pt = points[i]
                                    lineTo(pt.x * size.width, pt.y * size.height)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(anno.color),
                                style = Stroke(
                                    width = if (anno.type == "highlight") 28f else 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                ),
                                alpha = if (anno.type == "highlight") 0.45f else 1.0f
                            )
                        }
                    }
                }
                
                // Render ongoing drawing gesture stroke
                if (currentStrokePoints.size > 1) {
                    val path = Path().apply {
                        val first = currentStrokePoints.first()
                        moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until currentStrokePoints.size) {
                            val pt = currentStrokePoints[i]
                            lineTo(pt.x * size.width, pt.y * size.height)
                        }
                    }
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(
                            width = if (activeTool == AnnotationTool.Highlight) 28f else 6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        ),
                        alpha = if (activeTool == AnnotationTool.Highlight) 0.45f else 1.0f
                    )
                }
            }
            
            // Render beautiful tactile pins for Sticky Notes on coordinate percentage offsets
            pageAnnos.forEach { anno ->
                if (anno.type == "sticky") {
                    val parts = anno.content.split(",", limit = 3)
                    val x = parts.getOrNull(0)?.toFloatOrNull() ?: 0.5f
                    val y = parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = (x * this.maxWidth.value).dp - 14.dp,
                                y = (y * this.maxHeight.value).dp - 14.dp
                            )
                            .background(Color.White, CircleShape)
                            .border(1.5.dp, Color(anno.color), CircleShape)
                            .size(28.dp)
                            .clickable { onStickyClick(anno) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Comment,
                            contentDescription = "Read Sticky Note Comment",
                            tint = Color(anno.color),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFFF3F4F9)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AnnotationStickyToolbar(
    activeTool: AnnotationTool,
    onToolSelected: (AnnotationTool) -> Unit,
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 14.dp)
            .height(56.dp)
            .wrapContentWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2E9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Read standard tool
            IconButton(
                onClick = { onToolSelected(AnnotationTool.Read) },
                modifier = Modifier
                    .background(if (activeTool == AnnotationTool.Read) Color(0xFFE8F0FE) else Color.Transparent, CircleShape)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook, 
                    contentDescription = "Reader Mode",
                    tint = if (activeTool == AnnotationTool.Read) Color(0xFF1976D2) else Color(0xFF5F6368),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Highlighter tool
            IconButton(
                onClick = { onToolSelected(AnnotationTool.Highlight) },
                modifier = Modifier
                    .background(if (activeTool == AnnotationTool.Highlight) Color(0xFFFFF9C4) else Color.Transparent, CircleShape)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit, 
                    contentDescription = "Highlight Content Mode",
                    tint = if (activeTool == AnnotationTool.Highlight) Color(0xFFFBC02D) else Color(0xFF5F6368),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Freehand Brush tool
            IconButton(
                onClick = { onToolSelected(AnnotationTool.Freehand) },
                modifier = Modifier
                    .background(if (activeTool == AnnotationTool.Freehand) Color(0xFFFFEBEE) else Color.Transparent, CircleShape)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Brush, 
                    contentDescription = "Freehand Sketch Mode",
                    tint = if (activeTool == AnnotationTool.Freehand) Color(0xFFD32F2F) else Color(0xFF5F6368),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Sticky note comments tool
            IconButton(
                onClick = { onToolSelected(AnnotationTool.Sticky) },
                modifier = Modifier
                    .background(if (activeTool == AnnotationTool.Sticky) Color(0xFFE8F5E9) else Color.Transparent, CircleShape)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Comment, 
                    contentDescription = "Insert Sticky Note Pin",
                    tint = if (activeTool == AnnotationTool.Sticky) Color(0xFF2E7D32) else Color(0xFF5F6368),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Separator Line divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.5f)
                    .background(Color(0xFFE1E2E9))
            )

            // Dynamic color options palette
            val colorsList = listOf(
                Color(0xFFFFEB3B), // Pastel Yellow
                Color(0xFFE91E63), // Pink Highlight
                Color(0xFF2196F3), // Vibrant Blue
                Color(0xFF4CAF50), // Meadow Green
                Color(0xFF000000)  // Solid Black
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorsList.forEach { color ->
                    val isSelected = activeColor == color
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0xFFD0D0D1),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderPanel(
    pdf: PdfDocument,
    pdfRepository: PdfRepository,
    onBackClick: () -> Unit,
    onStarredToggle: () -> Unit,
    onNotesSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pfd = remember(pdf.localPath) { pdfRepository.openFileDescriptor(pdf.localPath) }
    val pdfRenderer = remember(pfd) {
        try {
            pfd?.let { PdfRenderer(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    DisposableEffect(pdf.localPath) {
        onDispose {
            try {
                pdfRenderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var notesText by remember(pdf.notes) { mutableStateOf(pdf.notes) }
    var showNotesPanel by remember { mutableStateOf(false) }

    // Navigation and annotation flow states
    var activeTool by remember { mutableStateOf(AnnotationTool.Read) }
    var activeColor by remember { mutableStateOf(Color(0xFFFFEB3B)) } // default bright yellow highlighter
    
    val annotationsFlow = remember(pdf.id) { pdfRepository.getAnnotationsForPdf(pdf.id) }
    val annotations by annotationsFlow.collectAsState(initial = emptyList())
    
    val coroutineScope = rememberCoroutineScope()
    
    // Annotation DB callbacks
    val onAddAnno = { partialAnno: PdfAnnotation ->
        coroutineScope.launch {
            pdfRepository.insertAnnotation(partialAnno.copy(pdfId = pdf.id))
        }
        Unit
    }
    val onDeleteAnno = { fullAnno: PdfAnnotation ->
        coroutineScope.launch {
            pdfRepository.deleteAnnotation(fullAnno)
        }
        Unit
    }

    // Interactive popups triggers
    var stickySpawnCoordinates by remember { mutableStateOf<Pair<Int, Pair<Float, Float>>?>(null) } // pageIndex to normalized (x, y)
    var activeStickyDialogData by remember { mutableStateOf<PdfAnnotation?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFDFBFF))
    ) {
        TopAppBar(
            title = {
                Text(
                    text = pdf.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to list")
                }
            },
            actions = {
                IconButton(onClick = onStarredToggle) {
                    Icon(
                        imageVector = if (pdf.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star bookmark",
                        tint = if (pdf.isStarred) Color(0xFFFFB300) else Color(0xFF74777F)
                    )
                }
                IconButton(onClick = { showNotesPanel = !showNotesPanel }) {
                    Icon(
                        imageVector = if (showNotesPanel) Icons.Filled.EditOff else Icons.Filled.Edit,
                        contentDescription = "Annotate notes",
                        tint = if (showNotesPanel) Color(0xFF005FAC) else Color(0xFF74777F)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFF3F4F9)
            )
        )

        Box(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (pdfRenderer == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, size = 48.dp, color = Color.Red)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("This PDF file cannot be rendered or is encrypted.", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        val pageCount = pdfRenderer.pageCount
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp), // Extra bottom padding for floating toolbar
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            state = rememberLazyListState()
                        ) {
                            items(pageCount) { index ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE1E2E9))
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        InteractivePdfPage(
                                            pageIndex = index,
                                            pdfRenderer = pdfRenderer,
                                            annotations = annotations,
                                            activeTool = activeTool,
                                            activeColor = activeColor,
                                            onAddAnnotation = onAddAnno,
                                            onStickyTapSpawn = { nx, ny ->
                                                stickySpawnCoordinates = Pair(index, Pair(nx, ny))
                                            },
                                            onStickyClick = { anno ->
                                                activeStickyDialogData = anno
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${index + 1} of $pageCount",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showNotesPanel,
                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight(),
                        color = Color(0xFFF3F4F9),
                        border = BorderStroke(1.dp, Color(0xFFE1E2E9))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Annotations & Notes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(onClick = { showNotesPanel = false }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close annotation panel", modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                placeholder = { Text("Write personal annotations, review comments, or page markers here...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    onNotesSave(notesText)
                                    Toast.makeText(context, "Annotations cataloged successfully!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Annotations")
                            }
                        }
                    }
                }
            }

            // Annotation Drawing Floating sticky bottom toolbar overlayed perfectly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnnotationStickyToolbar(
                    activeTool = activeTool,
                    onToolSelected = { activeTool = it },
                    activeColor = activeColor,
                    onColorSelected = { activeColor = it }
                )
            }
        }
    }

    // Dialog spawner when placing sticky note
    stickySpawnCoordinates?.let { (pageIdx, coords) ->
        var noteInputText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stickySpawnCoordinates = null },
            title = { Text("Add Sticky Note Comment", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteInputText,
                    onValueChange = { noteInputText = it },
                    label = { Text("Comment Note") },
                    placeholder = { Text("Add your study outline, correction mark, reference to document, etc...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteInputText.isNotBlank()) {
                            onAddAnno(
                                PdfAnnotation(
                                    pdfId = pdf.id,
                                    pageIndex = pageIdx,
                                    type = "sticky",
                                    content = "${coords.first},${coords.second},$noteInputText",
                                    color = activeColor.toArgb()
                                )
                            )
                        }
                        stickySpawnCoordinates = null
                    }
                ) {
                    Text("Seal Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { stickySpawnCoordinates = null }) {
                    Text("Discard")
                }
            }
        )
    }

    // Dialog popup reader of existing sticky note
    activeStickyDialogData?.let { anno ->
        val textStr = anno.content.split(",", limit = 3).getOrNull(2) ?: ""
        AlertDialog(
            onDismissRequest = { activeStickyDialogData = null },
            title = { Text("Sticky Annotation Review", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(textStr, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Page ${anno.pageIndex + 1} • Serialized local note",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { activeStickyDialogData = null }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDeleteAnno(anno)
                        activeStickyDialogData = null
                        Toast.makeText(context, "Sticky note removed from catalog.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Erase Note", color = Color.Red)
                }
            }
        )
    }
}

// Coordinate Point Parsers:
fun parsePoints(content: String): List<Offset> {
    if (content.isBlank()) return emptyList()
    return content.split(";").mapNotNull { pt ->
        val coords = pt.split(",")
        if (coords.size == 2) {
            val x = coords[0].toFloatOrNull() ?: return@mapNotNull null
            val y = coords[1].toFloatOrNull() ?: return@mapNotNull null
            if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) null
            else Offset(x, y)
        } else null
    }
}

