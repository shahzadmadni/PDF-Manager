package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PdfDocument
import com.example.data.PdfRepository
import com.example.data.PdfAnnotation
import com.example.data.PdfPageContent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository
    
    // UI filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _starredOnly = MutableStateFlow(false)
    val starredOnly = _starredOnly.asStateFlow()

    // Matching IDs lists for extracted content text search
    private val _contentMatchedPdfIds = MutableStateFlow<Set<Int>>(emptySet())
    val contentMatchedPdfIds = _contentMatchedPdfIds.asStateFlow()

    // Import status alerts
    private val _importStatus = MutableStateFlow<ImportState>(ImportState.Idle)
    val importStatus = _importStatus.asStateFlow()

    // Active reading states
    private val _activeReadingDocument = MutableStateFlow<PdfDocument?>(null)
    val activeReadingDocument = _activeReadingDocument.asStateFlow()

    // Active document editor states
    private val _editingDocument = MutableStateFlow<PdfDocument?>(null)
    val editingDocument = _editingDocument.asStateFlow()

    // Main PDF List Reactively managed
    val pdfListState: StateFlow<List<PdfDocument>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PdfRepository(application, database.pdfDao())

        // Reactively search inside PDF pages in response to query change
        viewModelScope.launch {
            _searchQuery.collectLatest { query ->
                if (query.isNotBlank()) {
                    val matchedList = repository.searchPdfsByContent(query)
                    _contentMatchedPdfIds.value = matchedList.toSet()
                } else {
                    _contentMatchedPdfIds.value = emptySet()
                }
            }
        }

        // Combine DB stream, Search flow, Category flow, Starred flow, and Content Search matches
        pdfListState = combine(
            repository.allPdfs,
            _searchQuery,
            _selectedCategory,
            _starredOnly,
            _contentMatchedPdfIds
        ) { list, query, category, starred, matchedContentIds ->
            var filtered = list

            // 1. Filter by Query (looks inside filename, original name, manual notes, and extracted page contents!)
            if (query.isNotBlank()) {
                filtered = filtered.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.originalName.contains(query, ignoreCase = true) ||
                    it.notes.contains(query, ignoreCase = true) ||
                    matchedContentIds.contains(it.id)
                }
            }

            // 2. Filter by Category
            if (category != "All") {
                filtered = filtered.filter { it.category == category }
            }

            // 3. Filter by Starred
            if (starred) {
                filtered = filtered.filter { it.isStarred }
            }

            filtered
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setStarredOnly(enabled: Boolean) {
        _starredOnly.value = enabled
    }

    fun toggleStarred(pdf: PdfDocument) {
        viewModelScope.launch {
            repository.updatePdf(pdf.copy(isStarred = !pdf.isStarred))
        }
    }

    fun updatePdfCategory(pdf: PdfDocument, category: String) {
        viewModelScope.launch {
            repository.updatePdf(pdf.copy(category = category))
            // Update selected reading or editing doc if needed
            if (_activeReadingDocument.value?.id == pdf.id) {
                _activeReadingDocument.value = _activeReadingDocument.value?.copy(category = category)
            }
            if (_editingDocument.value?.id == pdf.id) {
                _editingDocument.value = _editingDocument.value?.copy(category = category)
            }
        }
    }

    fun updatePdfNotes(pdf: PdfDocument, notes: String) {
        viewModelScope.launch {
            repository.updatePdf(pdf.copy(notes = notes))
            if (_activeReadingDocument.value?.id == pdf.id) {
                _activeReadingDocument.value = _activeReadingDocument.value?.copy(notes = notes)
            }
        }
    }

    fun renamePdf(pdf: PdfDocument, newName: String) {
        viewModelScope.launch {
            repository.updatePdf(pdf.copy(displayName = newName))
            if (_activeReadingDocument.value?.id == pdf.id) {
                _activeReadingDocument.value = _activeReadingDocument.value?.copy(displayName = newName)
            }
        }
    }

    fun updateReadingPage(pdf: PdfDocument, pageIndex: Int) {
        viewModelScope.launch {
            repository.updatePdf(pdf.copy(lastReadPage = pageIndex))
            if (_activeReadingDocument.value?.id == pdf.id) {
                _activeReadingDocument.value = _activeReadingDocument.value?.copy(lastReadPage = pageIndex)
            }
        }
    }

    fun deletePdf(pdf: PdfDocument) {
        viewModelScope.launch {
            if (_activeReadingDocument.value?.id == pdf.id) {
                _activeReadingDocument.value = null
            }
            if (_editingDocument.value?.id == pdf.id) {
                _editingDocument.value = null
            }
            repository.deletePdf(pdf)
        }
    }

    fun importPdf(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _importStatus.value = ImportState.Loading
            val result = repository.importPdfFromUri(uri, fileName)
            if (result.isSuccess) {
                _importStatus.value = ImportState.Success(result.getOrThrow())
            } else {
                _importStatus.value = ImportState.Error(result.exceptionOrNull()?.message ?: "Unknown import error")
            }
        }
    }

    fun resetImportStatus() {
        _importStatus.value = ImportState.Idle
    }

    fun openDocumentForReading(pdf: PdfDocument?) {
        _activeReadingDocument.value = pdf
    }

    fun openDocumentForEditing(pdf: PdfDocument?) {
        _editingDocument.value = pdf
    }

    fun getRepository(): PdfRepository = repository

    // --- Annotation Actions ---
    fun getAnnotationsForPdf(pdfId: Int): Flow<List<PdfAnnotation>> {
        return repository.getAnnotationsForPdf(pdfId)
    }

    fun addAnnotation(annotation: PdfAnnotation) {
        viewModelScope.launch {
            repository.insertAnnotation(annotation)
        }
    }

    fun deleteAnnotation(annotation: PdfAnnotation) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotation)
        }
    }
}

sealed interface ImportState {
    object Idle : ImportState
    object Loading : ImportState
    data class Success(val pdf: PdfDocument) : ImportState
    data class Error(val message: String) : ImportState
}
