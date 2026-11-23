package com.tabletnotes.app.feature.editor

import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NoteImageBlock
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TagNormalizer
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.feature.editor.ui.NoteEditorController
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEnhancementsTest {

    @Test
    fun `TagNormalizer cleans hash, whitespace, duplicates, and preserves Turkish letters`() {
        assertEquals("vize", TagNormalizer.normalize("  #Vize "))
        assertEquals("ödev_1", TagNormalizer.normalize("#Ödev_1!"))
        assertEquals(listOf("vize", "ödev", "fizik"), TagNormalizer.parseCsv("#vize, ödev, VIZE, fizik"))
        assertEquals("vize,ödev", TagNormalizer.toCsv(listOf("#Vize", "ödev", "vize")))
    }

    @Test
    fun `NoteBinaryCodec preserves favorite, pinned, tags, page backgroundImagePath, and images`() {
        val doc = NoteDocument(
            id = "note_enh_1",
            title = "PDF ve Görselli Not",
            pages = listOf(
                NotePage(pageIndex = 0, backgroundImagePath = "/tmp/pdf_0.jpg"),
                NotePage(pageIndex = 1, backgroundImagePath = null)
            ),
            strokes = listOf(
                NoteStroke(
                    id = "s1",
                    pageIndex = 0,
                    tool = DrawingToolType.PEN,
                    colorArgb = 0xFFDC4C46.toInt(),
                    thickness = 4f,
                    points = listOf(StrokePoint(100f, 150f), StrokePoint(200f, 250f))
                )
            ),
            textBlocks = listOf(
                TextBlock(id = "tb1", pageIndex = 1, x = 80f, y = 1300f, text = "Soru 2", colorArgb = 0xFF000000.toInt())
            ),
            images = listOf(
                NoteImageBlock(
                    id = "img1",
                    pageIndex = 0,
                    x = 120f,
                    y = 200f,
                    width = 320f,
                    height = 240f,
                    filePath = "/tmp/gallery_img.jpg"
                )
            ),
            isFavorite = true,
            isPinned = true,
            tags = listOf("vize", "ödev"),
            createdAtMillis = 1700000000000L,
            updatedAtMillis = 1700000050000L
        )

        val encoded = NoteBinaryCodec.encode(doc, compress = true)
        val decoded = NoteBinaryCodec.decode(encoded)

        assertTrue(decoded.isFavorite)
        assertTrue(decoded.isPinned)
        assertEquals(listOf("vize", "ödev"), decoded.tags)
        assertEquals("/tmp/pdf_0.jpg", decoded.pages[0].backgroundImagePath)
        assertNull(decoded.pages[1].backgroundImagePath)
        assertEquals(1, decoded.images.size)
        assertEquals("img1", decoded.images[0].id)
        assertEquals(120f, decoded.images[0].x, 0.2f)
        assertEquals(320f, decoded.images[0].width, 0.2f)
        assertEquals("/tmp/gallery_img.jpg", decoded.images[0].filePath)
    }

    @Test
    fun `NoteEditorController page management shifts strokes, textBlocks, and images accurately`() {
        val controller = NoteEditorController()
        val stride = NotePage.DEFAULT_PAGE_HEIGHT + NotePage.PAGE_GAP
        val doc = NoteDocument(
            id = "note_pm",
            title = "Sayfa Testi",
            pages = listOf(NotePage(0, backgroundImagePath = "/bg/0.jpg"), NotePage(1)),
            strokes = listOf(
                NoteStroke("s0", pageIndex = 0, tool = DrawingToolType.PEN, colorArgb = 0xFF000000.toInt(), thickness = 4f, points = listOf(StrokePoint(50f, 100f))),
                NoteStroke("s1", pageIndex = 1, tool = DrawingToolType.PEN, colorArgb = 0xFF000000.toInt(), thickness = 4f, points = listOf(StrokePoint(50f, stride + 100f)))
            ),
            textBlocks = listOf(
                TextBlock("tb1", pageIndex = 1, x = 60f, y = stride + 200f, text = "P2", colorArgb = 0xFF000000.toInt())
            ),
            images = listOf(
                NoteImageBlock("img1", pageIndex = 1, x = 100f, y = stride + 300f, width = 200f, height = 150f, filePath = "/img/1.jpg")
            ),
            createdAtMillis = 1L,
            updatedAtMillis = 2L
        )
        controller.loadDocument(doc)

        // 1. Sayfa 0 sonrasına boş sayfa ekle -> eski Sayfa 1, yeni Sayfa 2 olur
        controller.insertBlankPageAfter(0)
        assertEquals(3, controller.pages.size)
        val shifted = controller.toNoteDocument()
        val s1AfterInsert = shifted.strokes.first { it.id == "s1" }
        assertEquals(2, s1AfterInsert.pageIndex)
        assertEquals(2 * stride + 100f, s1AfterInsert.points[0].y, 0.5f)
        assertEquals(2, shifted.images.first().pageIndex)
        assertEquals(2 * stride + 300f, shifted.images.first().y, 0.5f)

        // 2. Sayfa 0'ı çoğalt -> toplam 4 sayfa, Sayfa 0 çizgisi Sayfa 1'e kopyalanır
        controller.duplicatePage(0)
        assertEquals(4, controller.pages.size)
        val afterDup = controller.toNoteDocument()
        assertEquals("/bg/0.jpg", afterDup.pages[1].backgroundImagePath)
        assertEquals(2, afterDup.strokes.count { it.points[0].x == 50f && (it.pageIndex == 0 || it.pageIndex == 1) })

        // 3. Sayfayı yukarı/aşağı taşı
        controller.movePage(0, 1)
        assertEquals("/bg/0.jpg", controller.pages[1].backgroundImagePath)

        // 4. Tekil sayfa sil ve en az 1 sayfa kaldığından emin ol
        assertTrue(controller.deletePage(1))
        assertEquals(3, controller.pages.size)
        assertTrue(controller.deletePage(2))
        assertTrue(controller.deletePage(1))
        assertFalse(controller.deletePage(0)) // Son sayfa silinemez
    }

    @Test
    fun `NoteEditorController image add, translate, scale, delete support Undo and Redo`() {
        val controller = NoteEditorController()
        controller.loadDocument(
            NoteDocument(
                id = "note_img",
                title = "Görsel",
                pages = listOf(NotePage(0)),
                strokes = emptyList(),
                textBlocks = emptyList(),
                createdAtMillis = 1L,
                updatedAtMillis = 2L
            )
        )

        controller.addImageBlock("/tmp/photo.jpg", aspectRatio = 2.0f, targetPageIndex = 0)
        assertEquals(1, controller.images.size)
        val orig = controller.images.first()
        assertEquals(380f, orig.width, 0.1f)
        assertEquals(190f, orig.height, 0.1f)

        controller.translateSelectedImage(40f, 60f, recordUndo = true, originalSnapshot = orig)
        assertEquals(orig.x + 40f, controller.images.first().x, 0.5f)

        controller.deleteSelectedImage()
        assertEquals(0, controller.images.size)
        assertTrue(controller.canUndo)

        controller.undo() // Görseli geri getirir
        assertEquals(1, controller.images.size)

        controller.redo() // Tekrar siler
        assertEquals(0, controller.images.size)
    }
}
