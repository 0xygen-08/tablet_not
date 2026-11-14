package com.tabletnotes.app.feature.format

import com.tabletnotes.app.core.storage.AtomicNoteFileStore
import com.tabletnotes.app.core.storage.LowStorageException
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.feature.format.binary.InvalidNoteFormatException
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import com.tabletnotes.app.feature.format.json.NoteJsonCodec
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBinaryCodecTest {

    private fun createSampleDocument(): NoteDocument {
        val pages = listOf(
            NotePage(pageIndex = 0, widthPt = 960f, heightPt = 1280f),
            NotePage(pageIndex = 1, widthPt = 960f, heightPt = 1280f)
        )
        val strokes = listOf(
            NoteStroke(
                id = "stroke-pen-1",
                pageIndex = 0,
                tool = DrawingToolType.PEN,
                colorArgb = 0xFF1F1F1F.toInt(),
                thickness = 3.5f,
                points = listOf(
                    StrokePoint(x = 120.4f, y = 240.8f, pressure = 0.425f, tiltRadians = 0.215f, orientationRadians = -0.512f, timestampMillis = 1700000001000L),
                    StrokePoint(x = 125.9f, y = 246.2f, pressure = 0.610f, tiltRadians = 0.220f, orientationRadians = -0.500f, timestampMillis = 1700000001008L),
                    StrokePoint(x = 140.0f, y = 260.5f, pressure = 0.855f, tiltRadians = 0.240f, orientationRadians = -0.480f, timestampMillis = 1700000001016L)
                )
            ),
            NoteStroke(
                id = "stroke-hl-2",
                pageIndex = 1,
                tool = DrawingToolType.HIGHLIGHTER,
                colorArgb = 0xFFD97757.toInt(),
                thickness = 18.25f,
                points = listOf(
                    StrokePoint(x = 40.0f, y = 1400.0f, pressure = 0.500f, tiltRadians = 0f, orientationRadians = 0f, timestampMillis = 1700000005000L),
                    StrokePoint(x = 380.5f, y = 1400.2f, pressure = 0.500f, tiltRadians = 0f, orientationRadians = 0f, timestampMillis = 1700000005040L)
                )
            )
        )
        val textBlocks = listOf(
            TextBlock(
                id = "tb-1",
                pageIndex = 0,
                x = 180.5f,
                y = 320.0f,
                width = 260.0f,
                text = "Merhaba TabletNotes! 120Hz Stylus & Vektör Format Testi",
                colorArgb = 0xFF1F1F1F.toInt(),
                fontSizeSp = 16.0f
            )
        )
        return NoteDocument(
            id = "doc-test-001",
            title = "Mimari Toplantı Notları",
            pages = pages,
            strokes = strokes,
            textBlocks = textBlocks,
            createdAtMillis = 1700000000000L,
            updatedAtMillis = 1700000010000L
        )
    }

    @Test
    fun `encode and decode with GZIP produces identical document and strokes`() {
        val original = createSampleDocument()
        val encodedBytes = NoteBinaryCodec.encode(original, compress = true)

        // Başlık kontrolü: "TNOT" + v1 (0x00, 0x01) + GZIP bayrağı (0x01)
        assertEquals('T'.code.toByte(), encodedBytes[0])
        assertEquals('N'.code.toByte(), encodedBytes[1])
        assertEquals('O'.code.toByte(), encodedBytes[2])
        assertEquals('T'.code.toByte(), encodedBytes[3])
        assertEquals(0.toByte(), encodedBytes[4])
        assertEquals(1.toByte(), encodedBytes[5])
        assertEquals(1.toByte(), encodedBytes[6])

        val decoded = NoteBinaryCodec.decode(encodedBytes)
        assertDocumentsIdentical(original, decoded)
    }

    @Test
    fun `encode and decode without compression produces identical document and strokes`() {
        val base = createSampleDocument()
        // Gerçekçi bir 120Hz stylus çizgisi (80 nokta) ekleyerek hem delta kodlamayı hem GZIP kazancını doğrula
        val denseStroke = NoteStroke(
            id = "stroke-dense-120hz",
            pageIndex = 0,
            tool = DrawingToolType.PEN,
            colorArgb = 0xFF1F1F1F.toInt(),
            thickness = 2.8f,
            points = (0 until 80).map { idx ->
                StrokePoint(
                    x = 100f + idx * 2.5f,
                    y = 200f + idx * 1.5f,
                    pressure = 0.5f + (idx % 10) * 0.02f,
                    tiltRadians = 0.25f,
                    orientationRadians = 0.5f,
                    timestampMillis = 1700000010000L + idx * 8L
                )
            }
        )
        val original = base.copy(strokes = base.strokes + denseStroke)
        val rawBytes = NoteBinaryCodec.encode(original, compress = false)
        val compressedBytes = NoteBinaryCodec.encode(original, compress = true)

        assertEquals(0.toByte(), rawBytes[6])
        assertTrue("GZIP sıkıştırma çok noktalı çizgilerde boyutu küçültmeli", compressedBytes.size < rawBytes.size)

        val decoded = NoteBinaryCodec.decode(rawBytes)
        assertDocumentsIdentical(original, decoded)
    }

    @Test(expected = InvalidNoteFormatException::class)
    fun `decode rejects invalid magic header`() {
        val badBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x00, 0x01, 0x00)
        NoteBinaryCodec.decode(badBytes)
    }

    @Test(expected = InvalidNoteFormatException::class)
    fun `decode rejects unsupported future version header`() {
        val futureVersionBytes = byteArrayOf(
            'T'.code.toByte(),
            'N'.code.toByte(),
            'O'.code.toByte(),
            'T'.code.toByte(),
            0x00,
            0x63, // v99
            0x00
        )
        NoteBinaryCodec.decode(futureVersionBytes)
    }

    @Test
    fun `json debug export includes header version strokes and text blocks`() {
        val doc = createSampleDocument()
        val json = NoteJsonCodec.encodeToJsonString(doc)
        assertTrue(json.contains("\"formatMagic\": \"TNOT\""))
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"stroke-pen-1\""))
        assertTrue(json.contains("\"HIGHLIGHTER\""))
        assertTrue(json.contains("Merhaba TabletNotes!"))
    }

    @Test
    fun `atomic file store writes via temp file and cleans up tmp on rename`() {
        val tempDir = Files.createTempDirectory("tabletnotes_atomic_test").toFile()
        try {
            val store = AtomicNoteFileStore(rootDir = tempDir, minRequiredFreeBytes = 1024L)
            val doc = createSampleDocument()
            val payload = NoteBinaryCodec.encode(doc, compress = true)

            val finalFile = store.writeNoteBytesAtomically(doc.id, payload)
            val tempFile = java.io.File(finalFile.parentFile, "${finalFile.name}.tmp")

            assertTrue("Hedef .tnote dosyası mevcut olmalı", finalFile.exists())
            assertFalse("Geçici .tmp dosyası atomic move sonrası kalmamalı", tempFile.exists())

            val readBack = store.readNoteBytes(doc.id)
            assertNotNull(readBack)
            val decoded = NoteBinaryCodec.decode(readBack)
            assertDocumentsIdentical(doc, decoded)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test(expected = LowStorageException::class)
    fun `atomic file store throws LowStorageException when required space exceeds disk capacity`() {
        val tempDir = Files.createTempDirectory("tabletnotes_lowspace_test").toFile()
        try {
            val impossibleRequirement = Long.MAX_VALUE / 2
            val store = AtomicNoteFileStore(rootDir = tempDir, minRequiredFreeBytes = impossibleRequirement)
            store.writeNoteBytesAtomically("doc-low", ByteArray(128))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun assertDocumentsIdentical(expected: NoteDocument, actual: NoteDocument) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.createdAtMillis, actual.createdAtMillis)
        assertEquals(expected.updatedAtMillis, actual.updatedAtMillis)

        assertEquals(expected.pages.size, actual.pages.size)
        for (i in expected.pages.indices) {
            assertEquals(expected.pages[i].pageIndex, actual.pages[i].pageIndex)
            assertEquals(expected.pages[i].widthPt, actual.pages[i].widthPt, 0.1f)
            assertEquals(expected.pages[i].heightPt, actual.pages[i].heightPt, 0.1f)
        }

        assertEquals(expected.strokes.size, actual.strokes.size)
        for (i in expected.strokes.indices) {
            val es = expected.strokes[i]
            val asStroke = actual.strokes[i]
            assertEquals(es.id, asStroke.id)
            assertEquals(es.pageIndex, asStroke.pageIndex)
            assertEquals(es.tool, asStroke.tool)
            assertEquals(es.colorArgb, asStroke.colorArgb)
            assertEquals(es.thickness, asStroke.thickness, 0.01f)
            assertEquals(es.points.size, asStroke.points.size)

            for (p in es.points.indices) {
                val ep = es.points[p]
                val ap = asStroke.points[p]
                assertEquals(ep.x, ap.x, 0.1f)
                assertEquals(ep.y, ap.y, 0.1f)
                assertEquals(ep.pressure, ap.pressure, 0.001f)
                assertEquals(ep.tiltRadians, ap.tiltRadians, 0.001f)
                assertEquals(ep.orientationRadians, ap.orientationRadians, 0.001f)
                assertEquals(ep.timestampMillis, ap.timestampMillis)
            }
        }

        assertEquals(expected.textBlocks.size, actual.textBlocks.size)
        for (i in expected.textBlocks.indices) {
            val eb = expected.textBlocks[i]
            val ab = actual.textBlocks[i]
            assertEquals(eb.id, ab.id)
            assertEquals(eb.pageIndex, ab.pageIndex)
            assertEquals(eb.x, ab.x, 0.1f)
            assertEquals(eb.y, ab.y, 0.1f)
            assertEquals(eb.width, ab.width, 0.1f)
            assertEquals(eb.text, ab.text)
            assertEquals(eb.colorArgb, ab.colorArgb)
            assertEquals(eb.fontSizeSp, ab.fontSizeSp, 0.1f)
        }
    }
}
