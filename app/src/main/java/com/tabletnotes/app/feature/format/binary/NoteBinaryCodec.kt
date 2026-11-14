package com.tabletnotes.app.feature.format.binary

import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

class InvalidNoteFormatException(message: String) : IllegalArgumentException(message)

/**
 * Özel Kompakt Binary Vektör Not Formatı (`.tnote`).
 *
 * Başlık (Header - 7 Bayt, sıkıştırılmamış):
 * - [0..3] : Magic Bytes `"TNOT"` (`0x54, 0x4E, 0x4F, 0x54`)
 * - [4..5] : Format Sürüm Numarası (`uint16`, şu an `1`) — eski notları bozmadan evrimleşebilir.
 * - [6]    : Sıkıştırma Bayrağı (`1 = GZIP`, `0 = Sıkıştırmasız`)
 *
 * Gövde (Payload - GZIP sıkıştırmalı):
 * - Delta kodlamalı koordinatlar (`Δx, Δy`, `0.1f` hassasiyetle sabit nokta tam sayı)
 * - Kuantize basınç (`0..1000`, `Δpressure`) ve eğim (`0.001 rad`, `Δtilt, Δorientation`)
 * - ZigZag + Değişken Uzunluklu Tam Sayılar (LEB128 VarInt / VarLong)
 */
object NoteBinaryCodec {

    private val MAGIC_BYTES = byteArrayOf(
        'T'.code.toByte(),
        'N'.code.toByte(),
        'O'.code.toByte(),
        'T'.code.toByte()
    )

    const val CURRENT_FORMAT_VERSION: Int = 1
    private const val FLAG_UNCOMPRESSED: Int = 0
    private const val FLAG_GZIP_COMPRESSED: Int = 1

    private const val COORD_SCALE = 10f        // 0.1f hassasiyet
    private const val THICKNESS_SCALE = 100f   // 0.01f hassasiyet
    private const val PRESSURE_SCALE = 1000f   // 0..1000 (10-bit) kuantizasyon
    private const val ANGLE_SCALE = 1000f      // miliradyan hassasiyet

    /**
     * [NoteDocument] nesnesini `.tnote` binary bayt dizisine kodlar.
     */
    fun encode(document: NoteDocument, compress: Boolean = true): ByteArray {
        val rawOut = ByteArrayOutputStream(4096)
        encodeToStream(document, rawOut, compress)
        return rawOut.toByteArray()
    }

    fun encodeToStream(document: NoteDocument, output: OutputStream, compress: Boolean = true) {
        // 1. Açık (sıkıştırılmamış) 7 baytlık başlığı yaz
        output.write(MAGIC_BYTES)
        output.write((CURRENT_FORMAT_VERSION ushr 8) and 0xFF)
        output.write(CURRENT_FORMAT_VERSION and 0xFF)
        output.write(if (compress) FLAG_GZIP_COMPRESSED else FLAG_UNCOMPRESSED)

        // 2. Gövdeyi GZIP (veya düz akış) ile yaz
        if (compress) {
            GZIPOutputStream(output).use { gzipOut ->
                writePayload(document, DataOutputStream(gzipOut))
            }
        } else {
            writePayload(document, DataOutputStream(output))
        }
    }

    /**
     * `.tnote` binary bayt dizisini tekrar [NoteDocument] nesnesine çözer.
     */
    fun decode(bytes: ByteArray): NoteDocument {
        return decodeFromStream(ByteArrayInputStream(bytes))
    }

    fun decodeFromStream(input: InputStream): NoteDocument {
        val magic = ByteArray(4)
        readFullyOrThrow(input, magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw InvalidNoteFormatException("Geçersiz dosya başlığı (TNOT magic bytes bulunamadı).")
        }

        val hi = input.read()
        val lo = input.read()
        val compressionFlag = input.read()
        if (hi < 0 || lo < 0 || compressionFlag < 0) {
            throw InvalidNoteFormatException("Bozuk veya eksik .tnote başlığı.")
        }

        val version = (hi shl 8) or lo
        if (version < 1 || version > CURRENT_FORMAT_VERSION) {
            throw InvalidNoteFormatException("Desteklenmeyen .tnote format sürümü: v$version")
        }

        return when (compressionFlag) {
            FLAG_GZIP_COMPRESSED -> GZIPInputStream(input).use { gzipIn ->
                readPayload(version, DataInputStream(gzipIn))
            }
            FLAG_UNCOMPRESSED -> readPayload(version, DataInputStream(input))
            else -> throw InvalidNoteFormatException("Bilinmeyen sıkıştırma bayrağı: $compressionFlag")
        }
    }

