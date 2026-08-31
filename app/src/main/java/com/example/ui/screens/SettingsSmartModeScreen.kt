package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NetworkSettings
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniNeonTeal
import com.example.ui.theme.OmniViolet
import com.example.ui.viewmodel.OmniGetViewModel

@Composable
fun SettingsSmartModeScreen(
    viewModel: OmniGetViewModel
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Smart Network Optimizer Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(listOf(OmniViolet, OmniCyan))
                ),
                modifier = Modifier.fillMaxWidth().testTag("smart_network_mode_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(OmniViolet, OmniCyan))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Smart Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Akıllı Ağ Optimize Modu",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Otomatik bant genişliği ve kota tasarrufu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.smartNetworkMode,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(smartNetworkMode = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OmniViolet
                            ),
                            modifier = Modifier.testTag("smart_mode_switch")
                        )
                    }

                    if (settings.smartNetworkMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "✓ Mobil verideyken hız stabilizasyonu sağlar\n✓ Wi-Fi ağına bağlandığında otomatik turbo hıza geçer\n✓ Ağ kopmasında otomatik olarak kaldığı yerden devam eder",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = OmniNeonTeal
                        )
                    }
                }
            }
        }

        // Network & Concurrency Settings
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().testTag("network_concurrency_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "İndirme ve Ağ Yapılandırması",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wi-Fi Only Switch
                    SettingSwitchRow(
                        icon = Icons.Default.Wifi,
                        title = "Yalnızca Wi-Fi ile İndir",
                        subtitle = "Mobil veri tüketimini engellemek için sadece Wi-Fi kullanılır",
                        checked = settings.wifiOnly,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(wifiOnly = it)) },
                        testTag = "wifi_only_switch"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Max Concurrent Downloads Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Eşzamanlı İndirme Sayısı",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${settings.maxConcurrentDownloads} Dosya",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OmniViolet
                            )
                        }
                        Slider(
                            value = settings.maxConcurrentDownloads.toFloat(),
                            onValueChange = {
                                viewModel.updateSettings(settings.copy(maxConcurrentDownloads = it.toInt()))
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = OmniViolet,
                                activeTrackColor = OmniViolet
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("concurrent_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Threads per file
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Dosya Başına Parçalı Bağlantı (Multi-Thread)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${settings.connectionThreadsPerFile}x Segment",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OmniCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 4, 8).forEach { threadCount ->
                                val selected = settings.connectionThreadsPerFile == threadCount
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) OmniCyan else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.updateSettings(settings.copy(connectionThreadsPerFile = threadCount))
                                        }
                                        .testTag("setting_thread_$threadCount")
                                ) {
                                    Text(
                                        text = "${threadCount}x",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Speed limit
                    Column {
                        Text(
                            text = "Bant Genişliği Hız Sınırı (Throttle)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(0, "Sınırsız"),
                                Pair(500, "500 KB/s"),
                                Pair(2000, "2 MB/s"),
                                Pair(5000, "5 MB/s")
                            ).forEach { (kbps, label) ->
                                val selected = settings.speedLimitKbps == kbps
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) OmniEmerald else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.updateSettings(settings.copy(speedLimitKbps = kbps))
                                        }
                                        .testTag("setting_speed_$kbps")
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notification Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().testTag("notifications_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Arka Plan & Bildirim Sistemi",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Tamamlanma Bildirim Sesi",
                        subtitle = "İndirme bittiğinde sesli uyarı ver",
                        checked = settings.notificationSound,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(notificationSound = it)) },
                        testTag = "sound_switch"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Vibration,
                        title = "Titreşim Uyarısı",
                        subtitle = "İndirme tamamlandığında titreşimle bilgilendir",
                        checked = settings.notificationVibration,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(notificationVibration = it)) },
                        testTag = "vibration_switch"
                    )
                }
            }
        }

        // Cross-Platform & OmniGet GitHub Info Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, OmniViolet.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("cross_platform_info_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Code, contentDescription = "Repo", tint = OmniViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OmniGet Cross-Platform & Build Bilgisi",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Bu mobil uygulama 'tonhowtf/omniget' projesinin tüm gelişmiş indirme motoru, kurs modülü indirici, çoklu segment hızlandırma ve yerel Room veritabanı özelliklerini Android ve iOS için optimize edilmiş Jetpack Compose mimarisiyle sunar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Android, contentDescription = "Android", tint = OmniEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Android (Kotlin)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.PhoneIphone, contentDescription = "iOS", tint = OmniCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("iOS Uyumlu UI", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tonhowtf/omniget"))
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("open_github_repo_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniViolet)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "GitHub", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitHub Reposunu Ziyaret Et (tonhowtf/omniget)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = OmniCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OmniViolet
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
