package com.example.instamediasaver.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.instamediasaver.data.downloader.GalleryMediaDownloader
import com.example.instamediasaver.data.local.AppDatabase
import com.example.instamediasaver.data.local.DownloadHistoryEntity
import com.example.instamediasaver.data.preferences.AppSettings
import com.example.instamediasaver.data.preferences.SettingsRepository
import com.example.instamediasaver.data.remote.InstagramMediaResolver
import com.example.instamediasaver.model.DownloadProgressState
import com.example.instamediasaver.model.ErrorCode
import com.example.instamediasaver.model.MediaItem
import com.example.instamediasaver.model.ResolveResult
import com.example.instamediasaver.model.ResolvedPost
import com.example.instamediasaver.util.InstagramUrlParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val isResolving: Boolean = false,
    val resolvedPost: ResolvedPost? = null,
    val selectedItemIds: Set<String> = emptySet(),
    val errorCode: ErrorCode? = null,
    val errorDetails: String? = null,
    val downloadProgress: DownloadProgressState = DownloadProgressState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val historyDao = database.downloadHistoryDao()
    private val settingsRepo = SettingsRepository(application)
    private val mediaResolver = InstagramMediaResolver()
    private val mediaDownloader = GalleryMediaDownloader(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    val historyItems: StateFlow<List<DownloadHistoryEntity>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onUrlInputChange(newUrl: String) {
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                errorCode = null,
                errorDetails = null
            )
        }
    }

    fun handleIncomingSharedText(sharedText: String?) {
        val extracted = InstagramUrlParser.extractFirstUrlFromText(sharedText) ?: return
        _uiState.update {
            it.copy(urlInput = extracted, errorCode = null, errorDetails = null)
        }
        resolveCurrentUrl()
    }

    fun clearInput() {
        _uiState.value = HomeUiState()
    }

    fun resolveCurrentUrl() {
        val currentUrl = _uiState.value.urlInput.trim()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolving = true,
                    errorCode = null,
                    errorDetails = null,
                    resolvedPost = null,
                    selectedItemIds = emptySet(),
                    downloadProgress = DownloadProgressState()
                )
            }
            when (val result = mediaResolver.resolveUrl(currentUrl, settings.value)) {
                is ResolveResult.Success -> {
                    val downloadableIds = result.post.items
                        .filter { it.isDownloadable && !it.mediaUrl.isNullOrBlank() }
                        .map { it.id }
                        .toSet()
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            resolvedPost = result.post,
                            selectedItemIds = downloadableIds
                        )
                    }
                }
                is ResolveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isResolving = false,
                            errorCode = result.errorCode,
                            errorDetails = result.details
                        )
                    }
                }
            }
        }
    }

    fun toggleItemSelection(itemId: String) {
        _uiState.update { state ->
            val updated = state.selectedItemIds.toMutableSet()
            if (!updated.add(itemId)) {
                updated.remove(itemId)
            }
            state.copy(selectedItemIds = updated)
        }
    }

    fun selectAllDownloadable() {
        val post = _uiState.value.resolvedPost ?: return
        val allIds = post.items
            .filter { it.isDownloadable && !it.mediaUrl.isNullOrBlank() }
            .map { it.id }
            .toSet()
        _uiState.update { it.copy(selectedItemIds = allIds) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedItemIds = emptySet()) }
    }

    fun downloadSelectedItems() {
        val post = _uiState.value.resolvedPost ?: return
        val selectedIds = _uiState.value.selectedItemIds
        val itemsToDownload = post.items.filter { it.id in selectedIds && it.isDownloadable }
        startBatchDownload(post, itemsToDownload)
    }

    fun downloadAllItems() {
        val post = _uiState.value.resolvedPost ?: return
        val allDownloadable = post.items.filter { it.isDownloadable && !it.mediaUrl.isNullOrBlank() }
        startBatchDownload(post, allDownloadable)
    }

    private fun startBatchDownload(post: ResolvedPost, items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var completed = 0
            var failed = 0
            var lastUri: String? = null
            var lastMime: String? = null
            var lastError: String? = null

            items.forEachIndexed { idx, item ->
                _uiState.update {
                    it.copy(
                        downloadProgress = DownloadProgressState(
                            isDownloading = true,
                            currentItemIndex = idx + 1,
                            totalItems = items.size,
                            currentItemPercent = 0,
                            completedCount = completed,
                            failedCount = failed
                        )
                    )
                }

                val res = mediaDownloader.downloadItemToGallery(
                    shortcode = post.shortcode,
                    item = item,
                    onProgressPercent = { pct ->
                        _uiState.update { state ->
                            state.copy(
                                downloadProgress = state.downloadProgress.copy(
                                    currentItemPercent = pct
                                )
                            )
                        }
                    }
                )

                res.onSuccess { saved ->
                    completed++
                    lastUri = saved.contentUri.toString()
                    lastMime = saved.mimeType
                    historyDao.insert(
                        DownloadHistoryEntity(
                            shortcode = post.shortcode,
                            itemIndex = item.index,
                            sourcePostUrl = post.canonicalUrl,
                            authorUsername = post.authorUsername,
                            mediaKind = item.kind.name,
                            mimeType = saved.mimeType,
                            fileName = saved.fileName,
                            localContentUri = saved.contentUri.toString(),
                            thumbnailUrl = item.thumbnailUrl,
                            fileSizeBytes = saved.bytesWritten,
                            downloadedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                }.onFailure { err ->
                    failed++
                    lastError = err.localizedMessage
                }
            }

            _uiState.update {
                it.copy(
                    downloadProgress = DownloadProgressState(
                        isDownloading = false,
                        currentItemIndex = items.size,
                        totalItems = items.size,
                        currentItemPercent = 100,
                        completedCount = completed,
                        failedCount = failed,
                        lastSavedUri = lastUri,
                        lastSavedMimeType = lastMime,
                        errorMessage = lastError
                    )
                )
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyDao.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun saveSettings(newSettings: AppSettings) {
        val oldLang = settings.value.languageTag
        settingsRepo.updateSettings(newSettings)
        if (oldLang != newSettings.languageTag) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(newSettings.languageTag)
            )
        }
    }
}
