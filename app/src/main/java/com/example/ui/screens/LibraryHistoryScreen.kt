package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType
import com.example.service.NotificationHelper
import com.example.ui.components.DownloadStatusBadge
import com.example.ui.components.FileTypeBadge
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniRose
import com.example.ui.theme.OmniViolet
import com.example.ui.viewmodel.OmniGetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryHistoryScreen(
    viewModel: OmniGetViewModel,
    onNavigateToDownloader: () -> Unit
) {
    val context = LocalContext.current
    val filteredList by viewModel.filteredLibrary.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var itemDetailsToShow by remember { mutableStateOf<DownloadEntity?>(null) }

    val totalStorageUsedBytes = remember(allDownloads) {
        allDownloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.downloadedBytes }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
            placeholder = { Text("İndirilen dosyalarda ara...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Ara", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OmniViolet,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { viewModel.setCategoryFilter(null) },
                label = { Text("Tümü (${allDownloads.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OmniViolet,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("filter_all")
            )

            listOf(
                Pair(FileType.VIDEO, "Videolar"),
                Pair(FileType.AUDIO, "Ses/Müzik"),
                Pair(FileType.COURSE, "Kurslar"),
                Pair(FileType.DOCUMENT, "Belgeler"),
                Pair(FileType.ARCHIVE, "Arşivler")
            ).forEach { (type, label) ->
                val count = allDownloads.count { it.fileType == type }
                FilterChip(
                    selected = selectedCategory == type,
                    onClick = { viewModel.setCategoryFilter(if (selectedCategory == type) null else type) },
                    label = { Text("$label ($count)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OmniViolet,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_${type.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Storage & Clear Summary Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().testTag("storage_summary_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Depolama",
                        tint = OmniCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Toplam İndirilen Alan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = NotificationHelper.formatFileSize(totalStorageUsedBytes),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = OmniEmerald
                        )
                    }
                }

                if (allDownloads.any { it.status == DownloadStatus.COMPLETED }) {
                    TextButton(
                        onClick = { viewModel.clearCompleted() },
                        modifier = Modifier.testTag("clear_completed_history_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Temizle", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Temizle", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Items List or Empty State
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Boş",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (searchQuery.isNotBlank()) "Aramaya uygun dosya bulunamadı" else "Henüz İndirme Geçmişi Yok",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tamamlanan veya indirilen tüm video, müzik, kurs ve belgeleriniz burada listelenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    LibraryHistoryItemCard(
                        item = item,
                        onOpen = { viewModel.openMediaPreview(item) },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, item.fileName)
                                putExtra(Intent.EXTRA_TEXT, "OmniGet ile indirildi: ${item.fileName}\nKaynak: ${item.url}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Dosyayı Paylaş"))
                        },
                        onShowDetails = { itemDetailsToShow = item },
                        onDelete = { itemToDelete = item }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Dosyayı Sil", fontWeight = FontWeight.Bold) },
            text = { Text("\"${target.fileName}\" dosyasını silmek istediğinize emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownload(target.id, deleteFileFromDisk = true)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniRose)
                ) {
                    Text("Tamamen Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // Details Info Dialog
    if (itemDetailsToShow != null) {
        val detail = itemDetailsToShow!!
        AlertDialog(
            onDismissRequest = { itemDetailsToShow = null },
            title = { Text("Dosya Detayları", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ad: ${detail.fileName}", fontWeight = FontWeight.SemiBold)
                    Text("Boyut: ${NotificationHelper.formatFileSize(detail.downloadedBytes)}")
                    Text("Durum: ${detail.status.name}")
                    Text("Tarih: ${formatDate(detail.completedAt ?: detail.createdAt)}")
                    Text("Konum: ${if (detail.filePath.isNotBlank()) detail.filePath else "Uygulama İndirmeleri"}")
                    Text("Kaynak: ${detail.url}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { itemDetailsToShow = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
fun LibraryHistoryItemCard(
    item: DownloadEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("history_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FileTypeBadge(item.fileType)
                DownloadStatusBadge(item.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Boyut: ${NotificationHelper.formatFileSize(item.downloadedBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OmniEmerald
                )

                Text(
                    text = formatDate(item.completedAt ?: item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play / Open button
                Button(
                    onClick = onOpen,
                    modifier = Modifier.height(36.dp).testTag("play_media_${item.id}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniViolet)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Oynat", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (item.fileType == FileType.VIDEO || item.fileType == FileType.AUDIO) "Oynat" else "Görüntüle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Paylaş", tint = OmniCyan, modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = onShowDetails, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Detay", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = OmniRose, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
