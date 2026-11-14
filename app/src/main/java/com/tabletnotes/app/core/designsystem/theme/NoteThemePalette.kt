package com.tabletnotes.app.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R

/**
 * Uygulamadaki tüm görsel öğelerin renk, kareli kağıt dokusu ve köşe yuvarlatma değerlerini
 * tek bir merkezden yöneten tema veri sınıfı.
 *
 * Yeni bir tasarım/tema eklemek veya mevcut temayı değiştirmek için sadece bu sınıftan
 * yeni bir nesne tanımlamak yeterlidir; ekran kodlarına dokunmaya gerek kalmaz.
 */
@Immutable
data class NoteThemePalette(
    val id: ThemePreset,
    @StringRes val displayNameRes: Int,
    val isDark: Boolean,

    // Ana Arka Plan ve Yüzey Renkleri
    val appBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInteractive: Color,
    val border: Color,
    val borderSubtle: Color,

    // Defter / Tuval (Canvas) ve Kareli Kağıt (Graph Grid) Renkleri
    val canvasBackground: Color,
    val canvasMinorGrid: Color,
    val canvasMajorGrid: Color,
    val canvasGridSpacingDp: Dp = 28.dp,

    // Metin Renkleri
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Vurgu (Accent) ve Varsayılan Kalem Renkleri
    val primaryAccent: Color,
    val onPrimaryAccent: Color,
    val secondaryAccent: Color,
    val statusReadyEmerald: Color,
    val inkCoralRed: Color,
    val inkElectricBlue: Color,
    val inkHighlighterYellow: Color,

    // Köşe Yuvarlatmaları (Stitch tasarımındaki 'squircle' 18dp-22dp yapısı)
    val cardCornerRadius: Dp = 18.dp,
    val pillCornerRadius: Dp = 999.dp
)

enum class ThemePreset {
    STITCH_CHARCOAL_DARK,
    WARM_PAPER_LIGHT,
    MIDNIGHT_OLED;

    fun next(): ThemePreset {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * 1. Varsayılan Tema: Stitch Minimalist Tablet Tasarımı (Charcoal 950-100 + Kareli Mühendislik Defteri)
 * Kaynak: stitch_minimalist_social_notes_tablet.zip
 */
val StitchCharcoalPalette = NoteThemePalette(
    id = ThemePreset.STITCH_CHARCOAL_DARK,
    displayNameRes = R.string.theme_stitch_charcoal,
    isDark = true,
    appBackground = Color(0xFF0D0E11),       // charcoal-950
    surface = Color(0xFF131418),             // charcoal-900
    surfaceElevated = Color(0xFF181A20),     // charcoal-850
    surfaceInteractive = Color(0xFF20232A),  // charcoal-800
    border = Color(0xFF2D313A),              // charcoal-700
    borderSubtle = Color(0xFF1F232B),
    canvasBackground = Color(0xFF111215),    // notebook background
    canvasMinorGrid = Color(0xFF1F232B),     // 32px minor grid
    canvasMajorGrid = Color(0xFF2A2F3A),     // 160px major grid
    canvasGridSpacingDp = 28.dp,
    textPrimary = Color(0xFFF1F3F7),         // charcoal-100
    textSecondary = Color(0xFFD1D5DB),       // charcoal-200
    textMuted = Color(0xFF8C93A4),           // charcoal-400
    primaryAccent = Color(0xFF3B82F6),       // Stitch chat accent blue
    onPrimaryAccent = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFFEF5350),     // Stitch vector noteRed
    statusReadyEmerald = Color(0xFF34D399),  // emerald-400
    inkCoralRed = Color(0xFFEF5350),
    inkElectricBlue = Color(0xFF60A5FA),
    inkHighlighterYellow = Color(0x66FACC15)
)

/**
 * 2. Alternatif Tema: Sıcak Kağıt (Açık / Gündüz Okuma Modu)
 * Tek tıkla veya ayarlardan değiştirilebilir.
 */
val WarmPaperPalette = NoteThemePalette(
    id = ThemePreset.WARM_PAPER_LIGHT,
    displayNameRes = R.string.theme_warm_paper,
    isDark = false,
    appBackground = Color(0xFFF4F1EA),
    surface = Color(0xFFFAF8F5),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceInteractive = Color(0xFFEBE6DC),
    border = Color(0xFFD6CFC2),
    borderSubtle = Color(0xFFE5DFD3),
    canvasBackground = Color(0xFFFDFBF7),
    canvasMinorGrid = Color(0xFFE6E0D4),
    canvasMajorGrid = Color(0xFFD2C9B8),
    canvasGridSpacingDp = 28.dp,
    textPrimary = Color(0xFF1C1E21),
    textSecondary = Color(0xFF3E434C),
    textMuted = Color(0xFF6E7582),
    primaryAccent = Color(0xFF2563EB),
    onPrimaryAccent = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFFD9383A),
    statusReadyEmerald = Color(0xFF059669),
    inkCoralRed = Color(0xFFD9383A),
    inkElectricBlue = Color(0xFF1D4ED8),
    inkHighlighterYellow = Color(0x66FDE047)
)

/**
 * 3. Alternatif Tema: Gece Siyahı (AMOLED Tabletler için Maksimum Pil Tasarrufu)
 */
val MidnightOledPalette = NoteThemePalette(
    id = ThemePreset.MIDNIGHT_OLED,
    displayNameRes = R.string.theme_midnight_oled,
    isDark = true,
    appBackground = Color(0xFF000000),
    surface = Color(0xFF0A0B0E),
    surfaceElevated = Color(0xFF12141A),
    surfaceInteractive = Color(0xFF1A1D26),
    border = Color(0xFF262A36),
    borderSubtle = Color(0xFF151820),
    canvasBackground = Color(0xFF050507),
    canvasMinorGrid = Color(0xFF14171F),
    canvasMajorGrid = Color(0xFF222634),
    canvasGridSpacingDp = 28.dp,
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF64748B),
    primaryAccent = Color(0xFF60A5FA),
    onPrimaryAccent = Color(0xFF050507),
    secondaryAccent = Color(0xFFF87171),
    statusReadyEmerald = Color(0xFF34D399),
    inkCoralRed = Color(0xFFF87171),
    inkElectricBlue = Color(0xFF38BDF8),
    inkHighlighterYellow = Color(0x66FACC15)
)

fun ThemePreset.toPalette(): NoteThemePalette = when (this) {
    ThemePreset.STITCH_CHARCOAL_DARK -> StitchCharcoalPalette
    ThemePreset.WARM_PAPER_LIGHT -> WarmPaperPalette
    ThemePreset.MIDNIGHT_OLED -> MidnightOledPalette
}

val LocalNoteThemePalette = staticCompositionLocalOf { StitchCharcoalPalette }
