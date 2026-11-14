package com.tabletnotes.app.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme

/**
 * Stitch tasarımındaki kareli mühendislik defteri (Graph Paper Grid) arka planını
 * yüksek performansla (ekstra Composable katmanı oluşturmadan `drawBehind` ile) çizen bileşen.
 *
 * Tema değiştiğinde (ör. Koyu Charcoal -> Sıcak Kağıt) kare çizgilerinin rengi otomatik güncellenir.
 */
@Composable
fun GraphGridBackground(
    modifier: Modifier = Modifier,
    showMajorGrid: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = TabletNotesTheme.palette

    Box(
        modifier = modifier.drawBehind {
            drawRect(color = palette.canvasBackground)

            val stepPx = palette.canvasGridSpacingDp.toPx().coerceAtLeast(12f)
            val strokeWidthPx = 1.dp.toPx()
            val majorEvery = 5

            // Dikey kare çizgileri
            var colIndex = 0
            var x = 0f
            while (x <= size.width) {
                val isMajor = showMajorGrid && (colIndex % majorEvery == 0)
                drawLine(
                    color = if (isMajor) palette.canvasMajorGrid else palette.canvasMinorGrid,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidthPx
                )
                x += stepPx
                colIndex++
            }

            // Yatay kare çizgileri
            var rowIndex = 0
            var y = 0f
            while (y <= size.height) {
                val isMajor = showMajorGrid && (rowIndex % majorEvery == 0)
                drawLine(
                    color = if (isMajor) palette.canvasMajorGrid else palette.canvasMinorGrid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidthPx
                )
                y += stepPx
                rowIndex++
            }
        },
        content = content
    )
}
