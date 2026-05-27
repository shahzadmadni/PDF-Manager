package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_page_content")
data class PdfPageContent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfId: Int,
    val pageIndex: Int,
    val text: String
)
