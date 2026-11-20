package com.tabletnotes.app.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min

/**
 * Editörde kullanılan çizim, boyama ve seçim araçları (Samsung Notes tarzı).
 */
enum class DrawingToolType(val wireId: Int) {
    PEN(0),               // Tükenmez / Standart Kalem
    HIGHLIGHTER(1),       // Fosforlu Kalem
    ERASER(2),            // Çizgi Silgisi
    TEXT(3),              // Metin Kutusu
    FINE_FOUNTAIN(4),     // İnce Dolma Kalem (Yüksek basınç hassasiyeti)
    CALLIGRAPHY(5),       // Kalın Dolma / Kaligrafi Kalemi (Kesik uç açılı)
    PENCIL(6),            // Kurşun Kalem (Dokulu grafit hissi)
    MARKER(7),            // Marker Kalem (Dolgun keçe uç)
    WATERCOLOR(8),        // Sulu Boya Fırçası (Yumuşak yarı saydam katmanlı)
    LASSO_SELECT(9),      // Kare Seçim Modu (Taşıma, Kopyalama, Boyutlandırma, Silme)
    LASER_POINTER(10),    // Lazer İşaretçi (Kalıcı değil, belirlenen süre sonra kaybolur)
    SHAPE_TOOL(11);       // Şekil Algılama ve Düzeltme (Kare, Üçgen, Yamuk, Düz Çizgi, Çember)

    val isPenVariant: Boolean
        get() = this == PEN ||
            this == FINE_FOUNTAIN ||
            this == CALLIGRAPHY ||
            this == PENCIL ||
            this == MARKER ||
            this == WATERCOLOR

    companion object {
        val penVariants: List<DrawingToolType> = listOf(
            FINE_FOUNTAIN,
            CALLIGRAPHY,
            PEN,
            PENCIL,
            MARKER,
            WATERCOLOR
        )

        fun fromWireId(id: Int): DrawingToolType =
            entries.firstOrNull { it.wireId == id } ?: PEN
    }
}

/**
 * Silgi çalışma modu: Çizgi Silgisi veya Alan Silgisi.
 */
enum class EraserMode {
    STROKE_ERASER, // Çekilen bir çizgiyi tek dokunuşta tamamen siler
    AREA_ERASER    // Sadece silgi dairesinin değdiği kısmı hassas olarak siler
}

/**
 * Şekil algılama motorunun tanıdığı geometrik şekil tipleri.
 */
enum class RecognizedShapeType {
    LINE,
    CIRCLE,
    TRIANGLE,
    RECTANGLE,
    SQUARE,
    TRAPEZOID
}

/**
 * Şekil çizim modunda algılanan ve kullanıcının boyutunu/yerini ayarlayıp onaylayacağı geçici şekil.
 */
@Immutable
data class PendingRecognizedShape(
    val shapeType: RecognizedShapeType,
    val labelTr: String,
    val pageIndex: Int,
    val colorArgb: Int,
    val thickness: Float,
    val points: List<StrokePoint>,
    val bounds: BoundingBox = BoundingBox.fromPoints(points, padding = 10f)
)

/**
 * Uygulama temasından bağımsız (Independent) Kağıt Arka Plan Rengi.
 */
enum class PaperColor(
    val wireId: Int,
    val bgArgb: Int,
    val minorPatternArgb: Int,
    val majorPatternArgb: Int
) {
    CREAM(
        wireId = 0,
        bgArgb = 0xFFFAF7EE.toInt(),
        minorPatternArgb = 0xFFD9D3C4.toInt(),
        majorPatternArgb = 0xFFC5BEB0.toInt()
    ),
    WHITE(
        wireId = 1,
        bgArgb = 0xFFFFFFFF.toInt(),
        minorPatternArgb = 0xFFDCE1EC.toInt(),
        majorPatternArgb = 0xFFC5CEE0.toInt()
    ),
    DARK(
        wireId = 2,
        bgArgb = 0xFF161925.toInt(),
        minorPatternArgb = 0xFF2B3147.toInt(),
        majorPatternArgb = 0xFF38405C.toInt()
    );

    companion object {
        fun fromWireId(id: Int): PaperColor =
            entries.firstOrNull { it.wireId == id } ?: CREAM
    }
}

/**
 * Kağıt Deseni / Şablonu (Noktalı, Kareli, Çizgili, Düz Boş).
 */
enum class PaperTemplate(val wireId: Int) {
    DOTTED(0),
    GRID(1),
    LINED(2),
    BLANK(3);

    companion object {
        fun fromWireId(id: Int): PaperTemplate =
            entries.firstOrNull { it.wireId == id } ?: DOTTED
    }
}

/**
 * Tek bir kalem/dokunuş örnekleme noktası (sample point).
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
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f

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

        fun union(boxes: List<BoundingBox>): BoundingBox? {
            if (boxes.isEmpty()) return null
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (b in boxes) {
                if (b.minX < minX) minX = b.minX
                if (b.minY < minY) minY = b.minY
                if (b.maxX > maxX) maxX = b.maxX
                if (b.maxY > maxY) maxY = b.maxY
            }
            return BoundingBox(minX, minY, maxX, maxY)
        }
    }
}

/**
 * Tamamlanmış (committed) bir vektör çizgi (Stroke).
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

        fun pageTopY(pageIndex: Int): Float =
            pageIndex * (DEFAULT_PAGE_HEIGHT + PAGE_GAP)

        fun pageIndexAtY(docY: Float, totalPages: Int): Int {
            val stride = DEFAULT_PAGE_HEIGHT + PAGE_GAP
            val raw = (docY / stride).toInt()
            return min(max(0, raw), max(0, totalPages - 1))
        }
    }
}

/**
 * Bir not belgesinin tüm vektör verisi (çizgiler, metin kutuları, sayfa yapısı, ders klasörü ve kağıt ayarları).
 */
@Immutable
data class NoteDocument(
    val id: String,
    val title: String,
    val pages: List<NotePage>,
    val strokes: List<NoteStroke>,
    val textBlocks: List<TextBlock>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val courseName: String = "Fizik",
    val paperColor: PaperColor = PaperColor.CREAM,
    val paperTemplate: PaperTemplate = PaperTemplate.DOTTED
)
