package com.tabletnotes.app.feature.editor.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.DrawingToolType
import java.util.Locale
import kotlin.math.roundToInt

private val INK_COLOR_PRESETS = intArrayOf(
    0xFFEF5350.toInt(), // Stitch Coral Red
    0xFF60A5FA.toInt(), // Electric Blue
    0xFF34D399.toInt(), // Emerald Green
    0xFFFBBF24.toInt(), // Amber Yellow
    0xFFA78BFA.toInt(), // Royal Purple
    0xFFF1F3F7.toInt(), // Chalk White
    0xFF1E293B.toInt()  // Deep Slate
)

private val THICKNESS_PRESETS = floatArrayOf(2.2f, 4.5f, 8.0f, 14.0f)

/**
 * M2 & M3: Tam işlevsel Düşük Gecikmeli Çizim, Çoklu Sayfa, Atomik Otomatik Kayıt
 * ve JSON Debug Dışa Aktarım destekli Not Editörü Ekranı.
 */
@Composable
fun NoteEditorScreen(
    controller: NoteEditorController,
    autosaveStatus: AutosaveStatus,
    onRequestJsonDebug: () -> String,
    onCycleTheme: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    // Metin Kutusu (Text Block) diyaloğu
    var showTextDialog by remember { mutableStateOf(false) }
    var editingTextBlockId by remember { mutableStateOf<String?>(null) }
    var pendingTextDocX by remember { mutableFloatStateOf(120f) }
    var pendingTextDocY by remember { mutableFloatStateOf(120f) }
    var pendingTextValue by remember { mutableStateOf("") }

    // Başlık düzenleme diyaloğu
    var showTitleDialog by remember { mutableStateOf(false) }
    var pendingTitleValue by remember { mutableStateOf("") }

    // JSON Debug önizleme diyaloğu
    var showJsonDebugDialog by remember { mutableStateOf(false) }
    var jsonDebugText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 1. Üst Ana Araç Çubuğu
        Surface(
            color = palette.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol: Geri Butonu, Tıklanabilir Not Başlığı ve Atomik Otomatik Kayıt Rozeti
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_back),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary
                        )
                    }

                    Text(
                        text = controller.noteTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                pendingTitleValue = controller.noteTitle
                                showTitleDialog = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    AutosaveBadge(status = autosaveStatus)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Orta: Çizim Araçları
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToolSelectPill(
                        icon = Icons.Default.Create,
                        labelRes = R.string.editor_tool_pen,
                        selected = controller.activeTool == DrawingToolType.PEN,
                        onClick = { controller.activeTool = DrawingToolType.PEN }
                    )
                    ToolSelectPill(
                        icon = Icons.Default.BorderColor,
                        labelRes = R.string.editor_tool_highlighter,
                        selected = controller.activeTool == DrawingToolType.HIGHLIGHTER,
                        onClick = { controller.activeTool = DrawingToolType.HIGHLIGHTER }
                    )
                    ToolSelectPill(
                        icon = Icons.Default.AutoFixNormal,
                        labelRes = R.string.editor_tool_eraser,
                        selected = controller.activeTool == DrawingToolType.ERASER,
                        onClick = { controller.activeTool = DrawingToolType.ERASER }
                    )
                    ToolSelectPill(
                        icon = Icons.Default.TextFields,
                        labelRes = R.string.editor_tool_text,
                        selected = controller.activeTool == DrawingToolType.TEXT,
                        onClick = { controller.activeTool = DrawingToolType.TEXT }
                    )

                    ToolSelectPill(
                        icon = if (controller.stylusOnlyMode) Icons.Default.Edit else Icons.Default.PanToolAlt,
                        labelRes = if (controller.stylusOnlyMode) {
                            R.string.editor_tool_stylus_only
                        } else {
                            R.string.editor_tool_finger_draw
                        },
                        selected = controller.stylusOnlyMode,
                        onClick = { controller.stylusOnlyMode = !controller.stylusOnlyMode }
                    )

                    IconButton(
                        onClick = { controller.undo() },
                        enabled = controller.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.editor_tool_undo),
                            tint = if (controller.canUndo) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = { controller.redo() },
                        enabled = controller.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.editor_tool_redo),
                            tint = if (controller.canRedo) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Sağ: JSON Debug Dışa Aktarım ve Tema Değiştirici
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            jsonDebugText = onRequestJsonDebug()
                            showJsonDebugDialog = true
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = stringResource(R.string.editor_json_debug_btn),
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.editor_json_debug_btn),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = onCycleTheme,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(R.string.action_switch_theme),
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. İkinci Alt Çubuk: Renk Seçici, Kalınlık Seçici, Yeni Sayfa, +10K Çizgi Testi ve Canlı FPS HUD
        Surface(
            color = palette.surfaceElevated.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Renk Seçici (Color Picker)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    INK_COLOR_PRESETS.forEach { argb ->
                        val isSelected = controller.selectedColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 28.dp else 22.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) palette.textPrimary else palette.border,
                                    shape = CircleShape
                                )
                                .clickable { controller.selectedColorArgb = argb }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    THICKNESS_PRESETS.forEach { thickness ->
                        val isSelected = controller.selectedThickness == thickness
                        Surface(
                            color = if (isSelected) palette.primaryAccent else palette.surface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { controller.selectedThickness = thickness }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((thickness + 3f).dp.coerceAtMost(14.dp))
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) palette.onPrimaryAccent else palette.textSecondary
                                        )
                                )
                                Text(
                                    text = "${thickness.roundToInt()}pt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) palette.onPrimaryAccent else palette.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { controller.resetZoomAndPan() },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.editor_zoom_reset,
                                (controller.zoom * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = { controller.addPage() },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.editor_add_page),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = { controller.generateTenThousandStrokesBenchmark() },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.primaryAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.editor_stress_test_10k),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primaryAccent
                        )
                    }

                    OutlinedButton(
                        onClick = { controller.clearAll() },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.editor_clear_canvas),
                            tint = palette.secondaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.editor_clear_canvas),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryAccent
                        )
                    }

                    Surface(
                        color = palette.appBackground,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.editor_perf_hud_format,
                                controller.currentFps,
                                controller.strokeCount,
                                controller.cachedTileCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.statusReadyEmerald,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Ana Düşük Gecikmeli Çizim Tuvali
        Box(modifier = Modifier.fillMaxSize()) {
            val revision = controller.renderRevision
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    NoteEditorCanvasView(
                        context = ctx,
                        controller = controller,
                        onTapForTextBlock = { docX, docY ->
                            val hitBlock = controller.textBlocks.firstOrNull { block ->
                                docX in block.x..(block.x + block.width) &&
                                    docY in block.y..(block.y + 90f)
                            }
                            editingTextBlockId = hitBlock?.id
                            pendingTextDocX = hitBlock?.x ?: docX
                            pendingTextDocY = hitBlock?.y ?: docY
                            pendingTextValue = hitBlock?.text ?: ""
                            showTextDialog = true
                        }
                    )
                },
                update = { view ->
                    if (revision >= 0) {
                        view.updatePalette(palette)
                        view.invalidate()
                    }
                }
            )

            Surface(
                color = palette.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.editor_page_badge,
                            1,
                            controller.pages.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryAccent
                    )
                    Text(
                        text = stringResource(R.string.editor_stylus_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
        }
    }

    // Metin Kutusu Diyaloğu
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(text = stringResource(R.string.editor_text_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = pendingTextValue,
                    onValueChange = { pendingTextValue = it },
                    placeholder = { Text(text = stringResource(R.string.editor_text_dialog_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.upsertTextBlock(
                            existingId = editingTextBlockId,
                            docX = pendingTextDocX,
                            docY = pendingTextDocY,
                            text = pendingTextValue,
                            colorArgb = controller.selectedColorArgb
                        )
                        showTextDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.editor_text_dialog_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingTextBlockId != null) {
                        TextButton(
                            onClick = {
                                editingTextBlockId?.let { controller.deleteTextBlock(it) }
                                showTextDialog = false
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.editor_text_dialog_delete),
                                color = palette.secondaryAccent
                            )
                        }
                    }
                    TextButton(onClick = { showTextDialog = false }) {
                        Text(text = stringResource(R.string.editor_text_dialog_cancel))
                    }
                }
            }
        )
    }

    // Not Başlığı Düzenleme Diyaloğu
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text(text = stringResource(R.string.editor_edit_title_dialog)) },
            text = {
                OutlinedTextField(
                    value = pendingTitleValue,
                    onValueChange = { pendingTitleValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.updateTitle(pendingTitleValue)
                        showTitleDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.editor_text_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) {
                    Text(text = stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // JSON Debug Dışa Aktarım Diyaloğu
    if (showJsonDebugDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDebugDialog = false },
            title = { Text(text = stringResource(R.string.editor_json_debug_title)) },
            text = {
                Surface(
                    color = palette.appBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    Text(
                        text = jsonDebugText.take(6000),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showJsonDebugDialog = false }) {
                    Text(text = stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }
}

@Composable
private fun AutosaveBadge(status: AutosaveStatus) {
    val palette = TabletNotesTheme.palette
    val text = when (status) {
        AutosaveStatus.Idle -> null
        AutosaveStatus.Saving -> stringResource(R.string.editor_autosave_saving)
        is AutosaveStatus.Saved -> stringResource(
            R.string.editor_autosave_saved_format,
            formatFileSize(status.sizeBytes)
        )
        AutosaveStatus.LowStorageError -> stringResource(R.string.editor_autosave_low_storage)
    }
    if (text != null) {
        val color = when (status) {
            AutosaveStatus.LowStorageError -> palette.secondaryAccent
            is AutosaveStatus.Saved -> palette.statusReadyEmerald
            else -> palette.textMuted
        }
        Surface(
            color = palette.surfaceElevated,
            shape = CircleShape,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

@Composable
private fun ToolSelectPill(
    icon: ImageVector,
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val containerColor = if (selected) palette.primaryAccent else palette.surfaceElevated
    val contentColor = if (selected) palette.onPrimaryAccent else palette.textSecondary

    Surface(
        color = containerColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) palette.primaryAccent else palette.border),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}
