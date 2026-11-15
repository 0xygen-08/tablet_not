package com.tabletnotes.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme

/**
 * Ana uygulama kabuğu arka planı.
 * Taslaklardaki (Gece, Kağıt, Pastel) sade ve temiz arka plan rengini uygular;
 * kağıt üzerindeki noktalı/kareli/çizgili doku ise yalnızca Not Editörü sayfasının
 * kendi bağımsız kağıdı üzerinde çizilir.
 */
@Composable
fun GraphGridBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = TabletNotesTheme.palette
    Box(
        modifier = modifier.background(palette.appBackground),
        content = content
    )
}
