package com.tabletnotes.app.feature.format.thumbnail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.feature.editor.canvas.StrokeRenderer
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Not listesi ve sohbet ekleri (M4) için 1. sayfanın küçük önizleme resmini (PNG Thumbnail)
 * üreten yardımcı sınıf.
 * Not: Asıl not her zaman vektör (`.tnote`) olarak saklanır; bu PNG yalnızca liste önizlemesi içindir.
 */
object NoteThumbnailGenerator {

    private const val THUMB_WIDTH = 270
    private const val THUMB_HEIGHT = 360

    fun renderThumbnailPngBytes(
        document: NoteDocument,
        backgroundColorArgb: Int = document.paperColor.bgArgb,
        gridColorArgb: Int = document.paperColor.minorPatternArgb,
        borderColorArgb: Int = document.paperColor.majorPatternArgb
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColorArgb }
        val gridPaint = Paint().apply {
            color = gridColorArgb
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColorArgb
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
        }

        val pageWidth = document.pages.firstOrNull()?.widthPt ?: NotePage.DEFAULT_PAGE_WIDTH
        val pageHeight = document.pages.firstOrNull()?.heightPt ?: NotePage.DEFAULT_PAGE_HEIGHT
        val scaleX = THUMB_WIDTH / pageWidth
        val scaleY = THUMB_HEIGHT / pageHeight
        val scale = minOf(scaleX, scaleY)

        canvas.save()
        canvas.scale(scale, scale)

        val rect = RectF(0f, 0f, pageWidth, pageHeight)
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Kareli defter ızgarası
        val step = 32f
        var x = step
        while (x < pageWidth) {
            canvas.drawLine(x, 0f, x, pageHeight, gridPaint)
            x += step
        }
        var y = step
        while (y < pageHeight) {
            canvas.drawLine(0f, y, pageWidth, y, gridPaint)
            y += step
        }

        // 1. sayfadaki çizgileri çiz (10.000+ çizgili stres testlerinde önizleme üretiminin anında bitmesi için ilk 600 çizgi ile sınırla)
        var drawnCount = 0
        for (stroke in document.strokes) {
            if (stroke.pageIndex == 0 || stroke.bounds.minY <= pageHeight) {
                StrokeRenderer.drawStroke(canvas, stroke)
                drawnCount++
                if (drawnCount >= 600) break
            }
        }

        // 1. sayfadaki metin kutularını çiz
        for (block in document.textBlocks) {
            if (block.pageIndex == 0 || block.y <= pageHeight) {
                textPaint.color = block.colorArgb
                val lines = block.text.split('\n')
                var ty = block.y + 30f
                for (line in lines) {
                    canvas.drawText(line, block.x + 10f, ty, textPaint)
                    ty += max(28f, block.fontSizeSp * 1.6f)
                }
            }
        }

        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)
        canvas.restore()

        val out = ByteArrayOutputStream(8192)
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}