    private fun writePayload(doc: NoteDocument, out: DataOutputStream) {
        writeString(out, doc.id)
        writeString(out, doc.title)
        writeUVarLong(out, doc.createdAtMillis.coerceAtLeast(0L))
        writeUVarLong(out, doc.updatedAtMillis.coerceAtLeast(0L))

        // Sayfalar (Pages)
        writeUVarInt(out, doc.pages.size)
        for (page in doc.pages) {
            writeUVarInt(out, page.pageIndex.coerceAtLeast(0))
            writeUVarInt(out, (page.widthPt * COORD_SCALE).roundToInt().coerceAtLeast(1))
            writeUVarInt(out, (page.heightPt * COORD_SCALE).roundToInt().coerceAtLeast(1))
        }

        // Çizgiler (Strokes) — Delta Kodlamalı Noktalar
        writeUVarInt(out, doc.strokes.size)
        for (stroke in doc.strokes) {
            writeString(out, stroke.id)
            writeUVarInt(out, stroke.pageIndex.coerceAtLeast(0))
            out.writeByte(stroke.tool.wireId)
            out.writeInt(stroke.colorArgb)
            writeUVarInt(out, (stroke.thickness * THICKNESS_SCALE).roundToInt().coerceAtLeast(1))

            val points = stroke.points
            writeUVarInt(out, points.size)

            var prevQx = 0
            var prevQy = 0
            var prevQp = 500
            var prevQtilt = 0
            var prevQorient = 0
            var prevTime = 0L

            for (pt in points) {
                val qx = (pt.x * COORD_SCALE).roundToInt()
                val qy = (pt.y * COORD_SCALE).roundToInt()
                val qp = (pt.pressure.coerceIn(0f, 1f) * PRESSURE_SCALE).roundToInt()
                val qtilt = (pt.tiltRadians * ANGLE_SCALE).roundToInt()
                val qorient = (pt.orientationRadians * ANGLE_SCALE).roundToInt()
                val t = pt.timestampMillis

                writeZigZagVarInt(out, qx - prevQx)
                writeZigZagVarInt(out, qy - prevQy)
                writeZigZagVarInt(out, qp - prevQp)
                writeZigZagVarInt(out, qtilt - prevQtilt)
                writeZigZagVarInt(out, qorient - prevQorient)
                writeZigZagVarLong(out, t - prevTime)

                prevQx = qx
                prevQy = qy
                prevQp = qp
                prevQtilt = qtilt
                prevQorient = qorient
                prevTime = t
            }
        }

        // Metin Kutuları (Text Blocks)
        writeUVarInt(out, doc.textBlocks.size)
        for (block in doc.textBlocks) {
            writeString(out, block.id)
            writeUVarInt(out, block.pageIndex.coerceAtLeast(0))
            writeZigZagVarInt(out, (block.x * COORD_SCALE).roundToInt())
            writeZigZagVarInt(out, (block.y * COORD_SCALE).roundToInt())
            writeUVarInt(out, (block.width * COORD_SCALE).roundToInt().coerceAtLeast(10))
            writeString(out, block.text)
            out.writeInt(block.colorArgb)
            writeUVarInt(out, (block.fontSizeSp * COORD_SCALE).roundToInt().coerceAtLeast(10))
        }
    }

