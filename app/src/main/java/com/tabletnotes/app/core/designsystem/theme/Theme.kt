package com.tabletnotes.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Uygulama geneli Material 3 + Özel [NoteThemePalette] sarmalayıcısı.
 * Herhangi bir Composable içinden `TabletNotesTheme.palette` ile aktif temanın
 * tüm renklerine ve köşe yuvarlatmalarına erişilebilir.
 */
@Composable
fun TabletNotesTheme(
    preset: ThemePreset = ThemePreset.STITCH_CHARCOAL_DARK,
    content: @Composable () -> Unit
) {
    val palette = preset.toPalette()

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

    val shapes = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(palette.cardCornerRadius),
        large = RoundedCornerShape(22.dp)
    )

    CompositionLocalProvider(LocalNoteThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TabletNotesTypography,
            shapes = shapes,
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
