package com.tabletnotes.app.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min

/**
 * Editörde kullanılan çizim ve düzenleme araçları.
 */
enum class DrawingToolType(val wireId: Int) {
    PEN(0),
    HIGHLIGHTER(1),
    ERASER(2),
    TEXT(3);

    companion object {
        fun fromWireId(id: Int): DrawingToolType =
            entries.firstOrNull { it.wireId == id } ?: PEN
    }
}

/**
 * Tek bir kalem/dokunuş örnekleme noktası (sample point).
 * Koordinatlar sayfa/belge uzayındadır (float); basınç (0..1), eğim (radyan) ve zaman damgası içerir.
 */
@Immutable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tiltRadians: Float = 0f,
    val orientationRadians: Float = 0f,
    val timestampMillis: Long = 0L
)

/**
 * Çizgilerin ve görünüm alanının (Viewport / Tile) hızlı kesişim testi için
 * eksen hizalı sınırlayıcı kutu (Axis-Aligned Bounding Box - AABB).
 */
@Immutable
data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = max(0f, maxX - minX)
    val height: Float get() = max(0f, maxY - minY)

    fun intersects(other: BoundingBox): Boolean {
        return minX <= other.maxX &&
            maxX >= other.minX &&
            minY <= other.maxY &&
            maxY >= other.minY
    }

    fun contains(x: Float, y: Float): Boolean {
        return x in minX..maxX && y in minY..maxY
    }

    fun inflated(padding: Float): BoundingBox = BoundingBox(
        minX = minX - padding,
        minY = minY - padding,
        maxX = maxX + padding,
        maxY = maxY + padding
    )

    companion object {
        fun fromPoints(points: List<StrokePoint>, padding: Float): BoundingBox {
            if (points.isEmpty()) return BoundingBox(0f, 0f, 0f, 0f)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (i in points.indices) {
                val p = points[i]
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            return BoundingBox(
                minX = minX - padding,
                minY = minY - padding,
                maxX = maxX + padding,
                maxY = maxY + padding
            )
        }
    }
}

/**
 * Tamamlanmış (committed) bir vektör çizgi (Stroke).
 * Her çizgi; sayfa numarasını, aracını (Kalem / Fosforlu), rengini (ARGB),
 * taban kalınlığını ve tüm nokta dizisini saklar.
 */
@Immutable
data class NoteStroke(
    val id: String,
    val pageIndex: Int = 0,
    val tool: DrawingToolType,
    val colorArgb: Int,
    val thickness: Float,
    val points: List<StrokePoint>,
    val bounds: BoundingBox = BoundingBox.fromPoints(points, padding = thickness * 1.5f)
)

/**
 * Tuval üzerine yerleştirilen düzenlenebilir metin kutusu (Text Block).
 */
@Immutable
data class TextBlock(
    val id: String,
    val pageIndex: Int = 0,
    val x: Float,
    val y: Float,
    val width: Float = 260f,
    val text: String,
    val colorArgb: Int,
    val fontSizeSp: Float = 16f
)

/**
 * Dikey çoklu sayfa yapısındaki her bir sayfanın mantıksal boyutları.
 */
@Immutable
data class NotePage(
    val pageIndex: Int,
    val widthPt: Float = DEFAULT_PAGE_WIDTH,
    val heightPt: Float = DEFAULT_PAGE_HEIGHT
) {
    companion object {
        const val DEFAULT_PAGE_WIDTH = 900f
        const val DEFAULT_PAGE_HEIGHT = 1200f
        const val PAGE_GAP = 32f

        /**
         * Verilen sayfanın belge (document) koordinatlarındaki dikey başlangıç Y değerini döndürür.
         */
        fun pageTopY(pageIndex: Int): Float =
            pageIndex * (DEFAULT_PAGE_HEIGHT + PAGE_GAP)

        /**
         * Belge Y koordinatının hangi sayfaya denk geldiğini hesaplar.
         */
        fun pageIndexAtY(docY: Float, totalPages: Int): Int {
            val stride = DEFAULT_PAGE_HEIGHT + PAGE_GAP
            val raw = (docY / stride).toInt()
            return min(max(0, raw), max(0, totalPages - 1))
        }
    }
}

/**
 * Bir not belgesinin tüm vektör verisi (çizgiler, metin kutuları ve sayfa yapısı).
 */
@Immutable
data class NoteDocument(
    val id: String,
    val title: String,
    val pages: List<NotePage>,
    val strokes: List<NoteStroke>,
    val textBlocks: List<TextBlock>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
