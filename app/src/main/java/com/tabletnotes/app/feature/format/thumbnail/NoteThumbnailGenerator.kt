package com.tabletnotes.app.feature.format.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.feature.editor.canvas.StrokeRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Not listesi, Sayfa Paneli küçük resimleri ve PNG dışa aktarma için
 * tekil bir sayfanın bitmap görüntüsünü üreten ortak yardımcı sınıf.
 */
object NoteThumbnailGenerator {

    private const val THUMB_WIDTH = 270
    private const val THUMB_HEIGHT = 360

    fun renderThumbnailPngBytes(
        document: NoteDocument
    ): ByteArray {
        val bitmap = renderSinglePageBitmap(
            document = document,
            pageIndex = 0,
            targetWidthPx = THUMB_WIDTH,
            targetHeightPx = THUMB_HEIGHT,
            maxStrokesLimit = 600
        )
        val out = ByteArrayOutputStream(8192)
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    fun renderSinglePageBitmap(
        document: NoteDocument,
        pageIndex: Int,
        targetWidthPx: Int = THUMB_WIDTH,
        targetHeightPx: Int = THUMB_HEIGHT,
        maxStrokesLimit: Int = Int.MAX_VALUE
    ): Bitmap {
        val page = document.pages.firstOrNull { it.pageIndex == pageIndex } ?: NotePage(pageIndex)
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scaleX = targetWidthPx / page.widthPt
        val scaleY = targetHeightPx / page.heightPt
        val scale = minOf(scaleX, scaleY)

        canvas.save()
        canvas.scale(scale, scale)
        drawPageContentOnCanvas(
            canvas = canvas,
            document = document,
            page = page,
            roundedCornerRadius = 18f,
            maxStrokesLimit = maxStrokesLimit
        )
        canvas.restore()
        return bitmap
    }

    /**
     * Bir sayfanın kağıt/PDF arka planını, görsellerini, vektör çizgilerini ve metin kutularını
     * yerel `(0..page.widthPt, 0..page.heightPt)` koordinatlarına çizer.
     * Hem PNG dışa aktarmada hem PDF dışa aktarmada (PdfDocument) ortak kullanılır.
     */
    fun drawPageContentOnCanvas(
        canvas: Canvas,
        document: NoteDocument,
        page: NotePage,
        roundedCornerRadius: Float = 0f,
        maxStrokesLimit: Int = Int.MAX_VALUE
    ) {
        val pageWidth = page.widthPt
        val pageHeight = page.heightPt
        val pageTopY = NotePage.pageTopY(page.pageIndex)
        val pageBottomY = pageTopY + pageHeight

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = document.paperColor.bgArgb }
        val gridPaint = Paint().apply {
            color = document.paperColor.minorPatternArgb
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val majorPaint = Paint().apply {
            color = document.paperColor.majorPatternArgb
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = document.paperColor.majorPatternArgb
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = document.paperColor.majorPatternArgb
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
        }

        val rect = RectF(0f, 0f, pageWidth, pageHeight)
        val clipPath = Path().apply {
            if (roundedCornerRadius > 0f) {
                addRoundRect(rect, roundedCornerRadius, roundedCornerRadius, Path.Direction.CW)
            } else {
                addRect(rect, Path.Direction.CW)
            }
        }

        canvas.save()
        canvas.clipPath(clipPath)

        if (roundedCornerRadius > 0f) {
            canvas.drawRoundRect(rect, roundedCornerRadius, roundedCornerRadius, bgPaint)
        } else {
            canvas.drawRect(rect, bgPaint)
        }

        // 1. PDF veya görsel sayfa arka planı varsa çiz
        val bgPath = page.backgroundImagePath
        val hasPdfBackground = !bgPath.isNullOrBlank() && File(bgPath).exists()
        if (hasPdfBackground) {
            val bgBitmap = BitmapFactory.decodeFile(bgPath)
            if (bgBitmap != null) {
                canvas.drawBitmap(bgBitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                bgBitmap.recycle()
            }
        } else {
            val step = 28f
            when (document.paperTemplate) {
                PaperTemplate.DOTTED -> {
                    var gy = step
                    while (gy < pageHeight - 12f) {
                        var gx = step
                        while (gx < pageWidth - 12f) {
                            canvas.drawCircle(gx, gy, 1.8f, dotPaint)
                            gx += step
                        }
                        gy += step
                    }
                }
                PaperTemplate.GRID -> {
                    var col = 0
                    var gx = step
                    while (gx < pageWidth) {
                        canvas.drawLine(gx, 0f, gx, pageHeight, if (col % 5 == 0) majorPaint else gridPaint)
                        gx += step
                        col++
                    }
                    var row = 0
                    var gy = step
                    while (gy < pageHeight) {
                        canvas.drawLine(0f, gy, pageWidth, gy, if (row % 5 == 0) majorPaint else gridPaint)
                        gy += step
                        row++
                    }
                }
                PaperTemplate.LINED -> {
                    var gy = 64f
                    while (gy < pageHeight - 24f) {
                        canvas.drawLine(32f, gy, pageWidth - 32f, gy, gridPaint)
                        gy += 36f
                    }
                }
                PaperTemplate.BLANK -> Unit
            }
        }

        // 2. Sayfa üzerindeki görsel blokları çiz
        for (img in document.images) {
            if (img.pageIndex == page.pageIndex || (img.y in pageTopY..pageBottomY)) {
                val f = File(img.filePath)
                if (f.exists()) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) {
                        val localTop = img.y - pageTopY
                        val dst = RectF(img.x, localTop, img.x + img.width, localTop + img.height)
                        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                        bmp.recycle()
                    }
                }
            }
        }

        // 3. Sayfadaki vektör çizgileri çiz (-pageTopY kadar yukarı kaydırarak)
        canvas.save()
        canvas.translate(0f, -pageTopY)
        var drawnCount = 0
        for (stroke in document.strokes) {
            if (stroke.pageIndex == page.pageIndex ||
                (stroke.bounds.maxY >= pageTopY && stroke.bounds.minY <= pageBottomY)
            ) {
                StrokeRenderer.drawStroke(canvas, stroke)
                drawnCount++
                if (drawnCount >= maxStrokesLimit) break
            }
        }
        canvas.restore()

        // 4. Sayfadaki metin kutularını çiz
        for (block in document.textBlocks) {
            if (block.pageIndex == page.pageIndex || block.y in pageTopY..pageBottomY) {
                textPaint.color = block.colorArgb
                val lines = block.text.split('\n')
                var ty = (block.y - pageTopY) + 30f
                for (line in lines) {
                    canvas.drawText(line, block.x + 10f, ty, textPaint)
                    ty += max(28f, block.fontSizeSp * 1.6f)
                }
            }
        }

        canvas.restore()

        if (roundedCornerRadius > 0f) {
            canvas.drawRoundRect(rect, roundedCornerRadius, roundedCornerRadius, borderPaint)
        }
    }
}
