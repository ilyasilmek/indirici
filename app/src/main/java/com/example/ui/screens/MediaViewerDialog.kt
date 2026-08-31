package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DownloadEntity
import com.example.data.model.FileType
import com.example.service.NotificationHelper
import com.example.ui.components.FileTypeBadge
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniNeonTeal
import com.example.ui.theme.OmniViolet

@Composable
fun MediaViewerDialog(
    item: DownloadEntity,
    onDismiss: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var playbackProgress by remember { mutableFloatStateOf(0.35f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, OmniViolet.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("media_viewer_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FileTypeBadge(item.fileType)

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_media_viewer")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Media Display Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.fileType) {
                        FileType.VIDEO -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(OmniViolet.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video",
                                        tint = OmniCyan,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OmniGet HD Medya Oynatıcı",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "1080p 60 FPS • H.264 / AAC Akışı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OmniCyan
                                )
                            }
                        }
                        FileType.AUDIO -> {
                            // Sound wave equalizer bars animation
                            val transition = rememberInfiniteTransition(label = "eq")
                            val bar1 by transition.animateFloat(
                                initialValue = 0.2f, targetValue = 0.9f,
                                animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1"
                            )
                            val bar2 by transition.animateFloat(
                                initialValue = 0.8f, targetValue = 0.3f,
                                animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b2"
                            )
                            val bar3 by transition.animateFloat(
                                initialValue = 0.4f, targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b3"
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Box(modifier = Modifier.width(6.dp).height((45 * bar1).dp).clip(RoundedCornerShape(3.dp)).background(OmniCyan))
                                    Box(modifier = Modifier.width(6.dp).height((45 * bar2).dp).clip(RoundedCornerShape(3.dp)).background(OmniViolet))
                                    Box(modifier = Modifier.width(6.dp).height((45 * bar3).dp).clip(RoundedCornerShape(3.dp)).background(OmniNeonTeal))
                                    Box(modifier = Modifier.width(6.dp).height((45 * bar1).dp).clip(RoundedCornerShape(3.dp)).background(OmniEmerald))
                                    Box(modifier = Modifier.width(6.dp).height((45 * bar2).dp).clip(RoundedCornerShape(3.dp)).background(OmniCyan))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "320 kbps Stüdyo Ses Akışı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OmniCyan
                                )
                            }
                        }
                        FileType.COURSE -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.School, contentDescription = "Kurs", tint = OmniEmerald, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("OmniGet Çevrimdışı Kurs Oynatıcı", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Text("Modül & Ders İçeriği Hazır", style = MaterialTheme.typography.labelSmall, color = OmniEmerald)
                            }
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = "Dosya", tint = OmniViolet, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Belge ve Arşiv Önizleme", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & File Info
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Boyut: ${NotificationHelper.formatFileSize(item.downloadedBytes)} • ${item.status.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrub Slider
                Slider(
                    value = playbackProgress,
                    onValueChange = { playbackProgress = it },
                    colors = SliderDefaults.colors(
                        thumbColor = OmniViolet,
                        activeTrackColor = OmniViolet,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playback_scrub_slider")
                )

                // Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "02:15", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "06:40", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Media Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = { playbackProgress = (playbackProgress - 0.1f).coerceAtLeast(0f) }) {
                        Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Geri Sar", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(OmniViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.testTag("toggle_play_button")) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    IconButton(onClick = { playbackProgress = (playbackProgress + 0.1f).coerceAtMost(1f) }) {
                        Icon(imageVector = Icons.Default.FastForward, contentDescription = "İleri Sar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Kapat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
