package com.example.instamediasaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.instamediasaver.R
import com.example.instamediasaver.model.ErrorCode
import com.example.instamediasaver.model.MediaItem
import com.example.instamediasaver.model.MediaKind
import com.example.instamediasaver.ui.HomeUiState

@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit,
    onFetchPreview: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpenSavedFile: (String, String?) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // URL Input Field
        item {
            OutlinedTextField(
                value = uiState.urlInput,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.url_input_label)) },
                placeholder = { Text(stringResource(R.string.url_input_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Large Action Buttons: Paste, Clear, Preview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onUrlChange(clip)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_paste), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(0.7f)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_clear))
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onFetchPreview,
                enabled = !uiState.isResolving && !uiState.downloadProgress.isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.fetching_media), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_fetch_preview), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Understandable Error Card
        if (uiState.errorCode != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val localizedErr = when (uiState.errorCode) {
                                ErrorCode.EMPTY_URL -> stringResource(R.string.err_empty_url)
                                ErrorCode.INVALID_INSTAGRAM_URL -> stringResource(R.string.err_invalid_url)
                                ErrorCode.NO_API_CONFIGURED -> stringResource(R.string.err_no_api_configured)
                                ErrorCode.PRIVATE_OR_NOT_FOUND -> stringResource(R.string.err_private_or_not_found)
                                ErrorCode.RATE_LIMITED -> "Instagram API rate limit reached (HTTP 429). Please wait before retrying."
                                ErrorCode.NETWORK_ERROR -> "Network connection error. Check your internet or Backend Proxy URL."
                                ErrorCode.SERVER_ERROR -> "Server error while resolving post."
                            }
                            Text(
                                text = localizedErr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!uiState.errorDetails.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = uiState.errorDetails,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Download Progress & Completion Banner
        val prog = uiState.downloadProgress
        if (prog.isDownloading || prog.completedCount > 0 || prog.failedCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (prog.isDownloading) {
                            Text(
                                text = stringResource(
                                    R.string.downloading_progress,
                                    prog.currentItemIndex,
                                    prog.totalItems,
                                    prog.currentItemPercent
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { prog.currentItemPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            if (prog.completedCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.download_success_summary,
                                            prog.completedCount
                                        ),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (prog.lastSavedUri != null) {
                                    Spacer(Modifier.height(10.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            onOpenSavedFile(prog.lastSavedUri, prog.lastSavedMimeType)
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_open_in_gallery))
                                    }
                                }
                            }
                            if (!prog.errorMessage.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = prog.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Resolved Post Header, Selection Controls & Carousel Preview Cards
        val post = uiState.resolvedPost
        if (post != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (!post.authorUsername.isNullOrBlank()) {
                                stringResource(R.string.post_by_author, post.authorUsername)
                            } else {
                                "${post.mediaTypeLabel} (${post.shortcode})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = post.resolutionSource,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (!post.caption.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = post.caption,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!post.noticeMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = post.noticeMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        val downloadableCount = post.items.count { it.isDownloadable && !it.mediaUrl.isNullOrBlank() }
                        Text(
                            text = stringResource(
                                R.string.carousel_items_count,
                                post.items.size,
                                uiState.selectedItemIds.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        if (post.items.size > 1 && downloadableCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onSelectAll,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.btn_select_all))
                                }
                                OutlinedButton(
                                    onClick = onDeselectAll,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.btn_deselect_all))
                                }
                            }
                        }

                        if (downloadableCount > 0) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = onDownloadSelected,
                                    enabled = uiState.selectedItemIds.isNotEmpty() && !prog.isDownloading,
                                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(
                                            R.string.btn_download_selected,
                                            uiState.selectedItemIds.size
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = onDownloadAll,
                                    enabled = !prog.isDownloading,
                                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.btn_download_all, downloadableCount),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(post.items, key = { it.id }) { mediaItem ->
                MediaItemPreviewCard(
                    item = mediaItem,
                    isSelected = mediaItem.id in uiState.selectedItemIds,
                    onToggle = { onToggleItem(mediaItem.id) }
                )
            }
        }
    }
}

@Composable
private fun MediaItemPreviewCard(
    item: MediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isDownloadable) { onToggle() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (item.kind == MediaKind.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${item.index} • " + when {
                            !item.isDownloadable -> stringResource(R.string.badge_unavailable)
                            item.kind == MediaKind.VIDEO -> stringResource(R.string.badge_video)
                            else -> stringResource(R.string.badge_photo)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.width != null && item.height != null) {
                        Text(
                            text = "${item.width} × ${item.height} px (Original Quality)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.isDownloadable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.restrictionReason ?: stringResource(R.string.item_unavailable_reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (item.isDownloadable) onToggle() },
                    enabled = item.isDownloadable
                )
            }
        }
    }
}
