package com.tabletnotes.app.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabletnotes.app.R

/**
 * ============================================================================
 * TEK MERKEZLİ TASARIM SİSTEMİ (SINGLE UNIFIED THEME FILE — Theme.kt)
 * ============================================================================
 * Uygulamadaki tüm renkler, 3 görsel tema (Kağıt / Açık, Gece / Koyu, Pastel),
 * sistem teması takibi, ders klasörü pastel renkleri, bağımsız kağıt renkleri,
 * köşe yuvarlatmaları (24dp kartlar, tam yuvarlak pill butonlar) ve Inter
 * tipografisi kolayca özelleştirilebilmesi için tek bu dosyada toplanmıştır.
 */

enum class ThemePreset(@StringRes val titleRes: Int) {
    SYSTEM_AUTO(R.string.theme_system_auto),
    NIGHT_DARK(R.string.theme_stitch_charcoal),
    PAPER_LIGHT(R.string.theme_warm_paper),
    PASTEL_SOFT(R.string.theme_midnight_oled);

    fun next(): ThemePreset {
        val selectable = listOf(NIGHT_DARK, PAPER_LIGHT, PASTEL_SOFT, SYSTEM_AUTO)
        val idx = selectable.indexOf(this).coerceAtLeast(0)
        return selectable[(idx + 1) % selectable.size]
    }

    companion object {
        // Geriye dönük uyumluluk sabitleri
        val STITCH_CHARCOAL_DARK = NIGHT_DARK
        val WARM_PAPER_LIGHT = PAPER_LIGHT
        val MIDNIGHT_OLED = PASTEL_SOFT
    }
}

/**
 * Her ders klasörü için nokta (dot) rengi ve Pastel kart arka plan rengi.
 * Taslak görsellerdeki Fizik (Mavi), Kimya (Yeşil), Mat. (Mor), Tarih (Turuncu), İng. (Pembe)
 * renkleriyle birebir uyumludur.
 */
@Immutable
data class CourseFolderColorStyle(
    val courseKey: String,
    val dotLight: Color,
    val dotDark: Color,
    val pastelSurfaceLight: Color,
    val pastelSurfaceDark: Color
)

object CourseFolderPalette {
    val PhysicsBlue = CourseFolderColorStyle(
        courseKey = "fizik",
        dotLight = Color(0xFF2B50EC),
        dotDark = Color(0xFF7B96FF),
        pastelSurfaceLight = Color(0xFFDCE7FE),
        pastelSurfaceDark = Color(0xFF1A2238)
    )
    val ChemistryGreen = CourseFolderColorStyle(
        courseKey = "kimya",
        dotLight = Color(0xFF16794B),
        dotDark = Color(0xFF5CE09B),
        pastelSurfaceLight = Color(0xFFD8F3E5),
        pastelSurfaceDark = Color(0xFF162924)
    )
    val MathPurple = CourseFolderColorStyle(
        courseKey = "mat",
        dotLight = Color(0xFF6B38E8),
        dotDark = Color(0xFFC49BFF),
        pastelSurfaceLight = Color(0xFFEAE0FF),
        pastelSurfaceDark = Color(0xFF241D38)
    )
    val HistoryOrange = CourseFolderColorStyle(
        courseKey = "tarih",
        dotLight = Color(0xFF944E12),
        dotDark = Color(0xFFFA984C),
        pastelSurfaceLight = Color(0xFFFDE6CC),
        pastelSurfaceDark = Color(0xFF2D221A)
    )
    val EnglishPink = CourseFolderColorStyle(
        courseKey = "ing",
        dotLight = Color(0xFFA82258),
        dotDark = Color(0xFFF57CB8),
        pastelSurfaceLight = Color(0xFFFCDCE8),
        pastelSurfaceDark = Color(0xFF2E1C28)
    )
    val BiologyTeal = CourseFolderColorStyle(
        courseKey = "biyoloji",
        dotLight = Color(0xFF0E7490),
        dotDark = Color(0xFF38BDF8),
        pastelSurfaceLight = Color(0xFFD8F3FA),
        pastelSurfaceDark = Color(0xFF162832)
    )

