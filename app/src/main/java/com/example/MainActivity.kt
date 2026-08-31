package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.OmniGetTopAppBar
import com.example.ui.screens.ActiveDownloadsScreen
import com.example.ui.screens.DownloaderScreen
import com.example.ui.screens.LibraryHistoryScreen
import com.example.ui.screens.SettingsSmartModeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.OmniViolet
import com.example.ui.viewmodel.OmniGetViewModel

enum class NavigationTab(val title: String, val icon: ImageVector, val tag: String) {
    DOWNLOADER("İndirici", Icons.Default.CloudDownload, "nav_downloader"),
    ACTIVE("Aktif", Icons.Default.Speed, "nav_active"),
    LIBRARY("Kütüphane", Icons.Default.Folder, "nav_library"),
    SETTINGS("Akıllı Ağ", Icons.Default.AutoAwesome, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: OmniGetViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                OmniGetMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleSharedUrl(sharedText)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun OmniGetMainApp(viewModel: OmniGetViewModel) {
    var currentTab by remember { mutableStateOf(NavigationTab.DOWNLOADER) }
    val snackbarHostState = remember { SnackbarHostState() }

    val snackMessage by viewModel.snackMessage.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val totalSpeed by viewModel.totalSpeedFormatted.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val navigateToDownloaderEvent by viewModel.navigateToDownloaderEvent.collectAsStateWithLifecycle()

    val isWifi = viewModel.isWifiActive()

    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackMessage()
        }
    }

    LaunchedEffect(navigateToDownloaderEvent) {
        if (navigateToDownloaderEvent > 0) {
            currentTab = NavigationTab.DOWNLOADER
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OmniGetTopAppBar(
                totalSpeed = totalSpeed,
                activeCount = activeDownloads.size,
                isWifi = isWifi,
                smartModeActive = settings.smartNetworkMode
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("omniget_bottom_navigation")
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == NavigationTab.ACTIVE && activeDownloads.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = OmniCyan) {
                                            Text("${activeDownloads.size}", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(imageVector = tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OmniViolet,
                            selectedTextColor = OmniViolet,
                            indicatorColor = OmniViolet.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    NavigationTab.DOWNLOADER -> DownloaderScreen(
                        viewModel = viewModel,
                        onNavigateToActive = { currentTab = NavigationTab.ACTIVE }
                    )
                    NavigationTab.ACTIVE -> ActiveDownloadsScreen(
                        viewModel = viewModel,
                        onNavigateToDownloader = { currentTab = NavigationTab.DOWNLOADER }
                    )
                    NavigationTab.LIBRARY -> LibraryHistoryScreen(
                        viewModel = viewModel,
                        onNavigateToDownloader = { currentTab = NavigationTab.DOWNLOADER }
                    )
                    NavigationTab.SETTINGS -> SettingsSmartModeScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
