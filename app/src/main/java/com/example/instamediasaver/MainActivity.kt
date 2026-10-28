package com.example.instamediasaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.instamediasaver.ui.MainViewModel
import com.example.instamediasaver.ui.screens.DownloadScreen
import com.example.instamediasaver.ui.screens.HistoryScreen
import com.example.instamediasaver.ui.screens.SettingsScreen
import com.example.instamediasaver.ui.theme.InstaMediaSaverTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled when user triggers download on Android 8-9 */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractUrlFromIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val historyItems by viewModel.historyItems.collectAsState()
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            InstaMediaSaverTheme(darkTheme = settings.darkMode) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_download)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_history)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_settings)) }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> DownloadScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onUrlChange = viewModel::onUrlInputChange,
                            onClear = viewModel::clearInput,
                            onFetchPreview = viewModel::resolveCurrentUrl,
                            onToggleItem = viewModel::toggleItemSelection,
                            onSelectAll = viewModel::selectAllDownloadable,
                            onDeselectAll = viewModel::deselectAll,
                            onDownloadSelected = {
                                ensureStoragePermissionThen { viewModel.downloadSelectedItems() }
                            },
                            onDownloadAll = {
                                ensureStoragePermissionThen { viewModel.downloadAllItems() }
                            },
                            onOpenSavedFile = { uriStr, mimeType ->
                                openDownloadedFile(uriStr, mimeType)
                            }
                        )
                        1 -> HistoryScreen(
                            modifier = Modifier.padding(innerPadding),
                            items = historyItems,
                            onOpenFile = { item ->
                                openDownloadedFile(item.localContentUri, item.mimeType)
                            },
                            onShareFile = { item ->
                                shareDownloadedFile(item.localContentUri, item.mimeType)
                            },
                            onDeleteItem = { item ->
                                viewModel.deleteHistoryItem(item.id)
                            },
                            onClearAll = viewModel::clearAllHistory
                        )
                        2 -> SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentSettings = settings,
                            onSaveSettings = { updated ->
                                viewModel.saveSettings(updated)
                                Toast.makeText(
                                    this,
                                    getString(R.string.settings_saved_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrlFromIntent(intent)
    }

    private fun extractUrlFromIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    viewModel.handleIncomingSharedText(sharedText)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                viewModel.handleIncomingSharedText(dataUri)
            }
        }
    }

    private fun ensureStoragePermissionThen(action: () -> Unit) {
        // Android 10+ (API 29+) uses scoped MediaStore without any storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun openDownloadedFile(uriString: String, mimeType: String?) {
        try {
            val uri = Uri.parse(uriString)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, getString(R.string.btn_open_file)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.err_open_file_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDownloadedFile(uriString: String, mimeType: String?) {
        try {
            val uri = Uri.parse(uriString)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_share_file)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.err_open_file_failed), Toast.LENGTH_LONG).show()
        }
    }
}
