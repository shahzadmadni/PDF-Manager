package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localPath: String,              // Relative path/filename inside filesDir/pdfs/
    val originalName: String,           // Name of the original imported file
    val displayName: String,            // User customizable name or original name
    val sizeInBytes: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val category: String = "Uncategorized", // Receipt, Work, Study, Personal, etc.
    val isStarred: Boolean = false,
    val notes: String = "",
    val lastReadPage: Int = 0,
    val pageCount: Int = 0
)
