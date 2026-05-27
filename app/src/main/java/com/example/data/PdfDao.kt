package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY dateAdded DESC")
    fun getAllPdfs(): Flow<List<PdfDocument>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    fun getPdfById(id: Int): Flow<PdfDocument?>

    @Query("SELECT * FROM pdf_documents WHERE category = :category ORDER BY dateAdded DESC")
    fun getPdfsByCategory(category: String): Flow<List<PdfDocument>>

    @Query("SELECT * FROM pdf_documents WHERE isStarred = 1 ORDER BY dateAdded DESC")
    fun getStarredPdfs(): Flow<List<PdfDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocument): Long

    @Update
    suspend fun updatePdf(pdf: PdfDocument)

    @Delete
    suspend fun deletePdf(pdf: PdfDocument)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePdfById(id: Int)

    // --- Page Text Content Extractor Index Indexing ---
    @Query("SELECT * FROM pdf_page_content WHERE pdfId = :pdfId")
    suspend fun getPageContentsForPdf(pdfId: Int): List<PdfPageContent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageContents(contents: List<PdfPageContent>)

    @Query("SELECT DISTINCT pdfId FROM pdf_page_content WHERE text LIKE '%' || :query || '%'")
    suspend fun searchPdfsByContent(query: String): List<Int>

    // --- Annotation Tools persistence ---
    @Query("SELECT * FROM pdf_annotations WHERE pdfId = :pdfId")
    fun getAnnotationsForPdfFlow(pdfId: Int): Flow<List<PdfAnnotation>>

    @Query("SELECT * FROM pdf_annotations WHERE pdfId = :pdfId")
    suspend fun getAnnotationsForPdf(pdfId: Int): List<PdfAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PdfAnnotation): Long

    @Update
    suspend fun updateAnnotation(annotation: PdfAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: PdfAnnotation)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Int)
}
