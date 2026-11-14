package com.tabletnotes.app.feature.editor.spatial

import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.domain.model.NoteStroke
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 10.000+ çizgide yüksek performanslı görünüm kırpma (Viewport Culling) ve
 * Çizgi Silgisi (Stroke Eraser) kesişim testi için 2B Uzamsal Izgara İndeksi (Spatial Grid Index).
 *
 * Belge alanı `cellSize` (varsayılan 128 birim) büyüklüğünde hücrelere bölünür.
 * Her çizgi yalnızca sınırlayıcı kutusunun (BoundingBox) değdiği hücrelere kaydedilir.
 */
class SpatialGridIndex(
    private val cellSize: Float = 128f
) {
    private val strokesById = LinkedHashMap<String, NoteStroke>()
    private val cellBuckets = HashMap<Long, MutableSet<String>>()
    private val strokeCells = HashMap<String, LongArray>()

    val size: Int
        @Synchronized get() = strokesById.size

    @Synchronized
    fun clear() {
        strokesById.clear()
        cellBuckets.clear()
        strokeCells.clear()
    }

    @Synchronized
    fun getAllStrokes(): List<NoteStroke> = ArrayList(strokesById.values)

    @Synchronized
    fun insert(stroke: NoteStroke) {
        if (strokesById.containsKey(stroke.id)) {
            remove(stroke.id)
        }
        strokesById[stroke.id] = stroke
        val keys = computeCellKeys(stroke.bounds)
        strokeCells[stroke.id] = keys
        for (key in keys) {
            cellBuckets.getOrPut(key) { LinkedHashSet() }.add(stroke.id)
        }
    }

    @Synchronized
    fun insertAll(strokes: Collection<NoteStroke>) {
        for (stroke in strokes) {
            insert(stroke)
        }
    }

    @Synchronized
    fun remove(strokeId: String): NoteStroke? {
        val removed = strokesById.remove(strokeId) ?: return null
        val keys = strokeCells.remove(strokeId)
        if (keys != null) {
            for (key in keys) {
                val bucket = cellBuckets[key]
                if (bucket != null) {
                    bucket.remove(strokeId)
                    if (bucket.isEmpty()) {
                        cellBuckets.remove(key)
                    }
                }
            }
        }
        return removed
    }

    /**
     * Sadece verilen görünüm dikdörtgeni (Viewport veya Karo/Tile alanı) içine giren
     * çizgileri döndürür. 10.000+ çizgi varken ekran dışındaki çizgiler hiç taranmaz.
     */
    @Synchronized
    fun queryViewport(viewport: BoundingBox): List<NoteStroke> {
        if (strokesById.isEmpty()) return emptyList()
        val keys = computeCellKeys(viewport)
        val candidateIds = LinkedHashSet<String>()
        for (key in keys) {
            val bucket = cellBuckets[key] ?: continue
            candidateIds.addAll(bucket)
        }
        if (candidateIds.isEmpty()) return emptyList()

        val result = ArrayList<NoteStroke>(candidateIds.size)
        for (id in candidateIds) {
            val stroke = strokesById[id] ?: continue
            if (stroke.bounds.intersects(viewport)) {
                result.add(stroke)
            }
        }
        return result
    }

    /**
     * Çizgi Silgisi (Stroke Eraser) hareketi sırasında `(x0, y0) -> (x1, y1)` doğru parçasına
     * `eraserRadius` mesafesinden daha yakın geçen tüm çizgileri bulur.
     */
    @Synchronized
    fun findStrokesHitBySegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        eraserRadius: Float
    ): List<NoteStroke> {
        if (strokesById.isEmpty()) return emptyList()

        val sweepBounds = BoundingBox(
            minX = min(x0, x1) - eraserRadius,
            minY = min(y0, y1) - eraserRadius,
            maxX = max(x0, x1) + eraserRadius,
            maxY = max(y0, y1) + eraserRadius
        )

        val candidates = queryViewport(sweepBounds)
        if (candidates.isEmpty()) return emptyList()

        val hitStrokes = ArrayList<NoteStroke>()
        for (stroke in candidates) {
            val threshold = eraserRadius + (stroke.thickness * 0.55f)
            val thresholdSq = threshold * threshold
            if (doesStrokeIntersectSegment(stroke, x0, y0, x1, y1, thresholdSq)) {
                hitStrokes.add(stroke)
            }
        }
        return hitStrokes
    }

    private fun doesStrokeIntersectSegment(
        stroke: NoteStroke,
        ex0: Float,
        ey0: Float,
        ex1: Float,
        ey1: Float,
        thresholdSq: Float
    ): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false
        if (pts.size == 1) {
            return pointToSegmentDistanceSq(pts[0].x, pts[0].y, ex0, ey0, ex1, ey1) <= thresholdSq
        }
        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            if (segmentToSegmentDistanceSq(p0.x, p0.y, p1.x, p1.y, ex0, ey0, ex1, ey1) <= thresholdSq) {
                return true
            }
        }
        return false
    }

    private fun computeCellKeys(bounds: BoundingBox): LongArray {
        val minCellX = floor(bounds.minX / cellSize).toInt()
        val maxCellX = floor(bounds.maxX / cellSize).toInt()
        val minCellY = floor(bounds.minY / cellSize).toInt()
        val maxCellY = floor(bounds.maxY / cellSize).toInt()

        val countX = (maxCellX - minCellX + 1).coerceAtLeast(1)
        val countY = (maxCellY - minCellY + 1).coerceAtLeast(1)
        val keys = LongArray(countX * countY)
        var idx = 0
        for (cx in minCellX..maxCellX) {
            for (cy in minCellY..maxCellY) {
                keys[idx++] = packCellKey(cx, cy)
            }
        }
        return keys
    }

    private fun packCellKey(cellX: Int, cellY: Int): Long {
        return (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        /**
         * `(px, py)` noktasının `(ax, ay) - (bx, by)` doğru parçasına olan en kısa mesafesinin karesi.
         */
        fun pointToSegmentDistanceSq(
            px: Float,
            py: Float,
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float
        ): Float {
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            if (lenSq <= 1e-6f) {
                val ex = px - ax
                val ey = py - ay
                return ex * ex + ey * ey
            }
            val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
            val projX = ax + t * dx
            val projY = ay + t * dy
            val distX = px - projX
            val distY = py - projY
            return distX * distX + distY * distY
        }

        /**
         * İki doğru parçası arasındaki en kısa mesafenin karesi.
         */
        fun segmentToSegmentDistanceSq(
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float,
            cx: Float,
            cy: Float,
            dx: Float,
            dy: Float
        ): Float {
            if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
            val d1 = pointToSegmentDistanceSq(ax, ay, cx, cy, dx, dy)
            val d2 = pointToSegmentDistanceSq(bx, by, cx, cy, dx, dy)
            val d3 = pointToSegmentDistanceSq(cx, cy, ax, ay, bx, by)
            val d4 = pointToSegmentDistanceSq(dx, dy, ax, ay, bx, by)
            return min(min(d1, d2), min(d3, d4))
        }

        private fun segmentsIntersect(
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float,
            cx: Float,
            cy: Float,
            dx: Float,
            dy: Float
        ): Boolean {
            val o1 = ccw(ax, ay, bx, by, cx, cy)
            val o2 = ccw(ax, ay, bx, by, dx, dy)
            val o3 = ccw(cx, cy, dx, dy, ax, ay)
            val o4 = ccw(cx, cy, dx, dy, bx, by)
            return (o1 * o2 < 0f) && (o3 * o4 < 0f)
        }

        private fun ccw(
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float,
            cx: Float,
            cy: Float
        ): Float {
            return (by - ay) * (cx - bx) - (bx - ax) * (cy - by)
        }
    }
}

