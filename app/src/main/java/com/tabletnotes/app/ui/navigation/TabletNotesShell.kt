package com.tabletnotes.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.components.GraphGridBackground
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.core.designsystem.theme.toPalette
import com.tabletnotes.app.feature.editor.ui.NoteEditorController
import com.tabletnotes.app.feature.editor.ui.NoteEditorScreen

enum class TopTabDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    NOTES(R.string.nav_notes, Icons.Default.Description),
    FRIENDS_CHAT(R.string.nav_friends_chat, Icons.Default.ChatBubbleOutline),
    NEARBY_SHARE(R.string.nav_nearby_share, Icons.Default.WifiTethering)
}

/**
 * Tablet ekranları için ana uygulama kabuğu (Shell).
 * Stitch tasarımındaki üst sekmeli hap (segmented pill) navigasyonu, tema değiştiriciyi
 * ve tam ekran çok sayfalı çizim editörüne geçişi yönetir.
 */
@Composable
fun TabletNotesShell(
    currentTheme: ThemePreset,
    onCycleTheme: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(TopTabDestination.NOTES) }
    var isEditorOpen by rememberSaveable { mutableStateOf(false) }
    val editorController = remember { NoteEditorController() }

    GraphGridBackground(modifier = Modifier.fillMaxSize()) {
        if (isEditorOpen) {
            NoteEditorScreen(
                controller = editorController,
                onCycleTheme = onCycleTheme,
                onNavigateBack = { isEditorOpen = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                TabletTopNavigationBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    currentTheme = currentTheme,
                    onCycleTheme = onCycleTheme,
                    onNewNoteClick = { isEditorOpen = true }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    when (selectedTab) {
                        TopTabDestination.NOTES -> NotesOverviewSection(
                            onOpenEditor = { isEditorOpen = true }
                        )
                        TopTabDestination.FRIENDS_CHAT -> MilestonePlaceholderSection(
                            titleRes = R.string.chat_section_title,
                            subtitleRes = R.string.chat_section_subtitle,
                            infoRes = R.string.chat_placeholder_info,
                            icon = Icons.Default.ChatBubbleOutline
                        )
                        TopTabDestination.NEARBY_SHARE -> MilestonePlaceholderSection(
                            titleRes = R.string.nearby_section_title,
                            subtitleRes = R.string.nearby_section_subtitle,
                            infoRes = R.string.nearby_placeholder_info,
                            icon = Icons.Default.WifiTethering
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletTopNavigationBar(
    selectedTab: TopTabDestination,
    onSelectTab: (TopTabDestination) -> Unit,
    currentTheme: ThemePreset,
    onCycleTheme: () -> Unit,
    onNewNoteClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    Surface(
        color = palette.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Uygulama Başlığı ve Kalem Durum Rozeti
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )

                Surface(
                    color = palette.surfaceElevated,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.statusReadyEmerald)
                        )
                        Text(
                            text = stringResource(R.string.status_stylus_ready),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            // Orta: Stitch Tasarımındaki Yuvarlatılmış Hap (Segmented Pill) Navigasyonu
            Surface(
                color = palette.appBackground,
                shape = CircleShape,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopTabDestination.entries.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val tabBackground = if (isSelected) palette.textSecondary else palette.appBackground
                        val tabTextColor = if (isSelected) palette.appBackground else palette.textMuted

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(tabBackground)
                                .clickable { onSelectTab(tab) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = tabTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tabTextColor
                            )
                        }
                    }
                }
            }

            // Sağ: Tek Tıkla Tema Değiştirici ve Yeni Not Butonu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCycleTheme,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = stringResource(R.string.action_switch_theme),
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.theme_current_format,
                            stringResource(currentTheme.toPalette().displayNameRes)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }

                Button(
                    onClick = onNewNoteClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryAccent,
                        contentColor = palette.onPrimaryAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_note),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_new_note),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesOverviewSection(
    onOpenEditor: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.notes_section_title),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.textPrimary
        )
        Text(
            text = stringResource(R.string.notes_section_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted
        )

        val sampleCards = listOf(
            Triple(
                R.string.notes_sample_physics_title,
                R.string.notes_sample_physics_meta,
                palette.inkCoralRed
            ),
            Triple(
                R.string.notes_sample_calculus_title,
                R.string.notes_sample_calculus_meta,
                palette.inkElectricBlue
            ),
            Triple(
                R.string.notes_sample_algos_title,
                R.string.notes_sample_algos_meta,
                palette.statusReadyEmerald
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sampleCards.forEach { (titleRes, metaRes, accentColor) ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(palette.cardCornerRadius))
                        .clickable { onOpenEditor() },
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(palette.cardCornerRadius),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            text = stringResource(metaRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestonePlaceholderSection(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    @StringRes infoRes: Int,
    icon: ImageVector
) {
    val palette = TabletNotesTheme.palette

    Surface(
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(palette.cardCornerRadius),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.primaryAccent,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textSecondary
            )
            Text(
                text = stringResource(infoRes),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted
            )
        }
    }
}
