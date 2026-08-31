package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.MediaInspectResult
import com.example.data.model.MediaQualityOption
import com.example.engine.UrlInspector
import com.example.ui.components.FileTypeBadge
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniEmerald
import com.example.ui.theme.OmniNeonTeal
import com.example.ui.theme.OmniRose
import com.example.ui.theme.OmniViolet
import com.example.ui.viewmodel.OmniGetViewModel

@Composable
fun DownloaderScreen(
    viewModel: OmniGetViewModel,
    onNavigateToActive: () -> Unit
) {
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()
    val inspectResult by viewModel.inspectResult.collectAsStateWithLifecycle()
    val inspectError by viewModel.inspectError.collectAsStateWithLifecycle()
    val selectedThreads by viewModel.selectedThreads.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero Quick Intro Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(OmniViolet.copy(alpha = 0.5f), OmniCyan.copy(alpha = 0.5f)))
                ),
                modifier = Modifier.fillMaxWidth().testTag("hero_banner_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(OmniViolet, OmniCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = "Turbo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Çoklu Bağlantılı Hızlı İndirici",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Video, ses, kurs, e-kitap ve direkt URL bağlantılarını analiz edip turbo hızda indirin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // URL Input & Actions Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().testTag("url_input_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "İndirilecek Bağlantıyı Girin",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // TextField with Paste / Clear icons
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.onUrlInputChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_text_input"),
                        placeholder = {
                            Text(
                                "https://... (Video, ses, kurs, dosya URL'si)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                tint = OmniCyan
                            )
                        },
                        trailingIcon = {
                            Row {
                                if (urlInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.onUrlInputChanged("") },
                                        modifier = Modifier.testTag("clear_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Temizle",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            viewModel.onUrlInputChanged(clipText)
                                        }
                                    },
                                    modifier = Modifier.testTag("paste_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Yapıştır",
                                        tint = OmniViolet
                                    )
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-Thread Speed Booster Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Threads",
                                tint = OmniEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hızlandırma:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 4, 8).forEach { threads ->
                                val isSelected = selectedThreads == threads
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) OmniViolet else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable { viewModel.setThreadsCount(threads) }
                                        .testTag("thread_pill_$threads")
                                ) {
                                    Text(
                                        text = "${threads}x ${if (threads == 8) "Turbo" else ""}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Inspect Button
                    Button(
                        onClick = { viewModel.inspectUrl() },
                        enabled = !isInspecting && urlInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inspect_url_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniViolet,
                            contentColor = Color.White
                        )
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Medya & Akış Analiz Ediliyor...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Inspect",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bağlantıyı Analiz Et ve İndir", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Error text if inspect fails
                    if (inspectError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OmniRose.copy(alpha = 0.1f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Hata",
                                tint = OmniRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = inspectError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OmniRose
                            )
                        }
                    }
                }
            }
        }

        // Inspect Result Dialog / Card
        if (inspectResult != null) {
            item {
                InspectResultCard(
                    result = inspectResult!!,
                    onStartDownload = { quality ->
                        viewModel.startDownloadWithQuality(inspectResult!!, quality)
                        onNavigateToActive()
                    },
                    onStartBatchCourse = {
                        viewModel.startBatchCourseDownload(inspectResult!!)
                        onNavigateToActive()
                    },
                    onCancel = { viewModel.clearInspectResult() }
                )
            }
        }

        // Quick Presets and Sample Downloads Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Presets",
                        tint = OmniCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hızlı Test ve Örnek Medyalar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Tek Tıkla İndir",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Preset Items
        items(UrlInspector.PRESET_MEDIA_ITEMS) { preset ->
            PresetDownloadCard(
                preset = preset,
                onSelectPreset = {
                    viewModel.startQuickPresetDownload(preset)
                    onNavigateToActive()
                },
                onInspectPreset = {
                    viewModel.onUrlInputChanged(preset.originalUrl)
                    viewModel.inspectUrl(preset.originalUrl)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InspectResultCard(
    result: MediaInspectResult,
    onStartDownload: (MediaQualityOption) -> Unit,
    onStartBatchCourse: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedOptionId by remember(result) {
        mutableStateOf(result.qualityOptions.firstOrNull()?.id ?: "")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OmniViolet),
        modifier = Modifier.fillMaxWidth().testTag("inspect_result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FileTypeBadge(result.fileType)

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp).testTag("close_inspect_card")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thumbnail and Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (!result.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.thumbnailUrl,
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Kaynak: ${result.hostPlatform}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniCyan
                    )

                    if (result.durationText != null) {
                        Text(
                            text = "Süre / İçerik: ${result.durationText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Boyut: ${result.totalSizeText} • Çoklu Bağlantı: ${if (result.supportsMultiThread) "Destekleniyor" else "Standart"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quality / Format Choices
            Text(
                text = "İndirme Kalitesi ve Formatı Seçin:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            result.qualityOptions.forEach { option ->
                val isSelected = selectedOptionId == option.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) OmniViolet.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) OmniViolet else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { selectedOptionId = option.id }
                        .testTag("quality_option_${option.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedOptionId = option.id },
                                colors = RadioButtonDefaults.colors(selectedColor = OmniViolet)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${option.resolution} • ${option.format}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = option.estimatedSize,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = OmniEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Course Batch Option if available
            if (result.isCourseBundle && result.courseLessons.isNotEmpty()) {
                Button(
                    onClick = onStartBatchCourse,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("start_batch_course_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniEmerald)
                ) {
                    Icon(imageVector = Icons.Default.School, contentDescription = "Kurs", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tüm Kurs Modüllerini Sıraya Ekle (${result.courseLessons.size} Ders)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Start Download Button
            Button(
                onClick = {
                    val chosen = result.qualityOptions.find { it.id == selectedOptionId } ?: result.qualityOptions.firstOrNull()
                    if (chosen != null) {
                        onStartDownload(chosen)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_download_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OmniViolet)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "İndir", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hemen İndirmeye Başla", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PresetDownloadCard(
    preset: MediaInspectResult,
    onSelectPreset: () -> Unit,
    onInspectPreset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().testTag("preset_card_${preset.fileType.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!preset.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = preset.thumbnailUrl,
                    contentDescription = preset.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FileTypeBadge(preset.fileType)
                    Text(
                        text = preset.totalSizeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = OmniEmerald
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Text(
                    text = preset.hostPlatform,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick One-Tap Download Button
            IconButton(
                onClick = onSelectPreset,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OmniViolet.copy(alpha = 0.15f))
                    .testTag("preset_quick_download_${preset.fileType.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Hızlı İndir",
                    tint = OmniViolet,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