    val allStyles = listOf(
        PhysicsBlue,
        ChemistryGreen,
        MathPurple,
        HistoryOrange,
        EnglishPink,
        BiologyTeal
    )

    fun styleForCourse(courseName: String, indexHint: Int = 0): CourseFolderColorStyle {
        val norm = courseName.trim().lowercase()
        return when {
            norm.contains("fizik") || norm.contains("phys") -> PhysicsBlue
            norm.contains("kimya") || norm.contains("chem") -> ChemistryGreen
            norm.contains("mat") || norm.contains("geometri") -> MathPurple
            norm.contains("tarih") || norm.contains("hist") || norm.contains("coğ") -> HistoryOrange
            norm.contains("ing") || norm.contains("eng") || norm.contains("edeb") -> EnglishPink
            norm.contains("biyo") || norm.contains("bio") -> BiologyTeal
            else -> allStyles[indexHint.coerceAtLeast(0) % allStyles.size]
        }
    }
}

@Immutable
data class NoteThemePalette(
    val id: ThemePreset,
    @StringRes val displayNameRes: Int,
    val isDark: Boolean,
    val usePastelFolderCards: Boolean,

    // Ana Arka Plan ve Yüzey Renkleri
    val appBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInteractive: Color,
    val border: Color,
    val borderSubtle: Color,

    // Varsayılan Tuval (Canvas) Renkleri (Notun kendi bağımsız kağıt rengi seçilmediğinde)
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
    val inkDeepBlack: Color,
    val inkHighlighterYellow: Color,

    // Köşe Yuvarlatmaları (Tasarım sistemi: 24dp kartlar, tam yuvarlak pill bar ve butonlar)
    val cardCornerRadius: Dp = 24.dp,
    val fabCornerRadius: Dp = 20.dp,
    val pillCornerRadius: Dp = 999.dp
)

/**
 * 1. GECE (KOYU TEMA) — Taslak 1 ("Gece (koyu tema)")
 */
val NightDarkPalette = NoteThemePalette(
    id = ThemePreset.NIGHT_DARK,
    displayNameRes = R.string.theme_stitch_charcoal,
    isDark = true,
    usePastelFolderCards = false,
    appBackground = Color(0xFF0E1017),
    surface = Color(0xFF171A26),
    surfaceElevated = Color(0xFF1C2030),
    surfaceInteractive = Color(0xFF252A3D),
    border = Color(0xFF2A3045),
    borderSubtle = Color(0xFF202536),
    canvasBackground = Color(0xFFFAF7EE),
    canvasMinorGrid = Color(0xFFE2DDD0),
    canvasMajorGrid = Color(0xFFD0C9B8),
    textPrimary = Color(0xFFF4F6FB),
    textSecondary = Color(0xFFC8CEDA),
    textMuted = Color(0xFF8A93A6),
    primaryAccent = Color(0xFF8FA8FF),
    onPrimaryAccent = Color(0xFF0E1017),
    secondaryAccent = Color(0xFFE05252),
    statusReadyEmerald = Color(0xFF4ADE80),
    inkCoralRed = Color(0xFFE05252),
    inkElectricBlue = Color(0xFF2962FF),
    inkDeepBlack = Color(0xFF1A1C23),
    inkHighlighterYellow = Color(0x66FACC15),
    cardCornerRadius = 24.dp,
    fabCornerRadius = 20.dp
)

/**
 * 2. KAĞIT (AÇIK TEMA) — Taslak 2 ("Kağıt (açık tema)")
 */
