package com.tabletnotes.app.feature.chat.friends

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tabletnotes.app.domain.model.UsernameValidator

object QrCodeHelper {

    fun buildQrPayload(username: String): String {
        val clean = UsernameValidator.normalize(username)
        return "@$clean"
    }

    fun generateUsernameQrBitmap(username: String, sizePx: Int = 512): Bitmap? {
        val clean = UsernameValidator.normalize(username)
        if (!UsernameValidator.isValid(clean)) return null
        val payload = buildQrPayload(clean)
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    /**
     * QR kodundan gelen metni ayrıştırıp geçerli kullanıcı adını döndürür;
     * geçersizse null döner. Desteklenen biçimler:
     * "@ahmet_yilmaz", "ahmet_yilmaz", "tabletnotes://user/ahmet_yilmaz"
     */
    fun parseScannedUsername(rawValue: String?): String? {
        if (rawValue.isNullOrBlank()) return null
        var candidate = rawValue.trim()
        if (candidate.startsWith("tabletnotes://", ignoreCase = true)) {
            candidate = candidate.substringAfterLast("/")
        }
        val normalized = UsernameValidator.normalize(candidate)
        return normalized.takeIf { UsernameValidator.isValid(it) }
    }
}
