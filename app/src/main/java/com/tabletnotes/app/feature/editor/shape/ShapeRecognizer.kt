package com.tabletnotes.app.feature.editor.shape

import com.tabletnotes.app.domain.model.RecognizedShapeType
import com.tabletnotes.app.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RecognizedShapeResult(
    val shapeType: RecognizedShapeType,
    val labelTr: String,
    val points: List<StrokePoint>
)

/**
 * Serbest el ile çizilen kaba geometrik şekilleri analiz edip düzgün vektör şekillere
 * (Düz Çizgi, Çember, Üçgen, Kare, Dikdörtgen, Yamuk) dönüştüren şekil tanıma motoru.
 */
object ShapeRecognizer {

    fun recognize(rawPoints: List<StrokePoint>): RecognizedShapeResult? {
        if (rawPoints.size < 2) return null

        val minX = rawPoints.minOf { it.x }
        val maxX = rawPoints.maxOf { it.x }
        val minY = rawPoints.minOf { it.y }
        val maxY = rawPoints.maxOf { it.y }
        val width = max(1f, maxX - minX)
        val height = max(1f, maxY - minY)
        val diag = hypot(width, height)
        if (diag < 18f) return null

        val pathLength = computePathLength(rawPoints)
        val first = rawPoints.first()
        val last = rawPoints.last()
        val startEndDist = hypot(last.x - first.x, last.y - first.y)

        // 1. Düz Çizgi Testi: Uç noktalar arası mesafe toplam yol uzunluğuna çok yakınsa veya doğruya sapma çok küçükse
        val directRatio = startEndDist / max(1f, pathLength)
        val maxLineDev = maxDistanceToLine(rawPoints, first, last)
        if (directRatio >= 0.87f && maxLineDev <= diag * 0.14f) {
            val dx = last.x - first.x
            val dy = last.y - first.y
            val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
            val endPt = when {
                abs(angleDeg) < 6.0 || abs(abs(angleDeg) - 180.0) < 6.0 ->
                    last.copy(y = first.y)
                abs(abs(angleDeg) - 90.0) < 6.0 ->
                    last.copy(x = first.x)
                else -> last
            }
            return RecognizedShapeResult(
                shapeType = RecognizedShapeType.LINE,
                labelTr = "Düz Çizgi",
                points = interpolatePolyline(listOf(first, endPt), stepsPerEdge = 24)
            )
        }

        // Kapalı şekil analizi: eşit aralıklı örnekleme + en uzak noktadan 2 açık zincire bölerek RDP sadeleştirme
        val resampled = resampleUniform(rawPoints, count = 48)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f

        val vertices = extractClosedPolygonVertices(resampled, diag)

        val rx = width * 0.5f
        val ry = height * 0.5f
        val avgEllipseError = resampled.map { pt ->
            val nx = (pt.x - cx) / max(1f, rx)
            val ny = (pt.y - cy) / max(1f, ry)
            abs(sqrt(nx * nx + ny * ny) - 1f)
        }.average().toFloat()
        val aspectRatio = min(width, height) / max(width, height)

        // 2. Üçgen Testi (3 belirgin köşe)
        if (vertices.size == 3) {
            val triVerts = listOf(vertices[0], vertices[1], vertices[2], vertices[0])
            return RecognizedShapeResult(
                shapeType = RecognizedShapeType.TRIANGLE,
                labelTr = "Üçgen",
                points = interpolatePolyline(triVerts, stepsPerEdge = 16)
            )
        }

        // 3. Dörtgen Testi (Yamuk, Kare, Dikdörtgen — elipsten önce kesin köşeli 4 kenarlıları yakala)
        if (vertices.size == 4 && avgEllipseError > 0.045f) {
            val sortedByY = vertices.sortedBy { it.y }
            val topTwo = sortedByY.take(2).sortedBy { it.x }
            val bottomTwo = sortedByY.drop(2).sortedBy { it.x }

            val topWidth = abs(topTwo[1].x - topTwo[0].x)
            val bottomWidth = abs(bottomTwo[1].x - bottomTwo[0].x)
            val widthRatio = min(topWidth, bottomWidth) / max(1f, max(topWidth, bottomWidth))

            // Üst ve alt tabanlar anlamlı derecede farklıysa -> Yamuk (Trapezoid)
            if (widthRatio < 0.78f && min(topWidth, bottomWidth) > width * 0.22f) {
                val topY = (topTwo[0].y + topTwo[1].y) * 0.5f
                val bottomY = (bottomTwo[0].y + bottomTwo[1].y) * 0.5f
                val trapVerts = listOf(
                    StrokePoint(topTwo[0].x, topY, 0.65f),
                    StrokePoint(topTwo[1].x, topY, 0.65f),
                    StrokePoint(bottomTwo[1].x, bottomY, 0.65f),
                    StrokePoint(bottomTwo[0].x, bottomY, 0.65f),
                    StrokePoint(topTwo[0].x, topY, 0.65f)
                )
                return RecognizedShapeResult(
                    shapeType = RecognizedShapeType.TRAPEZOID,
                    labelTr = "Yamuk",
                    points = interpolatePolyline(trapVerts, stepsPerEdge = 16)
                )
            }

            // Kare veya Dikdörtgen
            val isSquare = aspectRatio >= 0.80f
            val finalHalfW = if (isSquare) (width + height) * 0.25f else width * 0.5f
            val finalHalfH = if (isSquare) (width + height) * 0.25f else height * 0.5f
            val l = cx - finalHalfW
            val r = cx + finalHalfW
            val t = cy - finalHalfH
            val b = cy + finalHalfH
            val rectVerts = listOf(
                StrokePoint(l, t, 0.65f),
                StrokePoint(r, t, 0.65f),
                StrokePoint(r, b, 0.65f),
                StrokePoint(l, b, 0.65f),
                StrokePoint(l, t, 0.65f)
            )
            return RecognizedShapeResult(
                shapeType = if (isSquare) RecognizedShapeType.SQUARE else RecognizedShapeType.RECTANGLE,
                labelTr = if (isSquare) "Kare" else "Dikdörtgen",
                points = interpolatePolyline(rectVerts, stepsPerEdge = 16)
            )
        }

        // 4. Çember / Elips Testi: yumuşak eğri (5+ köşe adayı) ve düşük elips hatası
        if (avgEllipseError < 0.22f) {
            val radius = if (aspectRatio > 0.72f) (rx + ry) * 0.5f else null
            val finalRx = radius ?: rx
            val finalRy = radius ?: ry
            val circlePts = (0..48).map { i ->
                val theta = (i / 48.0) * Math.PI * 2.0
                StrokePoint(
                    x = cx + (cos(theta) * finalRx).toFloat(),
                    y = cy + (sin(theta) * finalRy).toFloat(),
                    pressure = 0.65f
                )
            }
            return RecognizedShapeResult(
                shapeType = RecognizedShapeType.CIRCLE,
                labelTr = "Çember",
                points = circlePts
            )
        }

        // Yedek dikdörtgen / kare
        val isSquare = aspectRatio >= 0.80f
        val rectVerts = listOf(
            StrokePoint(minX, minY, 0.65f),
            StrokePoint(maxX, minY, 0.65f),
            StrokePoint(maxX, maxY, 0.65f),
            StrokePoint(minX, maxY, 0.65f),
            StrokePoint(minX, minY, 0.65f)
        )
        return RecognizedShapeResult(
            shapeType = if (isSquare) RecognizedShapeType.SQUARE else RecognizedShapeType.RECTANGLE,
            labelTr = if (isSquare) "Kare" else "Dikdörtgen",
            points = interpolatePolyline(rectVerts, stepsPerEdge = 16)
        )
    }

