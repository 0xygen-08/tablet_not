package com.tabletnotes.app.feature.format.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tabletnotes.app.core.storage.AtomicNoteFileStore
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.feature.format.thumbnail.NoteThumbnailGenerator
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PDF içe aktarma (`PdfRenderer`), galeri görseli içe aktarma ve
 * notları yüksek kaliteli PDF (`PdfDocument`) veya PNG olarak dışa aktarma yardımcısı.
 */
object NotePdfPortability {

    private const val PDF_IMPORT_RENDER_W = 1200
    private const val PDF_IMPORT_RENDER_H = 1600
    private const val MAX_IMAGE_IMPORT_DIM = 1600
    private const val PNG_EXPORT_W = 1800
    private const val PNG_EXPORT_H = 2400

    fun resolveDisplayName(context: Context, uri: Uri, fallback: String = "PDF Notu"): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        val clean = name?.substringBeforeLast('.')?.trim()
        return if (!clean.isNullOrBlank()) clean else fallback
    }

    /**
     * Seçilen PDF dosyasının sayfalarını (en fazla [maxPages] sayfa) beyaz zeminli 3:4 oranlı
     * yüksek çözünürlüklü sayfa arka planlarına dönüştürür ve `note_assets` klasörüne kaydeder.
     */
    fun importPdfToPageAssets(
        context: Context,
        pdfUri: Uri,
        noteId: String,
        fileStore: AtomicNoteFileStore,
        startingPageIndex: Int = 0,
        maxPages: Int = 60
    ): List<NotePage> {
        val result = ArrayList<NotePage>()
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return emptyList()
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount.coerceAtMost(maxPages)
                for (i in 0 until count) {
                    renderer.openPage(i).use { pdfPage ->
                        val bitmap = Bitmap.createBitmap(
                            PDF_IMPORT_RENDER_W,
                            PDF_IMPORT_RENDER_H,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(Color.WHITE)

                        val srcW = pdfPage.width.toFloat().coerceAtLeast(1f)
                        val srcH = pdfPage.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(PDF_IMPORT_RENDER_W / srcW, PDF_IMPORT_RENDER_H / srcH)
                        val drawW = srcW * scale
                        val drawH = srcH * scale
                        val tx = (PDF_IMPORT_RENDER_W - drawW) * 0.5f
                        val ty = (PDF_IMPORT_RENDER_H - drawH) * 0.5f

                        val matrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(tx, ty)
                        }

                        pdfPage.render(
                            bitmap,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        val jpegOut = ByteArrayOutputStream(64 * 1024)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpegOut)
                        bitmap.recycle()

                        val assetId = "pdf_${i}_${UUID.randomUUID().toString().take(8)}"
                        val savedFile = fileStore.saveImageAsset(noteId, assetId, jpegOut.toByteArray(), ext = "jpg")
                        result.add(
                            NotePage(
                                pageIndex = startingPageIndex + i,
                                backgroundImagePath = savedFile.absolutePath
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * Galeriden seçilen fotoğrafı/ekran görüntüsünü bellek dostu şekilde küçülterek
     * uygulamanın özel klasörüne kaydeder; `(dosyaYolu, enBoyOranı)` döndürür.
     */
    fun importGalleryImageToAsset(
        context: Context,
        imageUri: Uri,
        noteId: String,
        fileStore: AtomicNoteFileStore
    ): Pair<String, Float>? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        while (srcW / sampleSize > MAX_IMAGE_IMPORT_DIM || srcH / sampleSize > MAX_IMAGE_IMPORT_DIM) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val ratio = (decoded.width.toFloat() / decoded.height.toFloat().coerceAtLeast(1f))
            .coerceIn(0.25f, 4.0f)

        val out = ByteArrayOutputStream(64 * 1024)
        decoded.compress(Bitmap.CompressFormat.JPEG, 90, out)
        decoded.recycle()

        val assetId = "img_${UUID.randomUUID().toString().take(10)}"
        val savedFile = fileStore.saveImageAsset(noteId, assetId, out.toByteArray(), ext = "jpg")
        return savedFile.absolutePath to ratio
    }

    /**
     * Tüm not sayfalarını vektör çizimleri, görselleri ve metin kutularıyla birlikte gerçek PDF'e aktarır.
     */
    fun exportNoteToPdf(document: NoteDocument, outputStream: OutputStream) {
        val pdfDoc = PdfDocument()
        try {
            val pages = document.pages.ifEmpty { listOf(NotePage(0)) }
            for (page in pages) {
                val info = PdfDocument.PageInfo.Builder(
                    page.widthPt.roundToInt().coerceAtLeast(100),
                    page.heightPt.roundToInt().coerceAtLeast(100),
                    page.pageIndex + 1
                ).create()
                val pdfPage = pdfDoc.startPage(info)
                NoteThumbnailGenerator.drawPageContentOnCanvas(
                    canvas = pdfPage.canvas,
                    document = document,
                    page = page,
                    roundedCornerRadius = 0f
                )
                pdfDoc.finishPage(pdfPage)
            }
            pdfDoc.writeTo(outputStream)
        } finally {
            pdfDoc.close()
        }
    }

    /**
     * Seçili sayfayı yüksek çözünürlüklü (`1800x2400`) PNG dosyası olarak dışa aktarır.
     */
    fun exportPageToPng(document: NoteDocument, pageIndex: Int, outputStream: OutputStream) {
        val bmp = NoteThumbnailGenerator.renderSinglePageBitmap(
            document = document,
            pageIndex = pageIndex,
            targetWidthPx = PNG_EXPORT_W,
            targetHeightPx = PNG_EXPORT_H
        )
        try {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } finally {
            bmp.recycle()
        }
    }
}