val PaperLightPalette = NoteThemePalette(
    id = ThemePreset.PAPER_LIGHT,
    displayNameRes = R.string.theme_warm_paper,
    isDark = false,
    usePastelFolderCards = false,
    appBackground = Color(0xFFF5F4EF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceInteractive = Color(0xFFECEAE2),
    border = Color(0xFFE3E0D6),
    borderSubtle = Color(0xFFECEAE2),
    canvasBackground = Color(0xFFFAF7EE),
    canvasMinorGrid = Color(0xFFE2DDD0),
    canvasMajorGrid = Color(0xFFD0C9B8),
    textPrimary = Color(0xFF171923),
    textSecondary = Color(0xFF4A5060),
    textMuted = Color(0xFF7B8294),
    primaryAccent = Color(0xFF2F49D1),
    onPrimaryAccent = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFFD9383A),
    statusReadyEmerald = Color(0xFF16A34A),
    inkCoralRed = Color(0xFFD9383A),
    inkElectricBlue = Color(0xFF2563EB),
    inkDeepBlack = Color(0xFF171923),
    inkHighlighterYellow = Color(0x66FDE047),
    cardCornerRadius = 24.dp,
    fabCornerRadius = 20.dp
)

/**
 * 3. PASTEL (YUMUŞAK VE GENÇ) — Taslak 3 ("Pastel (yumuşak ve genç)")
 * Klasör kartları ve kişi avatarları ders bazlı pastel tonlar kullanır.
 */
val PastelSoftPalette = NoteThemePalette(
    id = ThemePreset.PASTEL_SOFT,
    displayNameRes = R.string.theme_midnight_oled,
    isDark = false,
    usePastelFolderCards = true,
    appBackground = Color(0xFFEEF1F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceInteractive = Color(0xFFE3E8F4),
    border = Color(0xFFDCE2F0),
    borderSubtle = Color(0xFFE6EBF5),
    canvasBackground = Color(0xFFFAF7EE),
    canvasMinorGrid = Color(0xFFE2DDD0),
    canvasMajorGrid = Color(0xFFD0C9B8),
    textPrimary = Color(0xFF181B28),
    textSecondary = Color(0xFF495068),
    textMuted = Color(0xFF79829C),
    primaryAccent = Color(0xFF3949AB),
    onPrimaryAccent = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFFE05252),
    statusReadyEmerald = Color(0xFF16A34A),
    inkCoralRed = Color(0xFFE05252),
    inkElectricBlue = Color(0xFF2962FF),
    inkDeepBlack = Color(0xFF181B28),
    inkHighlighterYellow = Color(0x66FDE047),
    cardCornerRadius = 24.dp,
    fabCornerRadius = 20.dp
)

fun ThemePreset.toPalette(systemInDarkTheme: Boolean = false): NoteThemePalette {
    return when (this) {
        ThemePreset.SYSTEM_AUTO -> if (systemInDarkTheme) NightDarkPalette else PaperLightPalette
        ThemePreset.NIGHT_DARK -> NightDarkPalette
        ThemePreset.PAPER_LIGHT -> PaperLightPalette
        ThemePreset.PASTEL_SOFT -> PastelSoftPalette
    }
}

val LocalNoteThemePalette = staticCompositionLocalOf { NightDarkPalette }

/**
 * Inter tarzı geometrik, okunaklı ve modern tablet tipografisi.
 */
val InterFontFamily = FontFamily.SansSerif

val TabletNotesTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

/**
 * 24dp yuvarlatılmış kartlar ve tam yuvarlak (pill) sekme/buton şekilleri.
 */
val TabletNotesShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TabletNotesTheme(
    preset: ThemePreset = ThemePreset.SYSTEM_AUTO,
    content: @Composable () -> Unit
) {
    val palette = preset.toPalette(isSystemInDarkTheme())

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primaryAccent,
            onPrimary = palette.onPrimaryAccent,
            secondary = palette.secondaryAccent,
            background = palette.appBackground,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border
        )
    } else {
        lightColorScheme(
            primary = palette.primaryAccent,
            onPrimary = palette.onPrimaryAccent,
            secondary = palette.secondaryAccent,
            background = palette.appBackground,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border
        )
    }

    CompositionLocalProvider(LocalNoteThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TabletNotesTypography,
            shapes = TabletNotesShapes,
            content = content
        )
    }
}

object TabletNotesTheme {
    val palette: NoteThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNoteThemePalette.current
}