    /**
     * Kapalı şeklin çevresini başlangıç noktasına en uzak köşeden iki açık zincire bölerek
     * Douglas-Peucker ile gerçek çokgen köşelerini çıkarır.
     */
    private fun extractClosedPolygonVertices(pts: List<StrokePoint>, diag: Float): List<StrokePoint> {
        if (pts.size < 4) return pts
        val p0 = pts.first()
        var farthestIdx = pts.size / 2
        var maxD = -1f
        for (i in 1 until pts.size) {
            val d = hypot(pts[i].x - p0.x, pts[i].y - p0.y)
            if (d > maxD) {
                maxD = d
                farthestIdx = i
            }
        }
        val epsilon = diag * 0.06f
        val chain1 = douglasPeucker(pts.subList(0, farthestIdx + 1), epsilon)
        val chain2 = douglasPeucker(pts.subList(farthestIdx, pts.size), epsilon)
        val combined = chain1.dropLast(1) + chain2

        // Yakın kapanış köşelerini ve 180 dereceye yakın gereksiz orta noktaları temizle
        val deduped = ArrayList<StrokePoint>()
        val mergeDist = diag * 0.10f
        for (p in combined) {
            val last = deduped.lastOrNull()
            if (last == null || hypot(p.x - last.x, p.y - last.y) > mergeDist) {
                deduped.add(p)
            }
        }
        if (deduped.size >= 2 && hypot(deduped.last().x - deduped.first().x, deduped.last().y - deduped.first().y) <= mergeDist) {
            deduped.removeAt(deduped.lastIndex)
        }
        return filterCollinearVertices(deduped)
    }

