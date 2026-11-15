package com.tabletnotes.app.feature.editor.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import java.util.Locale
import kotlin.math.roundToInt

private val FullColorPalette = listOf(
    0xFFDC4C46.toInt(), // Mercan Kırmızı (Taslak 1. Renk)
    0xFF2B63D9.toInt(), // Kraliyet Mavisi (Taslak 2. Renk)
    0xFF1A1C23.toInt(), // Koyu Mürekkep Siyahı (Taslak 3. Renk)
    0xFF16794B.toInt(), // Zümrüt Yeşili
    0xFF6B38E8.toInt(), // Mor / Lavanta
    0xFFFA8C16.toInt(), // Kehribar Turuncu
    0xFFD81B60.toInt(), // Fuşya / Gül
    0xFF0E7490.toInt(), // Okyanus Turkuaz
    0xFF5D4037.toInt(), // Sepya Kahve
    0xFF475569.toInt(), // Kurşun Gri
    0xFFFACC15.toInt(), // Fosforlu Sarı
    0xFFF8FAFC.toInt()  // Beyaz (Gece Kağıdı için)
)

@Composable
fun NoteEditorScreen(
    controller: NoteEditorController,
    autosaveStatus: AutosaveStatus,
    onRequestJsonDebug: () -> String,
    onCycleTheme: () -> Unit,
    onNavigateBack: () -> Unit,
    onShareNoteRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette

    BackHandler(onBack = onNavigateBack)

    var showTextDialog by rememberSaveable { mutableStateOf(false) }
    var editingTextBlockId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTextDocX by rememberSaveable { mutableFloatStateOf(160f) }
    var pendingTextDocY by rememberSaveable { mutableFloatStateOf(160f) }
    var pendingTextValue by rememberSaveable { mutableStateOf("") }

    var showTitleDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTitleValue by rememberSaveable { mutableStateOf(controller.noteTitle) }
    var pendingCourseValue by rememberSaveable { mutableStateOf(controller.courseName) }

    var showPenPopover by remember { mutableStateOf(false) }
    var showColorCustomDialog by remember { mutableStateOf(false) }
    var showPaperSettingsDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showJsonDebugDialog by rememberSaveable { mutableStateOf(false) }
    var jsonDebugText by remember { mutableStateOf("") }
    var showDevHud by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.appBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. ÜST BAŞLIK ÇUBUĞU (Taslak 3 ile birebir: Sol < Geri | Orta "Fizik › Vektörler" Pill | Sağ Paylaş & Menü)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sol Yuvarlak Geri (<) Butonu (38dp tablet boyutu)
            Surface(
                onClick = onNavigateBack,
                shape = CircleShape,
                color = palette.surfaceElevated,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = stringResource(R.string.action_back),
                        tint = palette.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Orta Breadcrumb Pill: "Fizik › Vektörler" + Kayıt Durumu Noktası
            Surface(
                onClick = {
                    pendingTitleValue = controller.noteTitle
                    pendingCourseValue = controller.courseName
                    showTitleDialog = true
                },
                shape = CircleShape,
                color = palette.surfaceElevated,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.textPrimary)) {
                                append(controller.courseName)
                            }
                            withStyle(SpanStyle(color = palette.textMuted)) {
                                append("  ›  ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = palette.textSecondary)) {
                                append(controller.noteTitle)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )

                    AutosaveDotIndicator(status = autosaveStatus)
                }
            }

            // Odak Modu (Focus Mode — Yüzen Araç Çubuğunu Gizle/Göster)
            Surface(
                onClick = { controller.isFocusMode = !controller.isFocusMode },
                shape = CircleShape,
                color = if (controller.isFocusMode) palette.primaryAccent else palette.surfaceElevated,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (controller.isFocusMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.action_focus_mode),
                        tint = if (controller.isFocusMode) palette.onPrimaryAccent else palette.textPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Sağ Paylaş / Dışa Aktar (↑) Butonu (Taslak 3 ile birebir)
            Surface(
                onClick = { onShareNoteRequest?.invoke() },
                shape = CircleShape,
                color = palette.surfaceElevated,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = stringResource(R.string.action_share_note),
                        tint = palette.textPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Sağ Üç Çizgi (≡) Menü Butonu (Kağıt Ayarları, Sayfa Ekleme, Geliştirici/Binary Araçlar burada)
            Box {
                Surface(
                    onClick = { showMoreMenu = true },
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.action_menu),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_paper_settings_title)) },
                        leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            showPaperSettingsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_add_page)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            controller.addPage()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (controller.stylusOnlyMode) {
                                    stringResource(R.string.editor_tool_stylus_only) + " ✓"
                                } else {
                                    stringResource(R.string.editor_tool_finger_draw)
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.PanToolAlt, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            controller.stylusOnlyMode = !controller.stylusOnlyMode
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    R.string.editor_zoom_reset,
                                    (controller.zoom * 100).roundToInt()
                                )
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            controller.resetZoomAndPan()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_switch_theme)) },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onCycleTheme()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_clear_canvas)) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            controller.clearAll()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_stress_test_10k)) },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            showDevHud = true
                            controller.generateTenThousandStrokesBenchmark()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_json_debug_btn)) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            jsonDebugText = onRequestJsonDebug()
                            showJsonDebugDialog = true
                        }
                    )
                }
            }
        }

        // 2. SAMSUNG NOTES TARZI KOMPAKT YÜZEN ARAÇ ÇUBUĞU (Odak Modunda Gizlenebilir — 42dp yükseklik)
        AnimatedVisibility(
            visible = !controller.isFocusMode,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // SOL & ORTA: Samsung Notes Sıralamasıyla Araçlar + 3 Hızlı Erişim Rengi
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1) Kalem Çeşidi Seçici (İnce Dolma, Kalın Dolma, Tükenmez, Kurşun, Marker, Sulu Boya)
                            Box {
                                val isPenActive = controller.activeTool.isPenVariant
                                CompactToolbarButton(
                                    icon = penIconFor(controller.lastPenVariant),
                                    label = penShortNameFor(controller.lastPenVariant),
                                    selected = isPenActive,
                                    showDropdownChevron = true,
                                    onClick = {
                                        if (isPenActive) {
                                            showPenPopover = true
                                        } else {
                                            controller.selectPenVariant(controller.lastPenVariant)
                                        }
                                    }
                                )

                                DropdownMenu(
                                    expanded = showPenPopover,
                                    onDismissRequest = { showPenPopover = false },
                                    modifier = Modifier.width(280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.editor_tool_pen),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.textPrimary
                                        )
                                        DrawingToolType.penVariants.forEach { variant ->
                                            val isSelected = controller.lastPenVariant == variant && controller.activeTool == variant
                                            Surface(
                                                onClick = {
                                                    controller.selectPenVariant(variant)
                                                    showPenPopover = false
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) palette.primaryAccent.copy(alpha = 0.16f) else Color.Transparent,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = penIconFor(variant),
                                                            contentDescription = null,
                                                            tint = if (isSelected) palette.primaryAccent else palette.textSecondary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = penFullNameFor(variant),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = if (isSelected) palette.primaryAccent else palette.textPrimary
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = palette.primaryAccent,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                        Text(
                                            text = stringResource(
                                                R.string.editor_pen_thickness_label,
                                                controller.selectedThickness.roundToInt()
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textSecondary
                                        )
                                        Slider(
                                            value = controller.selectedThickness,
                                            onValueChange = { controller.selectedThickness = it },
                                            valueRange = 1.5f..22f
                                        )
                                    }
                                }
                            }

                            // 2) Fosforlu Kalem (Highlighter)
                            CompactToolbarIconButton(
                                icon = Icons.Default.BorderColor,
                                contentDescription = stringResource(R.string.editor_tool_highlighter),
                                selected = controller.activeTool == DrawingToolType.HIGHLIGHTER,
                                onClick = { controller.selectTool(DrawingToolType.HIGHLIGHTER) }
                            )

                            // 3) Silgi (Eraser)
                            CompactToolbarIconButton(
                                icon = Icons.Default.AutoFixNormal,
                                contentDescription = stringResource(R.string.editor_tool_eraser),
                                selected = controller.activeTool == DrawingToolType.ERASER,
                                onClick = { controller.selectTool(DrawingToolType.ERASER) }
                            )

                            // 4) Kare Seçim Modu (Lasso / Box Selection: Taşıma, Kopyalama, Boyutlandırma)
                            CompactToolbarIconButton(
                                icon = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.editor_tool_lasso),
                                selected = controller.activeTool == DrawingToolType.LASSO_SELECT,
                                onClick = { controller.selectTool(DrawingToolType.LASSO_SELECT) }
                            )

                            // 5) Metin Kutusu (Text)
                            CompactToolbarIconButton(
                                icon = Icons.Default.TextFields,
                                contentDescription = stringResource(R.string.editor_tool_text),
                                selected = controller.activeTool == DrawingToolType.TEXT,
                                onClick = { controller.selectTool(DrawingToolType.TEXT) }
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // 6) 3 Hızlı Erişim Renk Tuşu (Taslak 3'teki Kırmızı, Mavi, Siyah Yuvarlaklar)
                            controller.favoriteColors.forEachIndexed { slotIdx, colorArgb ->
                                val isSlotSelected = controller.selectedColorArgb == colorArgb
                                Box(
                                    modifier = Modifier
                                        .size(if (isSlotSelected) 26.dp else 22.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorArgb))
                                        .border(
                                            width = if (isSlotSelected) 2.5.dp else 1.dp,
                                            color = if (isSlotSelected) palette.primaryAccent else palette.border,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            controller.selectFavoriteColorSlot(slotIdx)
                                        }
                                )
                            }

                            // Hızlı Renk Özelleştirme / Renk Paleti Butonu
                            IconButton(
                                onClick = { showColorCustomDialog = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = stringResource(R.string.editor_color_picker_title),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // SAĞ: Geri Al (Undo ↶) & İleri Al (Redo ↷) (Taslak 3 & Samsung Notes)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { controller.undo() },
                                enabled = controller.canUndo,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = stringResource(R.string.editor_tool_undo),
                                    tint = if (controller.canUndo) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { controller.redo() },
                                enabled = controller.canRedo,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = stringResource(R.string.editor_tool_redo),
                                    tint = if (controller.canRedo) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Seçim Modu Aktifken ve Çizgi Seçildiyse Çıkan Bağlamsal Eylem Çubuğu (Taşı, Kopyala, Boyutlandır, Sil)
                if (controller.selectedStrokeIds.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = palette.surfaceElevated,
                        border = BorderStroke(1.dp, palette.primaryAccent),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = { controller.duplicateSelectedStrokes() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.editor_selection_duplicate), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = { controller.scaleSelectedStrokes(1.18f) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.editor_selection_scale_up), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = { controller.scaleSelectedStrokes(0.84f) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.editor_selection_scale_down), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = { controller.deleteSelectedStrokes() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = palette.secondaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.editor_selection_delete),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.secondaryAccent
                                )
                            }
                            TextButton(
                                onClick = { controller.clearSelection() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.editor_selection_clear), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // 3. ANA DÜŞÜK GECİKMELİ KAĞIT TUVALİ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
        ) {
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
                    view.syncState(revision = revision, palette = palette)
                }
            )

            // Yalnızca Geliştirici Stres Testi açıldığında sağ altta kompakt FPS göstergesi
            if (showDevHud) {
                Surface(
                    color = palette.surfaceElevated.copy(alpha = 0.92f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
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

    // --- DİYALOGLAR ---

    // 1. Not Başlığı ve Ders Klasörü Düzenleme Diyaloğu
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text(stringResource(R.string.editor_edit_title_dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pendingCourseValue,
                        onValueChange = { pendingCourseValue = it },
                        label = { Text(stringResource(R.string.editor_course_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pendingTitleValue,
                        onValueChange = { pendingTitleValue = it },
                        label = { Text(stringResource(R.string.editor_note_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.updateTitleAndCourse(
                            newTitle = pendingTitleValue,
                            newCourse = pendingCourseValue
                        )
                        showTitleDialog = false
                    }
                ) {
                    Text(stringResource(R.string.editor_text_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) {
                    Text(stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // 2. Bağımsız Kağıt Rengi ve Deseni Diyaloğu (Uygulama temasından bağımsız: Krem/Beyaz/Koyu & Noktalı/Kareli/Çizgili/Düz)
    if (showPaperSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPaperSettingsDialog = false },
            title = { Text(stringResource(R.string.editor_paper_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaperColorOptionChip(
                            labelRes = R.string.editor_paper_color_cream,
                            previewColor = Color(PaperColor.CREAM.bgArgb),
                            selected = controller.paperColor == PaperColor.CREAM,
                            onClick = { controller.updatePaperStyle(PaperColor.CREAM, controller.paperTemplate) },
                            modifier = Modifier.weight(1f)
                        )
                        PaperColorOptionChip(
                            labelRes = R.string.editor_paper_color_white,
                            previewColor = Color(PaperColor.WHITE.bgArgb),
                            selected = controller.paperColor == PaperColor.WHITE,
                            onClick = { controller.updatePaperStyle(PaperColor.WHITE, controller.paperTemplate) },
                            modifier = Modifier.weight(1f)
                        )
                        PaperColorOptionChip(
                            labelRes = R.string.editor_paper_color_dark,
                            previewColor = Color(PaperColor.DARK.bgArgb),
                            selected = controller.paperColor == PaperColor.DARK,
                            onClick = { controller.updatePaperStyle(PaperColor.DARK, controller.paperTemplate) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaperTemplateOptionChip(
                            labelRes = R.string.editor_paper_template_dotted,
                            selected = controller.paperTemplate == PaperTemplate.DOTTED,
                            onClick = { controller.updatePaperStyle(controller.paperColor, PaperTemplate.DOTTED) },
                            modifier = Modifier.weight(1f)
                        )
                        PaperTemplateOptionChip(
                            labelRes = R.string.editor_paper_template_grid,
                            selected = controller.paperTemplate == PaperTemplate.GRID,
                            onClick = { controller.updatePaperStyle(controller.paperColor, PaperTemplate.GRID) },
                            modifier = Modifier.weight(1f)
                        )
                        PaperTemplateOptionChip(
                            labelRes = R.string.editor_paper_template_lined,
                            selected = controller.paperTemplate == PaperTemplate.LINED,
                            onClick = { controller.updatePaperStyle(controller.paperColor, PaperTemplate.LINED) },
                            modifier = Modifier.weight(1f)
                        )
                        PaperTemplateOptionChip(
                            labelRes = R.string.editor_paper_template_blank,
                            selected = controller.paperTemplate == PaperTemplate.BLANK,
                            onClick = { controller.updatePaperStyle(controller.paperColor, PaperTemplate.BLANK) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPaperSettingsDialog = false }) {
                    Text(stringResource(R.string.editor_text_dialog_save))
                }
            }
        )
    }

    // 3. Hızlı Erişim Rengi Özelleştirme Diyaloğu
    if (showColorCustomDialog) {
        AlertDialog(
            onDismissRequest = { showColorCustomDialog = false },
            title = { Text(stringResource(R.string.editor_color_picker_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FullColorPalette.take(6).forEach { colorArgb ->
                            ColorSwatchCircle(
                                colorArgb = colorArgb,
                                isSelected = controller.selectedColorArgb == colorArgb,
                                onClick = {
                                    controller.updateFavoriteColor(controller.activeFavoriteColorSlot, colorArgb)
                                    showColorCustomDialog = false
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FullColorPalette.drop(6).forEach { colorArgb ->
                            ColorSwatchCircle(
                                colorArgb = colorArgb,
                                isSelected = controller.selectedColorArgb == colorArgb,
                                onClick = {
                                    controller.updateFavoriteColor(controller.activeFavoriteColorSlot, colorArgb)
                                    showColorCustomDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorCustomDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }

    // 4. Metin Kutusu Ekleme / Düzenleme Diyaloğu
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(text = stringResource(R.string.editor_text_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = pendingTextValue,
                    onValueChange = { pendingTextValue = it },
                    placeholder = { Text(stringResource(R.string.editor_text_dialog_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
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
                    Text(stringResource(R.string.editor_text_dialog_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingTextBlockId != null) {
                        OutlinedButton(
                            onClick = {
                                editingTextBlockId?.let { controller.deleteTextBlock(it) }
                                showTextDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.editor_text_dialog_delete))
                        }
                    }
                    TextButton(onClick = { showTextDialog = false }) {
                        Text(stringResource(R.string.editor_text_dialog_cancel))
                    }
                }
            }
        )
    }

    // 5. Geliştirici JSON & Binary Özeti Diyaloğu (Üç Çizgi Menüsünden Açılır)
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
                        .widthIn(min = 420.dp)
                        .heightIn(max = 360.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = jsonDebugText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = palette.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showJsonDebugDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }
}

@Composable
private fun AutosaveDotIndicator(status: AutosaveStatus) {
    val palette = TabletNotesTheme.palette
    val dotColor = when (status) {
        is AutosaveStatus.Idle -> palette.textMuted.copy(alpha = 0.4f)
        is AutosaveStatus.Saving -> palette.primaryAccent
        is AutosaveStatus.Saved -> palette.statusReadyEmerald
        is AutosaveStatus.LowStorageError -> palette.secondaryAccent
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
private fun CompactToolbarButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showDropdownChevron: Boolean = false,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val bgColor = if (selected) palette.primaryAccent else Color.Transparent
    val fgColor = if (selected) palette.onPrimaryAccent else palette.textPrimary

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fgColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fgColor
        )
        if (showDropdownChevron) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CompactToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val bgColor = if (selected) palette.primaryAccent else Color.Transparent
    val fgColor = if (selected) palette.onPrimaryAccent else palette.textSecondary

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fgColor,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun PaperColorOptionChip(
    @StringRes labelRes: Int,
    previewColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.primaryAccent.copy(alpha = 0.14f) else palette.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) palette.primaryAccent else palette.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, palette.border, CircleShape)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textPrimary
            )
        }
    }
}

@Composable
private fun PaperTemplateOptionChip(
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) palette.primaryAccent else palette.surface,
        border = BorderStroke(1.dp, if (selected) palette.primaryAccent else palette.border),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) palette.onPrimaryAccent else palette.textPrimary
            )
        }
    }
}

@Composable
private fun ColorSwatchCircle(
    colorArgb: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) palette.primaryAccent else palette.border,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private fun penIconFor(tool: DrawingToolType): ImageVector = when (tool) {
    DrawingToolType.FINE_FOUNTAIN -> Icons.Default.Edit
    DrawingToolType.CALLIGRAPHY -> Icons.Default.Draw
    DrawingToolType.PEN -> Icons.Default.Create
    DrawingToolType.PENCIL -> Icons.Default.Edit
    DrawingToolType.MARKER -> Icons.Default.BorderColor
    DrawingToolType.WATERCOLOR -> Icons.Default.Brush
    else -> Icons.Default.Create
}

@Composable
private fun penShortNameFor(tool: DrawingToolType): String = when (tool) {
    DrawingToolType.FINE_FOUNTAIN -> stringResource(R.string.editor_pen_fine_fountain)
    DrawingToolType.CALLIGRAPHY -> stringResource(R.string.editor_pen_calligraphy)
    DrawingToolType.PEN -> stringResource(R.string.editor_pen_ballpoint)
    DrawingToolType.PENCIL -> stringResource(R.string.editor_pen_pencil)
    DrawingToolType.MARKER -> stringResource(R.string.editor_pen_marker)
    DrawingToolType.WATERCOLOR -> stringResource(R.string.editor_pen_watercolor)
    else -> stringResource(R.string.editor_tool_pen)
}

@Composable
private fun penFullNameFor(tool: DrawingToolType): String = penShortNameFor(tool)

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
