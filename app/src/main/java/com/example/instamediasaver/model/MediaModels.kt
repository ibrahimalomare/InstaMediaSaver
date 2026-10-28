package com.example.instamediasaver.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    IMAGE,
    VIDEO
}

@Serializable
enum class ResolverMode {
    BACKEND_PROXY,
    DIRECT_GRAPH_API,
    PUBLIC_OEMBED_INSPECTOR
}

@Serializable
data class MediaItem(
    val id: String,
    val index: Int,
    val kind: MediaKind,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("is_downloadable") val isDownloadable: Boolean = true,
    @SerialName("restriction_reason") val restrictionReason: String? = null
)

@Serializable
data class ResolvedPost(
    val shortcode: String,
    @SerialName("canonical_url") val canonicalUrl: String,
    @SerialName("author_username") val authorUsername: String? = null,
    val caption: String? = null,
    @SerialName("media_type_label") val mediaTypeLabel: String,
    val timestamp: String? = null,
    val items: List<MediaItem>,
    @SerialName("resolution_source") val resolutionSource: String,
    @SerialName("notice_message") val noticeMessage: String? = null
)

sealed class ResolveResult {
    data class Success(val post: ResolvedPost) : ResolveResult()
    data class Error(val errorCode: ErrorCode, val details: String? = null) : ResolveResult()
}

enum class ErrorCode {
    EMPTY_URL,
    INVALID_INSTAGRAM_URL,
    PRIVATE_OR_NOT_FOUND,
    NO_API_CONFIGURED,
    RATE_LIMITED,
    NETWORK_ERROR,
    SERVER_ERROR
}

data class DownloadProgressState(
    val isDownloading: Boolean = false,
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val currentItemPercent: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val lastSavedUri: String? = null,
    val lastSavedMimeType: String? = null,
    val errorMessage: String? = null
)
