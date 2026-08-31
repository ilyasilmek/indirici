package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType
import com.example.ui.theme.OmniAmber
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniNeonTeal
import com.example.ui.theme.OmniRose
import com.example.ui.theme.OmniViolet

@Composable
fun OmniGetTopAppBar(
    totalSpeed: String,
    activeCount: Int,
    isWifi: Boolean,
    smartModeActive: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().testTag("omniget_top_app_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Title & Logo
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(OmniViolet, OmniCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "OmniGet Logo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Omni",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Get",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = OmniCyan
                        )
                    }
                    Text(
                        text = "Turbo Downloader",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Badges (Speed & Network)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Speed Pill
                if (activeCount > 0) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "speedPulse"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = OmniNeonTeal.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OmniNeonTeal.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OmniNeonTeal.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = totalSpeed,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OmniNeonTeal
                            )
                        }
                    }
                }

                // Smart Mode / Wifi Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (smartModeActive) OmniViolet.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Ağ Durumu",
                            tint = if (isWifi) OmniEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        if (smartModeActive) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Akıllı",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = OmniViolet
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileTypeBadge(fileType: FileType) {
    val (color, icon, text) = when (fileType) {
        FileType.VIDEO -> Triple(OmniViolet, Icons.Default.Videocam, "Video")
        FileType.AUDIO -> Triple(OmniCyan, Icons.Default.Audiotrack, "Ses/Müzik")
        FileType.COURSE -> Triple(OmniEmerald, Icons.Default.School, "Kurs")
        FileType.DOCUMENT -> Triple(OmniNeonTeal, Icons.Default.Description, "Belge")
        FileType.ARCHIVE -> Triple(OmniAmber, Icons.Default.Archive, "Arşiv/Paket")
        FileType.IMAGE -> Triple(OmniRose, Icons.Default.Image, "Görsel")
        FileType.OTHER -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Download, "Dosya")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = color
            )
        }
    }
}

@Composable
fun DownloadStatusBadge(status: DownloadStatus) {
    val (bg, fg, label) = when (status) {
        DownloadStatus.DOWNLOADING -> Triple(OmniEmerald.copy(alpha = 0.15f), OmniEmerald, "İndiriliyor")
        DownloadStatus.QUEUED -> Triple(OmniCyan.copy(alpha = 0.15f), OmniCyan, "Sırada")
        DownloadStatus.PAUSED -> Triple(OmniAmber.copy(alpha = 0.15f), OmniAmber, "Duraklatıldı")
        DownloadStatus.COMPLETED -> Triple(OmniEmerald.copy(alpha = 0.15f), OmniEmerald, "Tamamlandı")
        DownloadStatus.FAILED -> Triple(OmniRose.copy(alpha = 0.15f), OmniRose, "Hata")
        DownloadStatus.CANCELLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "İptal")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    status: DownloadStatus,
    modifier: Modifier = Modifier
) {
    val barColor = when (status) {
        DownloadStatus.DOWNLOADING -> OmniCyan
        DownloadStatus.PAUSED -> OmniAmber
        DownloadStatus.COMPLETED -> OmniEmerald
        DownloadStatus.FAILED -> OmniRose
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(OmniViolet, barColor)
                    )
                )
        )
    }
}
