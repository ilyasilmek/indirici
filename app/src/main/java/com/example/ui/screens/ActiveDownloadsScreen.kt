package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.service.NotificationHelper
import com.example.ui.components.AnimatedProgressBar
import com.example.ui.components.DownloadStatusBadge
import com.example.ui.components.FileTypeBadge
import com.example.ui.theme.OmniAmber
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniNeonTeal
import com.example.ui.theme.OmniRose
import com.example.ui.theme.OmniViolet
import com.example.ui.viewmodel.OmniGetViewModel

@Composable
fun ActiveDownloadsScreen(
    viewModel: OmniGetViewModel,
    onNavigateToDownloader: () -> Unit
) {
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val totalSpeed by viewModel.totalSpeedFormatted.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Aggregate Speedometer & Global Controls Header
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().testTag("active_summary_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Aktif İndirme Kuyruğu",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${activeDownloads.size} Görev • Toplam Hız: $totalSpeed",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniNeonTeal
                        )
                    }

                    // Multi-connection badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = OmniViolet.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Turbo",
                                tint = OmniViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Turbo Segment",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OmniViolet
                            )
                        }
                    }
                }

                if (activeDownloads.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.pauseAll() },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("pause_all_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Durdur", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tümünü Duraklat", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.resumeAll() },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("resume_all_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmniViolet)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Başlat", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tümünü Başlat", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeDownloads.isEmpty()) {
            // Empty State
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
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "Boş",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Aktif İndirme Bulunmuyor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "İndirici sekmesinden video, ses veya dosya bağlantısı ekleyerek turbo indirmeyi başlatabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onNavigateToDownloader,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniViolet),
                        modifier = Modifier.testTag("empty_add_download_button")
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Ekle")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yeni İndirme Başlat")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeDownloads, key = { it.id }) { item ->
                    ActiveDownloadItemCard(
                        item = item,
                        onPause = { viewModel.pauseDownload(item.id) },
                        onResume = { viewModel.resumeDownload(item.id) },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onRetry = { viewModel.retryDownload(item.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.status == DownloadStatus.DOWNLOADING) OmniCyan.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("active_task_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: File Type + Title + Status + Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeBadge(item.fileType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                DownloadStatusBadge(item.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Progress Bar
            AnimatedProgressBar(
                progress = item.progressPercent,
                status = item.status
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics row: Downloaded / Total size, Percent, Speed, ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Size & Percent
                val downloadedStr = NotificationHelper.formatFileSize(item.downloadedBytes)
                val totalStr = if (item.totalBytes > 0) NotificationHelper.formatFileSize(item.totalBytes) else "--"

                Text(
                    text = "$downloadedStr / $totalStr (${item.progressPercentInt}%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Speed & ETA
                if (item.status == DownloadStatus.DOWNLOADING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = NotificationHelper.formatSpeed(item.speedBytesPerSec),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OmniNeonTeal
                        )

                        if (item.etaSeconds > 0) {
                            Text(
                                text = "• ${formatEta(item.etaSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (item.status == DownloadStatus.PAUSED) {
                    Text(
                        text = "Duraklatıldı",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Thread count badge
                Text(
                    text = "${item.threadsCount}x Multi-Thread",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniViolet
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(32.dp).testTag("pause_task_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Duraklat",
                                tint = OmniAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.QUEUED) {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(32.dp).testTag("resume_task_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Devam Et",
                                tint = OmniEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (item.status == DownloadStatus.FAILED) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(32.dp).testTag("retry_task_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yeniden Dene",
                                tint = OmniViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp).testTag("cancel_task_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "İptal Et",
                            tint = OmniRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "$seconds sn"
        seconds < 3600 -> "${seconds / 60} dk ${seconds % 60} sn"
        else -> "${seconds / 3600} sa ${(seconds % 3600) / 60} dk"
    }
}
