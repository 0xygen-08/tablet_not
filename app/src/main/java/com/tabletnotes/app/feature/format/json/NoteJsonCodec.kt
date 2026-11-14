package com.tabletnotes.app.feature.format.json

import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Hata ayıklama (Debug) ve inceleme amacıyla bir [NoteDocument] nesnesini
 * insan tarafından okunabilir JSON metnine dönüştüren saf Kotlin (Pure Kotlin) yardımcı sınıfı.
 * Android `org.json` bağımlılığı olmadığı için hem cihazda hem de JVM birim testlerinde doğrudan çalışır.
 */
object NoteJsonCodec {

    fun encodeToJsonString(
        document: NoteDocument,
        @Suppress("UNUSED_PARAMETER") indentSpaces: Int = 2
    ): String = buildString(1024) {
        appendLine("{")
        append("  \"formatMagic\": \"TNOT\",\n")
        append("  \"formatVersion\": ${NoteBinaryCodec.CURRENT_FORMAT_VERSION},\n")
        append("  \"id\": \"${escapeJson(document.id)}\",\n")
        append("  \"title\": \"${escapeJson(document.title)}\",\n")
        append("  \"createdAtMillis\": ${document.createdAtMillis},\n")
        append("  \"updatedAtMillis\": ${document.updatedAtMillis},\n")

        // Pages
        append("  \"pages\": [")
        if (document.pages.isEmpty()) {
            appendLine("],")
        } else {
            appendLine()
            document.pages.forEachIndexed { idx, page ->
                val comma = if (idx < document.pages.lastIndex) "," else ""
                appendLine(
                    "    { \"pageIndex\": ${page.pageIndex}, " +
                        "\"widthPt\": ${fmt1(page.widthPt)}, " +
                        "\"heightPt\": ${fmt1(page.heightPt)} }$comma"
                )
            }
            appendLine("  ],")
        }

        // Strokes
        append("  \"strokes\": [")
        if (document.strokes.isEmpty()) {
            appendLine("],")
        } else {
            appendLine()
            document.strokes.forEachIndexed { sIdx, stroke ->
                val sComma = if (sIdx < document.strokes.lastIndex) "," else ""
                val colorHex = String.format(Locale.US, "#%08X", stroke.colorArgb)
                appendLine("    {")
                appendLine("      \"id\": \"${escapeJson(stroke.id)}\",")
                appendLine("      \"pageIndex\": ${stroke.pageIndex},")
                appendLine("      \"tool\": \"${stroke.tool.name}\",")
                appendLine("      \"colorArgb\": \"$colorHex\",")
                appendLine("      \"thickness\": ${fmt2(stroke.thickness)},")
                append("      \"points\": [")
                if (stroke.points.isEmpty()) {
                    appendLine("]")
                } else {
                    appendLine()
                    stroke.points.forEachIndexed { pIdx, pt ->
                        val pComma = if (pIdx < stroke.points.lastIndex) "," else ""
                        append(
                            "        { \"x\": ${fmt1(pt.x)}, \"y\": ${fmt1(pt.y)}, " +
                                "\"p\": ${fmt3(pt.pressure)}, " +
                                "\"tilt\": ${fmt3(pt.tiltRadians)}, " +
                                "\"orient\": ${fmt3(pt.orientationRadians)}, " +
                                "\"t\": ${pt.timestampMillis} }$pComma\n"
                        )
                    }
                    appendLine("      ]")
                }
                appendLine("    }$sComma")
            }
            appendLine("  ],")
        }

        // TextBlocks
        append("  \"textBlocks\": [")
        if (document.textBlocks.isEmpty()) {
            appendLine("]")
        } else {
            appendLine()
            document.textBlocks.forEachIndexed { tIdx, block ->
                val tComma = if (tIdx < document.textBlocks.lastIndex) "," else ""
                val colorHex = String.format(Locale.US, "#%08X", block.colorArgb)
                appendLine(
                    "    { \"id\": \"${escapeJson(block.id)}\", " +
                        "\"pageIndex\": ${block.pageIndex}, " +
                        "\"x\": ${fmt1(block.x)}, \"y\": ${fmt1(block.y)}, " +
                        "\"width\": ${fmt1(block.width)}, " +
                        "\"text\": \"${escapeJson(block.text)}\", " +
                        "\"colorArgb\": \"$colorHex\", " +
                        "\"fontSizeSp\": ${fmt1(block.fontSizeSp)} }$tComma"
                )
            }
            appendLine("  ]")
        }

        append("}")
    }

    private fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", (v * 10f).roundToInt() / 10.0)
    private fun fmt2(v: Float): String = String.format(Locale.US, "%.2f", (v * 100f).roundToInt() / 100.0)
    private fun fmt3(v: Float): String = String.format(Locale.US, "%.3f", (v * 1000f).roundToInt() / 1000.0)

    private fun escapeJson(raw: String): String = buildString(raw.length + 8) {
        for (ch in raw) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
