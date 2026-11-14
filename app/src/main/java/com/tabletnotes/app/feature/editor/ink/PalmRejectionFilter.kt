package com.tabletnotes.app.feature.editor.ink

import android.view.MotionEvent

/**
 * Bir dokunma/kalem olayının (MotionEvent) editörde nasıl işleneceğini belirten karar sonucu.
 */
enum class InputRouteDecision {
    /** Çizim veya çizgi silgisi olarak işle (Kalem veya izin verilen tek parmak/fare). */
    DRAW_OR_ERASE,

    /** Donanım kalem silgisi (kalemin arka ucu veya gövde tuşu) ile doğrudan silgi olarak işle. */
    HARDWARE_STYLUS_ERASER,

    /** Tuvali kaydırma / yakınlaştırma (Pan / Zoom) hareketi olarak işle. */
    PAN_ZOOM,

    /** Avuç içi (Palm) temasını yok say ve devam eden hatalı parmak çizimini iptal et. */
    REJECTED_PALM
}

/**
 * Tablet ekranlarında yazı yazarken avuç içinin (palm) ekrana değmesiyle oluşan
 * istenmeyen çizgileri ve kaymaları engelleyen filtre.
 */
class PalmRejectionFilter(
    private val stylusGuardWindowMillis: Long = 260L,
    private val palmTouchMajorThresholdPx: Float = 115f
) {
    private var isStylusCurrentlyDown: Boolean = false
    private var lastStylusActivityTimeMillis: Long = -10_000L

    fun onHoverEvent(event: MotionEvent) {
        val toolType = event.getToolType(0)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            lastStylusActivityTimeMillis = event.eventTime
        }
    }

    fun evaluate(
        event: MotionEvent,
        stylusOnlyMode: Boolean
    ): InputRouteDecision {
        if ((event.flags and MotionEvent.FLAG_CANCELED) != 0 ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            isStylusCurrentlyDown = false
            return InputRouteDecision.REJECTED_PALM
        }

        // Aktif işaretçiler (pointer) arasında stylus var mı kontrol et
        var stylusPointerIndex = -1
        for (i in 0 until event.pointerCount) {
            val type = event.getToolType(i)
            if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) {
                stylusPointerIndex = i
                break
            }
        }

        if (stylusPointerIndex >= 0) {
            val toolType = event.getToolType(stylusPointerIndex)
            val buttonState = event.buttonState
            lastStylusActivityTimeMillis = event.eventTime
            isStylusCurrentlyDown = event.actionMasked != MotionEvent.ACTION_UP &&
                event.actionMasked != MotionEvent.ACTION_POINTER_UP

            val isEraserTailOrButton = toolType == MotionEvent.TOOL_TYPE_ERASER ||
                (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

            return if (isEraserTailOrButton) {
                InputRouteDecision.HARDWARE_STYLUS_ERASER
            } else {
                InputRouteDecision.DRAW_OR_ERASE
            }
        }

        // Buraya gelindiyse olay parmak (TOOL_TYPE_FINGER) veya fare (TOOL_TYPE_MOUSE) kaynaklıdır.
        val timeSinceStylus = event.eventTime - lastStylusActivityTimeMillis
        if (isStylusCurrentlyDown || timeSinceStylus in 0..stylusGuardWindowMillis) {
            // Kalem ekranda aktifken veya yeni kalkmışken değen el/avuç içi reddedilir.
            return InputRouteDecision.REJECTED_PALM
        }

        val primaryToolType = event.getToolType(0)
        if (primaryToolType == MotionEvent.TOOL_TYPE_FINGER) {
            val touchMajor = event.getTouchMajor(0)
            if (touchMajor > palmTouchMajorThresholdPx) {
                // Geniş temas alanı (avuç içi / bilek teması) reddedilir.
                return InputRouteDecision.REJECTED_PALM
            }
        }

        // Çoklu parmak (2+ parmak) her zaman Zoom / Pan yapar
        if (event.pointerCount >= 2) {
            return InputRouteDecision.PAN_ZOOM
        }

        // "Sadece Kalem" (Stylus Only) modu açıksa tek parmak çizim yapmaz, sayfayı kaydırır (Pan).
        // Emülatörde fare (TOOL_TYPE_MOUSE) ile test kolaylığı için fare her zaman çizim yapabilir.
        if (stylusOnlyMode && primaryToolType == MotionEvent.TOOL_TYPE_FINGER) {
            return InputRouteDecision.PAN_ZOOM
        }

        return InputRouteDecision.DRAW_OR_ERASE
    }
}
