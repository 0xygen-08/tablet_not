package com.tabletnotes.app.feature.editor.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.feature.editor.spatial.SpatialGridIndex
import kotlin.math.floor

/**
 * 10.000+ çizgi (stroke) olduğunda bile 60/120 FPS kare hızını korumak için
 * sabitlenmiş ("dry") çizgileri `1024x1024` piksellik Bitmap Karolarında (Tiles) önbelleğe alan sınıf.
 */
class BitmapTileCache(
    val tileDocSize: Float = 512f,
    private val tilePixelSize: Int = 1024,
    private val maxCachedTiles: Int = 48
) {
    private val bitmapCache = object : LinkedHashMap<Long, Bitmap>(maxCachedTiles, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean {
            return size > maxCachedTiles
        }
    }

    private val dirtyTiles = HashSet<Long>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dstRect = RectF()

    val cachedTileCount: Int
        @Synchronized get() = bitmapCache.size

    @Synchronized
    fun clearAll() {
        bitmapCache.clear()
        dirtyTiles.clear()
    }

    /**
     * Sadece verilen sınırlayıcı kutuyla (ör. yeni eklenen veya silinen tek bir çizgi)
     * kesişen karoları geçersiz kılar (dirty olarak işaretler).
     */
    @Synchronized
    fun invalidateBounds(bounds: BoundingBox) {
        val minTx = floor(bounds.minX / tileDocSize).toInt()
        val maxTx = floor(bounds.maxX / tileDocSize).toInt()
        val minTy = floor(bounds.minY / tileDocSize).toInt()
        val maxTy = floor(bounds.maxY / tileDocSize).toInt()

        for (tx in minTx..maxTx) {
            for (ty in minTy..maxTy) {
                dirtyTiles.add(packTileKey(tx, ty))
            }
        }
    }

    /**
     * Görünüm alanı (Viewport) içine düşen tüm karoları [canvas] üzerine çizer.
     * Canvas'ın belge (document) koordinat sistemine ölçeklenmiş/kaydırılmış olduğu varsayılır.
     */
    @Synchronized
    fun drawVisibleTiles(
        canvas: Canvas,
        viewportDocBounds: BoundingBox,
        spatialIndex: SpatialGridIndex
    ) {
        val minTx = floor(viewportDocBounds.minX / tileDocSize).toInt()
        val maxTx = floor(viewportDocBounds.maxX / tileDocSize).toInt()
        val minTy = floor(viewportDocBounds.minY / tileDocSize).toInt()
        val maxTy = floor(viewportDocBounds.maxY / tileDocSize).toInt()

        for (tx in minTx..maxTx) {
            for (ty in minTy..maxTy) {
                val tileBounds = BoundingBox(
                    minX = tx * tileDocSize,
                    minY = ty * tileDocSize,
                    maxX = (tx + 1) * tileDocSize,
                    maxY = (ty + 1) * tileDocSize
                )
                if (!tileBounds.intersects(viewportDocBounds)) continue

                val key = packTileKey(tx, ty)
                var bitmap = bitmapCache.get(key)
                val isDirty = dirtyTiles.remove(key)

                if (bitmap == null || isDirty) {
                    val strokesInTile = spatialIndex.queryViewport(tileBounds)
                    if (strokesInTile.isEmpty()) {
                        if (bitmap != null) {
                            bitmapCache.remove(key)
                        }
                        continue
                    }

                    if (bitmap == null) {
                        bitmap = Bitmap.createBitmap(
                            tilePixelSize,
                            tilePixelSize,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmapCache.put(key, bitmap)
                    } else {
                        bitmap.eraseColor(Color.TRANSPARENT)
                    }

                    renderTile(bitmap, tileBounds, strokesInTile)
                }

                dstRect.set(
                    tileBounds.minX,
                    tileBounds.minY,
                    tileBounds.maxX,
                    tileBounds.maxY
                )
                canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint)
            }
        }
    }

    private fun renderTile(
        bitmap: Bitmap,
        tileBounds: BoundingBox,
        strokes: List<com.tabletnotes.app.domain.model.NoteStroke>
    ) {
        val tileCanvas = Canvas(bitmap)
        tileCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val scale = tilePixelSize / tileDocSize
        tileCanvas.save()
        tileCanvas.scale(scale, scale)
        tileCanvas.translate(-tileBounds.minX, -tileBounds.minY)
        for (i in strokes.indices) {
            StrokeRenderer.drawStroke(tileCanvas, strokes[i])
        }
        tileCanvas.restore()
    }

    private fun packTileKey(tx: Int, ty: Int): Long {
        return (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)
    }
}

