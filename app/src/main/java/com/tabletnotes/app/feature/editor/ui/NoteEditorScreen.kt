package com.tabletnotes.app.feature.editor.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.EraserMode
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.domain.model.StylusButtonAction
import com.tabletnotes.app.domain.model.TagNormalizer
import com.tabletnotes.app.domain.repository.NoteSummary
import java.util.Locale
import kotlin.math.roundToInt

private val FullColorPalette = listOf(
    0xFFDC4C46.toInt(), // Mercan Kırmızı
    0xFF2B63D9.toInt(), // Kraliyet Mavisi
    0xFF1A1C23.toInt(), // Koyu Mürekkep Siyahı
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
    courseNotes: List<NoteSummary>,
    onSwitchSubNote: (String) -> Unit,
    onCreateSubNote: () -> Unit,
    onDeleteSubNote: (String) -> Unit,
    onRequestJsonDebug: () -> String,
    onCycleTheme: () -> Unit,
    onNavigateBack: () -> Unit,
    onShareNoteRequest: (() -> Unit)? = null,
    isPortabilityBusy: Boolean = false,
    noticeMessageRes: Int? = null,
    onDismissNotice: () -> Unit = {},
    onStylusSideButtonActionChange: (StylusButtonAction) -> Unit = { controller.stylusSideButtonAction = it },
    onImportGalleryImage: (Uri) -> Unit = {},
    onImportPdfPages: (Uri) -> Unit = {},
    onExportPdf: (Uri) -> Unit = {},
    onExportPng: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette

    BackHandler(onBack = onNavigateBack)

    val pickGalleryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportGalleryImage(uri)
    }

    val importPdfPagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPdfPages(uri)
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onExportPdf(uri)
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) onExportPng(uri)
    }

    var showTextDialog by rememberSaveable { mutableStateOf(false) }
    var editingTextBlockId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTextDocX by rememberSaveable { mutableFloatStateOf(160f) }
    var pendingTextDocY by rememberSaveable { mutableFloatStateOf(160f) }
    var pendingTextValue by rememberSaveable { mutableStateOf("") }

    var showTitleDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTitleValue by rememberSaveable { mutableStateOf(controller.noteTitle) }
    var pendingCourseValue by rememberSaveable { mutableStateOf(controller.courseName) }
    var newTagInput by rememberSaveable { mutableStateOf("") }

    var showSubNotesMenu by remember { mutableStateOf(false) }
    var showPenPopover by remember { mutableStateOf(false) }
    var showLaserMenu by remember { mutableStateOf(false) }
    var showEraserMenu by remember { mutableStateOf(false) }
    var showColorCustomDialog by remember { mutableStateOf(false) }
    var showPaperSettingsDialog by remember { mutableStateOf(false) }
    var showPageManagerDialog by rememberSaveable { mutableStateOf(false) }
    var showStylusButtonDialog by rememberSaveable { mutableStateOf(false) }
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
        // 1. ÜST BAŞLIK ÇUBUĞU: Sol < Geri | Orta "Ders › Alt Not" Pill | Odak | Paylaş | ≡ Menü
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AutosaveDotIndicator(status = autosaveStatus)
                }
            }

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

            // Sağ Üç Çizgi (≡) Menü Butonu (Yakınlaştırma Sıfırla, Parmak+Kalem ve Sayfa Temizle kaldırıldı)
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
                        text = { Text(stringResource(R.string.editor_page_manager_btn)) },
                        leadingIcon = { Icon(Icons.Default.ViewAgenda, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            showPageManagerDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_add_page)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            controller.addPage(scrollToNewPage = true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_import_pdf_pages)) },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            importPdfPagesLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_export_pdf)) },
                        leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            val safeName = controller.noteTitle.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").ifBlank { "not" }
                            exportPdfLauncher.launch("$safeName.pdf")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_export_png)) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            val safeName = controller.noteTitle.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").ifBlank { "not" }
                            val pageNum = controller.getCurrentVisiblePageIndex() + 1
                            exportPngLauncher.launch("${safeName}_s$pageNum.png")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_stylus_button_menu)) },
                        leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            showStylusButtonDialog = true
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

        // 2. SOL ALT NOTLAR BUTONU + SAMSUNG NOTES TARZI KOMPAKT YÜZEN ARAÇ ÇUBUĞU
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sol Geri Tuşunun Hemen Altındaki Klasör Alt Notları Butonu
            Box {
                Surface(
                    onClick = { showSubNotesMenu = true },
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = stringResource(R.string.editor_subnotes_btn),
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSubNotesMenu,
                    onDismissRequest = { showSubNotesMenu = false },
                    modifier = Modifier.width(300.dp)
                ) {
                    Text(
                        text = "${controller.courseName} • Alt Notlar (${courseNotes.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    HorizontalDivider()

                    courseNotes.forEach { summary ->
                        val isCurrent = summary.id == controller.noteId
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = summary.title +
                                            (if (summary.isPinned) " 📌" else "") +
                                            (if (summary.isFavorite) " ★" else ""),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) palette.primaryAccent else palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tagSuffix = if (summary.tags.isNotEmpty()) {
                                        " • " + summary.tags.take(2).joinToString(" ") { "#$it" }
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "${summary.pageCount} sayfa$tagSuffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.Check else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isCurrent) palette.primaryAccent else palette.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (courseNotes.size > 1 || !isCurrent) {
                                    IconButton(
                                        onClick = {
                                            showSubNotesMenu = false
                                            onDeleteSubNote(summary.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = stringResource(R.string.notes_delete_note),
                                            tint = palette.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showSubNotesMenu = false
                                if (!isCurrent) {
                                    onSwitchSubNote(summary.id)
                                }
                            }
                        )
                    }

                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.editor_subnotes_new),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.primaryAccent
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, tint = palette.primaryAccent)
                        },
                        onClick = {
                            showSubNotesMenu = false
                            onCreateSubNote()
                        }
                    )
                }
            }

            // Kompakt Araç Çubuğu (Odak Modunda Gizlenebilir)
            AnimatedVisibility(
                visible = !controller.isFocusMode,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1) Basit Kalem İkonu Butonu (2. tıkta Kalem Çeşitleri menüsü açılır)
                            Box {
                                val isPenActive = controller.activeTool.isPenVariant
                                CompactToolbarIconButton(
                                    icon = Icons.Default.Edit,
                                    contentDescription = penFullNameFor(controller.lastPenVariant),
                                    selected = isPenActive,
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

                            // 3) Silgi (2. tıkta Çizgi Silgisi / Alan Silgisi + Boyut menüsü)
                            Box {
                                val isEraserSelected = controller.activeTool == DrawingToolType.ERASER
                                CompactToolbarIconButton(
                                    icon = Icons.Default.AutoFixNormal,
                                    contentDescription = stringResource(R.string.editor_tool_eraser),
                                    selected = isEraserSelected,
                                    onClick = {
                                        if (isEraserSelected) {
                                            showEraserMenu = true
                                        } else {
                                            controller.selectTool(DrawingToolType.ERASER)
                                        }
                                    }
                                )

                                DropdownMenu(
                                    expanded = showEraserMenu,
                                    onDismissRequest = { showEraserMenu = false },
                                    modifier = Modifier.width(280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.editor_tool_eraser),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.textPrimary
                                        )
                                        EraserOptionRow(
                                            label = stringResource(R.string.editor_eraser_stroke_mode),
                                            selected = controller.eraserMode == EraserMode.STROKE_ERASER,
                                            onClick = { controller.eraserMode = EraserMode.STROKE_ERASER }
                                        )
                                        EraserOptionRow(
                                            label = stringResource(R.string.editor_eraser_area_mode),
                                            selected = controller.eraserMode == EraserMode.AREA_ERASER,
                                            onClick = { controller.eraserMode = EraserMode.AREA_ERASER }
                                        )

                                        HorizontalDivider()

                                        Text(
                                            text = stringResource(
                                                R.string.editor_eraser_size_label,
                                                controller.eraserRadius.roundToInt()
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textSecondary
                                        )
                                        Slider(
                                            value = controller.eraserRadius,
                                            onValueChange = { controller.eraserRadius = it },
                                            valueRange = 8f..60f
                                        )
                                    }
                                }
                            }

                            // 4) Lazer İşaretçi (2. tıkta ekranda kalma süresini seçme menüsü)
                            Box {
                                val isLaserSelected = controller.activeTool == DrawingToolType.LASER_POINTER
                                CompactToolbarIconButton(
                                    icon = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.editor_tool_laser),
                                    selected = isLaserSelected,
                                    onClick = {
                                        if (isLaserSelected) {
                                            showLaserMenu = true
                                        } else {
                                            controller.selectTool(DrawingToolType.LASER_POINTER)
                                        }
                                    }
                                )

                                DropdownMenu(
                                    expanded = showLaserMenu,
                                    onDismissRequest = { showLaserMenu = false },
                                    modifier = Modifier.width(230.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.editor_laser_duration_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.textPrimary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider()
                                    listOf(
                                        1500L to "1.5 Saniye",
                                        3000L to "3 Saniye",
                                        5000L to "5 Saniye",
                                        10000L to "10 Saniye"
                                    ).forEach { (durationMs, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            trailingIcon = {
                                                if (controller.laserDurationMillis == durationMs) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = {
                                                controller.laserDurationMillis = durationMs
                                                showLaserMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 5) Şekil Algılama ve Düzeltme Modu
                            CompactToolbarIconButton(
                                icon = Icons.Default.Category,
                                contentDescription = stringResource(R.string.editor_tool_shape),
                                selected = controller.activeTool == DrawingToolType.SHAPE_TOOL,
                                onClick = { controller.selectTool(DrawingToolType.SHAPE_TOOL) }
                            )

                            // 6) Kare Seçim Modu (Lasso / Box Selection)
                            CompactToolbarIconButton(
                                icon = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.editor_tool_lasso),
                                selected = controller.activeTool == DrawingToolType.LASSO_SELECT,
                                onClick = { controller.selectTool(DrawingToolType.LASSO_SELECT) }
                            )

                            // 7) Metin Kutusu (Text)
                            CompactToolbarIconButton(
                                icon = Icons.Default.TextFields,
                                contentDescription = stringResource(R.string.editor_tool_text),
                                selected = controller.activeTool == DrawingToolType.TEXT,
                                onClick = { controller.selectTool(DrawingToolType.TEXT) }
                            )

                            // 8) Galeriden Görsel Ekle (Fotoğraf / Ekran Görüntüsü)
                            CompactToolbarIconButton(
                                icon = Icons.Default.Image,
                                contentDescription = stringResource(R.string.editor_tool_gallery_image),
                                selected = false,
                                onClick = {
                                    pickGalleryImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            // 9) Sayfa Paneli (Sayfaları Yönet)
                            CompactToolbarIconButton(
                                icon = Icons.Default.ViewAgenda,
                                contentDescription = stringResource(R.string.editor_page_manager_btn),
                                selected = false,
                                onClick = { showPageManagerDialog = true }
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // 8) 3 Hızlı Erişim Renk Tuşu
                            controller.favoriteColors.forEachIndexed { slotIdx, colorArgb ->
                                val isSlotSelected = controller.selectedColorArgb == colorArgb
                                Box(
                                    modifier = Modifier
                                        .size(if (isSlotSelected) 25.dp else 21.dp)
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
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = stringResource(R.string.editor_color_picker_title),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // SAĞ: Geri Al (Undo) & İleri Al (Redo)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { controller.undo() },
                                enabled = controller.canUndo,
                                modifier = Modifier.size(32.dp)
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
                                modifier = Modifier.size(32.dp)
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
            }
        }

        // Seçim Modu Aktifken Çıkan Eylem Çubuğu
        AnimatedVisibility(visible = !controller.isFocusMode && controller.selectedStrokeIds.isNotEmpty()) {
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

        // Bekleyen Algılanmış Şekil Varsa Onay / Boyut Eylem Çubuğu
        val pendingShape = controller.pendingShape
        AnimatedVisibility(visible = !controller.isFocusMode && pendingShape != null) {
            if (pendingShape != null) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.primaryAccent),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Algılanan Şekil: ${pendingShape.labelTr}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.primaryAccent
                        )
                        TextButton(
                            onClick = { controller.confirmPendingShape() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Onayla", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = { controller.discardPendingShape() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("İptal", style = MaterialTheme.typography.labelMedium, color = palette.secondaryAccent)
                        }
                    }
                }
            }
        }

        // Seçili Görsel Eylem Çubuğu
        AnimatedVisibility(visible = !controller.isFocusMode && controller.selectedImageId != null) {
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
                    Text(
                        text = stringResource(R.string.editor_image_selected_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.primaryAccent
                    )
                    TextButton(
                        onClick = { controller.scaleSelectedImage(1.18f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_selection_scale_up), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { controller.scaleSelectedImage(0.84f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_selection_scale_down), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { controller.deleteSelectedImage() },
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
                        onClick = { controller.selectImage(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.editor_image_confirm), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (noticeMessageRes != null) {
            Surface(
                color = palette.surfaceElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, palette.primaryAccent),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(noticeMessageRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                    TextButton(
                        onClick = onDismissNotice,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.editor_json_debug_close), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 3. ANA DÜŞÜK GECİKMELİ KAĞIT TUVALİ + SAĞ ÜST SAYFA & YAKINLIK YÜZDESİ
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

            // Sol Üst: Kalem Yan Tuşu Aktif Göstergesi veya İçe/Dışa Aktarma Yükleniyor Rozeti
            if (controller.isStylusSideButtonHeld || isPortabilityBusy) {
                Surface(
                    color = palette.primaryAccent.copy(alpha = 0.92f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPortabilityBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = palette.onPrimaryAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.editor_stylus_held_badge_fmt,
                                    stylusActionLabel(controller.stylusSideButtonAction)
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = palette.onPrimaryAccent
                            )
                        }
                    }
                }
            }

            // Sayfanın Sağ Üstünde Sayfa Göstergesi ("Sayfa 1 / 2") + Yüzdelik Yakınlaştırma Göstergesi
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { showPageManagerDialog = true },
                    color = palette.surfaceElevated.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.borderSubtle)
                ) {
                    Text(
                        text = stringResource(
                            R.string.editor_page_badge,
                            controller.getCurrentVisiblePageIndex() + 1,
                            controller.pages.size.coerceAtLeast(1)
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    onClick = { controller.resetZoomAndPan() },
                    color = palette.surfaceElevated.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.borderSubtle)
                ) {
                    Text(
                        text = "%${(controller.zoom * 100).roundToInt()}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

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

    // --- DİYALOGLAR (Tamamı 24dp yuvarlatılmış dikdörtgen formunda!) ---

    if (showTitleDialog) {
        val suggestedTags = listOf("vize", "final", "ödev", "özet")
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = { Text(stringResource(R.string.editor_edit_title_dialog)) },
            text = {
                Column(
                    modifier = Modifier
                        .widthIn(min = 380.dp, max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { controller.toggleFavorite() },
                            shape = CircleShape,
                            color = if (controller.isFavorite) palette.primaryAccent.copy(alpha = 0.16f) else palette.surface,
                            border = BorderStroke(1.dp, if (controller.isFavorite) palette.primaryAccent else palette.borderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (controller.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (controller.isFavorite) palette.primaryAccent else palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.editor_meta_favorite),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary
                                )
                            }
                        }

                        Surface(
                            onClick = { controller.togglePinned() },
                            shape = CircleShape,
                            color = if (controller.isPinned) palette.primaryAccent.copy(alpha = 0.16f) else palette.surface,
                            border = BorderStroke(1.dp, if (controller.isPinned) palette.primaryAccent else palette.borderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (controller.isPinned) palette.primaryAccent else palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.editor_meta_pinned),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = stringResource(R.string.editor_meta_tags_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedTags.forEach { preset ->
                            val active = preset in controller.tags
                            Surface(
                                onClick = {
                                    if (active) controller.removeTag(preset) else controller.addTag(preset)
                                },
                                shape = CircleShape,
                                color = if (active) palette.primaryAccent else palette.surface,
                                border = BorderStroke(1.dp, if (active) palette.primaryAccent else palette.borderSubtle)
                            ) {
                                Text(
                                    text = "#$preset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) palette.onPrimaryAccent else palette.textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (controller.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            controller.tags.forEach { tag ->
                                Surface(
                                    onClick = { controller.removeTag(tag) },
                                    shape = CircleShape,
                                    color = palette.primaryAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, palette.primaryAccent)
                                ) {
                                    Text(
                                        text = "#$tag  ×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.primaryAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            label = { Text(stringResource(R.string.editor_meta_tag_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val normalized = TagNormalizer.normalize(newTagInput)
                                if (normalized.isNotEmpty()) {
                                    controller.addTag(normalized)
                                    newTagInput = ""
                                }
                            },
                            enabled = TagNormalizer.normalize(newTagInput).isNotEmpty(),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.editor_meta_tag_add))
                        }
                    }
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
                    },
                    shape = CircleShape
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

    if (showPageManagerDialog) {
        PageManagerDialog(
            documentSnapshot = controller.toNoteDocument(),
            currentVisiblePageIndex = controller.getCurrentVisiblePageIndex(),
            onJumpToPage = { idx -> controller.scrollToPage(idx) },
            onMovePageUp = { idx -> controller.movePage(idx, idx - 1) },
            onMovePageDown = { idx -> controller.movePage(idx, idx + 1) },
            onDuplicatePage = { idx -> controller.duplicatePage(idx) },
            onInsertBlankAfter = { idx -> controller.insertBlankPageAfter(idx) },
            onDeletePage = { idx -> controller.deletePage(idx) },
            onAddBlankAtEnd = { controller.addPage(scrollToNewPage = true) },
            onImportPdfPages = { importPdfPagesLauncher.launch(arrayOf("application/pdf")) },
            onDismiss = { showPageManagerDialog = false }
        )
    }

    if (showStylusButtonDialog) {
        AlertDialog(
            onDismissRequest = { showStylusButtonDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = { Text(stringResource(R.string.editor_stylus_button_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StylusButtonAction.entries.forEach { action ->
                        EraserOptionRow(
                            label = stylusActionLabel(action),
                            selected = controller.stylusSideButtonAction == action,
                            onClick = {
                                onStylusSideButtonActionChange(action)
                                showStylusButtonDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStylusButtonDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }

    if (showPaperSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPaperSettingsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
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

    if (showColorCustomDialog) {
        AlertDialog(
            onDismissRequest = { showColorCustomDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
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

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
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

    if (showJsonDebugDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDebugDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
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
private fun EraserOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) palette.primaryAccent.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) palette.primaryAccent else palette.textPrimary
            )
            if (selected) {
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
private fun penFullNameFor(tool: DrawingToolType): String = when (tool) {
    DrawingToolType.FINE_FOUNTAIN -> stringResource(R.string.editor_pen_fine_fountain)
    DrawingToolType.CALLIGRAPHY -> stringResource(R.string.editor_pen_calligraphy)
    DrawingToolType.PEN -> stringResource(R.string.editor_pen_ballpoint)
    DrawingToolType.PENCIL -> stringResource(R.string.editor_pen_pencil)
    DrawingToolType.MARKER -> stringResource(R.string.editor_pen_marker)
    DrawingToolType.WATERCOLOR -> stringResource(R.string.editor_pen_watercolor)
    else -> stringResource(R.string.editor_tool_pen)
}

@Composable
private fun stylusActionLabel(action: StylusButtonAction): String = when (action) {
    StylusButtonAction.ERASER -> stringResource(R.string.editor_stylus_action_eraser)
    StylusButtonAction.LASSO_SELECT -> stringResource(R.string.editor_stylus_action_lasso)
    StylusButtonAction.LASER_POINTER -> stringResource(R.string.editor_stylus_action_laser)
    StylusButtonAction.DISABLED -> stringResource(R.string.editor_stylus_action_disabled)
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
