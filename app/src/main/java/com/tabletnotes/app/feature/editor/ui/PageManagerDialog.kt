package com.tabletnotes.app.feature.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.feature.format.thumbnail.NoteThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PageManagerDialog(
    documentSnapshot: NoteDocument,
    currentVisiblePageIndex: Int,
    onJumpToPage: (Int) -> Unit,
    onMovePageUp: (Int) -> Unit,
    onMovePageDown: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onInsertBlankAfter: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddBlankAtEnd: () -> Unit,
    onImportPdfPages: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val pages = documentSnapshot.pages

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_page_manager_title, pages.size),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = palette.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp, max = 680.dp)
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pages, key = { it.pageIndex }) { page ->
                        val isCurrent = page.pageIndex == currentVisiblePageIndex
                        val thumbState = produceState<Bitmap?>(
                            initialValue = null,
                            key1 = documentSnapshot.updatedAtMillis,
                            key2 = page.pageIndex,
                            key3 = pages.size
                        ) {
                            value = withContext(Dispatchers.Default) {
                                runCatching {
                                    NoteThumbnailGenerator.renderSinglePageBitmap(
                                        document = documentSnapshot,
                                        pageIndex = page.pageIndex,
                                        targetWidthPx = 240,
                                        targetHeightPx = 320,
                                        maxStrokesLimit = 450
                                    )
                                }.getOrNull()
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = palette.surface,
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) palette.primaryAccent else palette.borderSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.appBackground)
                                        .clickable {
                                            onJumpToPage(page.pageIndex)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bmp = thumbState.value
                                    if (bmp != null && !bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = stringResource(R.string.editor_page_item_label, page.pageIndex + 1),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Surface(
                                        color = if (isCurrent) palette.primaryAccent else palette.surfaceElevated.copy(alpha = 0.88f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "#${page.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCurrent) palette.onPrimaryAccent else palette.textPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Kompakt Sayfa İşlem Düğmeleri
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onMovePageUp(page.pageIndex) },
                                        enabled = page.pageIndex > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = stringResource(R.string.editor_page_move_up),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMovePageDown(page.pageIndex) },
                                        enabled = page.pageIndex < pages.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = stringResource(R.string.editor_page_move_down),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDuplicatePage(page.pageIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.editor_page_duplicate),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onInsertBlankAfter(page.pageIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PostAdd,
                                            contentDescription = stringResource(R.string.editor_page_insert_after),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePage(page.pageIndex) },
                                        enabled = pages.size > 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = stringResource(R.string.editor_page_delete),
                                            tint = if (pages.size > 1) palette.secondaryAccent else palette.textMuted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onAddBlankAtEnd()
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_page_add_blank))
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onImportPdfPages()
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_page_add_pdf))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_json_debug_close))
            }
        }
    )
}
