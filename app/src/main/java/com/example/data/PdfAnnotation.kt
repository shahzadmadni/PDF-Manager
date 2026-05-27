package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
data class PdfAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfId: Int,
    val pageIndex: Int,
    val type: String, // "highlight", "sticky", "freehand"
    val content: String, // Contains serialized coordinates, draw strokes or notes content
    val color: Int, // Hex Color Int
    val dateAdded: Long = System.currentTimeMillis()
)