    private fun readPayload(
        @Suppress("UNUSED_PARAMETER") version: Int,
        inp: DataInputStream
    ): NoteDocument {
        val id = readString(inp)
        val title = readString(inp)
        val createdAtMillis = readUVarLong(inp)
        val updatedAtMillis = readUVarLong(inp)

        val pageCount = readUVarInt(inp)
        val pages = ArrayList<NotePage>(pageCount)
        for (i in 0 until pageCount) {
            val pageIndex = readUVarInt(inp)
            val widthPt = readUVarInt(inp) / COORD_SCALE
            val heightPt = readUVarInt(inp) / COORD_SCALE
            pages.add(NotePage(pageIndex = pageIndex, widthPt = widthPt, heightPt = heightPt))
        }

        val strokeCount = readUVarInt(inp)
        val strokes = ArrayList<NoteStroke>(strokeCount)
        for (i in 0 until strokeCount) {
            val strokeId = readString(inp)
            val pageIndex = readUVarInt(inp)
            val tool = DrawingToolType.fromWireId(inp.readUnsignedByte())
            val colorArgb = inp.readInt()
            val thickness = readUVarInt(inp) / THICKNESS_SCALE

            val pointCount = readUVarInt(inp)
            val points = ArrayList<StrokePoint>(pointCount)

            var qx = 0
            var qy = 0
            var qp = 500
            var qtilt = 0
            var qorient = 0
            var time = 0L

            for (p in 0 until pointCount) {
                qx += readZigZagVarInt(inp)
                qy += readZigZagVarInt(inp)
                qp += readZigZagVarInt(inp)
                qtilt += readZigZagVarInt(inp)
                qorient += readZigZagVarInt(inp)
                time += readZigZagVarLong(inp)

                points.add(
                    StrokePoint(
                        x = qx / COORD_SCALE,
                        y = qy / COORD_SCALE,
                        pressure = (qp / PRESSURE_SCALE).coerceIn(0f, 1f),
                        tiltRadians = qtilt / ANGLE_SCALE,
                        orientationRadians = qorient / ANGLE_SCALE,
                        timestampMillis = time
                    )
                )
            }

            strokes.add(
                NoteStroke(
                    id = strokeId,
                    pageIndex = pageIndex,
                    tool = tool,
                    colorArgb = colorArgb,
                    thickness = thickness,
                    points = points
                )
            )
        }

        val textBlockCount = readUVarInt(inp)
        val textBlocks = ArrayList<TextBlock>(textBlockCount)
        for (i in 0 until textBlockCount) {
            val blockId = readString(inp)
            val pageIndex = readUVarInt(inp)
            val x = readZigZagVarInt(inp) / COORD_SCALE
            val y = readZigZagVarInt(inp) / COORD_SCALE
            val width = readUVarInt(inp) / COORD_SCALE
            val text = readString(inp)
            val colorArgb = inp.readInt()
            val fontSizeSp = readUVarInt(inp) / COORD_SCALE
            textBlocks.add(
                TextBlock(
                    id = blockId,
                    pageIndex = pageIndex,
                    x = x,
                    y = y,
                    width = width,
                    text = text,
                    colorArgb = colorArgb,
                    fontSizeSp = fontSizeSp
                )
            )
        }

        return NoteDocument(
            id = id,
            title = title,
            pages = pages,
            strokes = strokes,
            textBlocks = textBlocks,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    // --- Değişken Uzunluklu Tam Sayı (LEB128 VarInt & ZigZag) Yardımcıları ---

    internal fun writeUVarInt(out: OutputStream, value: Int) {
        var v = value
        while ((v and 0xFFFFFF80.toInt()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    internal fun readUVarInt(inp: InputStream): Int {
        var result = 0
        var shift = 0
        while (shift < 35) {
            val b = inp.read()
            if (b < 0) throw EOFException("Beklenmeyen akış sonu (UVarInt).")
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw InvalidNoteFormatException("Bozuk UVarInt verisi.")
    }

    internal fun writeZigZagVarInt(out: OutputStream, value: Int) {
        val zigZag = (value shl 1) xor (value shr 31)
        writeUVarInt(out, zigZag)
    }

    internal fun readZigZagVarInt(inp: InputStream): Int {
        val raw = readUVarInt(inp)
        return (raw ushr 1) xor -(raw and 1)
    }

    internal fun writeUVarLong(out: OutputStream, value: Long) {
        var v = value
        while ((v and -0x80L) != 0L) {
            out.write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
        out.write(v.toInt() and 0x7F)
    }

    internal fun readUVarLong(inp: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift < 70) {
            val b = inp.read()
            if (b < 0) throw EOFException("Beklenmeyen akış sonu (UVarLong).")
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw InvalidNoteFormatException("Bozuk UVarLong verisi.")
    }

    internal fun writeZigZagVarLong(out: OutputStream, value: Long) {
        val zigZag = (value shl 1) xor (value shr 63)
        writeUVarLong(out, zigZag)
    }

    internal fun readZigZagVarLong(inp: InputStream): Long {
        val raw = readUVarLong(inp)
        return (raw ushr 1) xor -(raw and 1L)
    }

    private fun writeString(out: OutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeUVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(inp: InputStream): String {
        val len = readUVarInt(inp)
        if (len == 0) return ""
        if (len < 0 || len > 10_000_000) {
            throw InvalidNoteFormatException("Geçersiz metin uzunluğu: $len")
        }
        val bytes = ByteArray(len)
        readFullyOrThrow(inp, bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readFullyOrThrow(inp: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = inp.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw EOFException("Beklenmeyen dosya sonu.")
            offset += read
        }
    }
}