    private fun filterCollinearVertices(verts: List<StrokePoint>): List<StrokePoint> {
        if (verts.size <= 3) return verts
        val result = ArrayList<StrokePoint>(verts.size)
        val n = verts.size
        for (i in 0 until n) {
            val prev = verts[(i - 1 + n) % n]
            val curr = verts[i]
            val next = verts[(i + 1) % n]
            val v1x = curr.x - prev.x
            val v1y = curr.y - prev.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val l1 = hypot(v1x, v1y).coerceAtLeast(1f)
            val l2 = hypot(v2x, v2y).coerceAtLeast(1f)
            val dot = (v1x * v2x + v1y * v2y) / (l1 * l2)
            // Aynı doğrultudaki noktayı (dot > 0.96 -> açı < ~16°) at
            if (dot <= 0.96f) {
                result.add(curr)
            }
        }
        return result
    }

    private fun computePathLength(pts: List<StrokePoint>): Float {
        var len = 0f
        for (i in 1 until pts.size) {
            len += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        }
        return len
    }

    private fun maxDistanceToLine(pts: List<StrokePoint>, a: StrokePoint, b: StrokePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        var maxDist = 0f
        for (p in pts) {
            val cross = abs((p.x - a.x) * dy - (p.y - a.y) * dx) / len
            if (cross > maxDist) maxDist = cross
        }
        return maxDist
    }

    private fun resampleUniform(pts: List<StrokePoint>, count: Int): List<StrokePoint> {
        val total = computePathLength(pts).coerceAtLeast(1f)
        val step = total / max(1, count - 1)
        val out = ArrayList<StrokePoint>(count)
        out.add(pts.first())
        var accumulated = 0f
        var prev = pts.first()
        var i = 1
        while (i < pts.size) {
            val curr = pts[i]
            val seg = hypot(curr.x - prev.x, curr.y - prev.y)
            if (accumulated + seg >= step) {
                val t = (step - accumulated) / max(0.001f, seg)
                val nx = prev.x + t * (curr.x - prev.x)
                val ny = prev.y + t * (curr.y - prev.y)
                val np = StrokePoint(nx, ny, pressure = 0.65f)
                out.add(np)
                prev = np
                accumulated = 0f
            } else {
                accumulated += seg
                prev = curr
                i++
            }
        }
        if (out.size < count) {
            out.add(pts.last())
        }
        return out
    }

    private fun douglasPeucker(pts: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
        if (pts.size < 3) return pts
        val first = pts.first()
        val last = pts.last()
        var index = -1
        var maxDist = 0f
        val dx = last.x - first.x
        val dy = last.y - first.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        for (i in 1 until pts.size - 1) {
            val d = abs((pts[i].x - first.x) * dy - (pts[i].y - first.y) * dx) / len
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon && index != -1) {
            val left = douglasPeucker(pts.subList(0, index + 1), epsilon)
            val right = douglasPeucker(pts.subList(index, pts.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun interpolatePolyline(vertices: List<StrokePoint>, stepsPerEdge: Int): List<StrokePoint> {
        if (vertices.size < 2) return vertices
        val out = ArrayList<StrokePoint>(vertices.size * stepsPerEdge)
        for (i in 0 until vertices.size - 1) {
            val a = vertices[i]
            val b = vertices[i + 1]
            for (s in 0 until stepsPerEdge) {
                val t = s.toFloat() / stepsPerEdge
                out.add(
                    StrokePoint(
                        x = a.x + (b.x - a.x) * t,
                        y = a.y + (b.y - a.y) * t,
                        pressure = 0.65f
                    )
                )
            }
        }
        out.add(vertices.last().copy(pressure = 0.65f))
        return out
    }
}
