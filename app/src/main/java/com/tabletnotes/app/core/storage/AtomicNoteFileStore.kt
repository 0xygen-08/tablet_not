package com.tabletnotes.app.core.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LowStorageException(message: String) : IOException(message)

/**
 * Not verilerini (`.tnote`) ve küçük önizleme resimlerini (`.png`) cihaz depolamasına
 * **Atomik Yazma (`temp file -> fsync -> atomic rename`)** yöntemiyle kaydeden sınıf.
 *
 * Neden Atomik Yazma?
 * Kayıt esnasında uygulama çökerse veya tabletin şarjı biterse, yarım kalan yazma işlemi
 * yalnızca `.tmp` geçici dosyasında kalır; kullanıcının önceki sağlam `.tnote` dosyası asla bozulmaz.
 */
class AtomicNoteFileStore(
    private val rootDir: File,
    private val minRequiredFreeBytes: Long = 2L * 1024L * 1024L // En az 2 MB boş alan koruması
) {
    private val notesDir = File(rootDir, "notes").apply { mkdirs() }
    private val thumbnailsDir = File(rootDir, "thumbnails").apply { mkdirs() }

    fun getNoteFile(noteId: String): File = File(notesDir, "${sanitizeId(noteId)}.tnote")

    fun getThumbnailFile(noteId: String): File = File(thumbnailsDir, "${sanitizeId(noteId)}.png")

    /**
     * Verilen bayt dizisini önce `<noteId>.tnote.tmp` dosyasına yazar, diske senkronize eder (`fd.sync()`)
     * ve ardından atomik olarak `<noteId>.tnote` dosyasının üzerine taşır.
     */
    @Synchronized
    fun writeNoteBytesAtomically(noteId: String, data: ByteArray): File {
        val targetFile = getNoteFile(noteId)
        writeBytesAtomically(targetFile, data)
        return targetFile
    }

    @Synchronized
    fun writeThumbnailBytesAtomically(noteId: String, pngBytes: ByteArray): File {
        val targetFile = getThumbnailFile(noteId)
        writeBytesAtomically(targetFile, pngBytes)
        return targetFile
    }

    fun readNoteBytes(noteId: String): ByteArray {
        val file = getNoteFile(noteId)
        if (!file.exists()) {
            throw IOException("Not dosyası bulunamadı: ${file.absolutePath}")
        }
        return file.readBytes()
    }

    fun deleteNoteFiles(noteId: String) {
        val safeId = sanitizeId(noteId)
        File(notesDir, "$safeId.tnote").delete()
        File(notesDir, "$safeId.tnote.tmp").delete()
        File(thumbnailsDir, "$safeId.png").delete()
        File(thumbnailsDir, "$safeId.png.tmp").delete()
    }

    private fun writeBytesAtomically(targetFile: File, data: ByteArray) {
        val parent = targetFile.parentFile ?: rootDir
        if (!parent.exists()) {
            parent.mkdirs()
        }

        val usable = parent.usableSpace
        val required = minRequiredFreeBytes + (data.size * 2L)
        if (usable in 1 until required) {
            throw LowStorageException("Cihazda yeterli depolama alanı yok (Boş: $usable bayt).")
        }

        val tempFile = File(parent, "${targetFile.name}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                // İşletim sistemi tamponunu fiziksel diske zorla (crash güvenliği)
                runCatching { fos.fd.sync() }
            }

            // Atomik yeniden adlandırma (POSIX üzerinde mevcut dosyayı tek adımda değiştirir)
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun sanitizeId(rawId: String): String {
        return rawId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}

