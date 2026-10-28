package com.example.instamediasaver.data.remote

import com.example.instamediasaver.data.preferences.AppSettings
import com.example.instamediasaver.model.ErrorCode
import com.example.instamediasaver.model.MediaItem
import com.example.instamediasaver.model.MediaKind
import com.example.instamediasaver.model.ResolveResult
import com.example.instamediasaver.model.ResolvedPost
import com.example.instamediasaver.model.ResolverMode
import com.example.instamediasaver.util.InstagramUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Compliant Instagram Media Resolver.
 *
 * Implements three transparent, policy-respecting retrieval paths:
 * 1. BACKEND_PROXY: Sends the canonical post URL to the user's self-hosted backend proxy
 *    (`POST /api/v1/resolve`), keeping Meta Graph API tokens or authorized third-party API keys
 *    exclusively on the server.
 * 2. DIRECT_GRAPH_API: Queries the official Meta Instagram Graph API v26.0
 *    (`GET /{ig-user-id}/media?fields=id,caption,media_type,media_url,thumbnail_url,permalink,shortcode,timestamp,username,children{id,media_type,media_url,thumbnail_url}`)
 *    to match the shortcode among posts owned by the user's Professional account.
 *    Correctly handles `CAROUSEL_ALBUM` children and detects when Meta omits `media_url`
 *    (e.g., due to copyrighted audio or Reel download restrictions).
 * 3. PUBLIC_OEMBED_INSPECTOR: Calls Meta's tokenless `/v26.0/instagram_oembed` endpoint to retrieve
 *    basic post metadata and thumbnail preview, while clearly flagging that full media downloads
 *    require Graph API or Backend Proxy authorization (never faking a video download).
 */
class InstagramMediaResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun resolveUrl(rawUrl: String, settings: AppSettings): ResolveResult =
        withContext(Dispatchers.IO) {
            if (rawUrl.isBlank()) {
                return@withContext ResolveResult.Error(ErrorCode.EMPTY_URL)
            }
            val parsed = InstagramUrlParser.parse(rawUrl)
                ?: return@withContext ResolveResult.Error(ErrorCode.INVALID_INSTAGRAM_URL)

            try {
                when (settings.resolverMode) {
                    ResolverMode.BACKEND_PROXY -> resolveViaBackendProxy(parsed, settings)
                    ResolverMode.DIRECT_GRAPH_API -> resolveViaDirectGraphApi(parsed, settings)
                    ResolverMode.PUBLIC_OEMBED_INSPECTOR -> resolveViaPublicOEmbed(parsed)
                }
            } catch (e: Exception) {
                ResolveResult.Error(ErrorCode.NETWORK_ERROR, e.localizedMessage)
            }
        }

    private fun resolveViaBackendProxy(
        parsed: InstagramUrlParser.ParsedInstagramUrl,
        settings: AppSettings
    ): ResolveResult {
        val baseUrl = settings.backendBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank() || baseUrl.contains("your-backend-proxy.example.com")) {
            return ResolveResult.Error(
                ErrorCode.NO_API_CONFIGURED,
                "Configure your Backend Proxy URL in Settings first."
            )
        }

        val payload = """{"url":"${parsed.canonicalUrl}","shortcode":"${parsed.shortcode}"}"""
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/v1/resolve")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (settings.backendBearerToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.backendBearerToken}")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 429) {
                return ResolveResult.Error(ErrorCode.RATE_LIMITED, "Rate limit reached (HTTP 429).")
            }
            if (response.code == 404 || response.code == 403) {
                return ResolveResult.Error(ErrorCode.PRIVATE_OR_NOT_FOUND, bodyStr)
            }
            if (!response.isSuccessful) {
                return ResolveResult.Error(
                    ErrorCode.SERVER_ERROR,
                    "Backend returned HTTP ${response.code}: $bodyStr"
                )
            }
            val resolvedPost = json.decodeFromString(ResolvedPost.serializer(), bodyStr)
            return ResolveResult.Success(resolvedPost)
        }
    }

    private fun resolveViaDirectGraphApi(
        parsed: InstagramUrlParser.ParsedInstagramUrl,
        settings: AppSettings
    ): ResolveResult {
        val token = settings.graphApiAccessToken.trim()
        val igUserId = settings.graphApiUserId.trim()
        if (token.isBlank() || igUserId.isBlank()) {
            return ResolveResult.Error(
                ErrorCode.NO_API_CONFIGURED,
                "Enter your Meta Graph API Access Token and IG_USER_ID in Settings."
            )
        }

        val fields = "id,caption,media_type,media_url,thumbnail_url,permalink,shortcode,timestamp,username,children{id,media_type,media_url,thumbnail_url}"
        val encodedFields = URLEncoder.encode(fields, "UTF-8")
        val endpoint = "https://graph.facebook.com/v26.0/$igUserId/media?fields=$encodedFields&limit=50&access_token=${URLEncoder.encode(token, "UTF-8")}"

        val request = Request.Builder().url(endpoint).get().build()
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 429) {
                return ResolveResult.Error(ErrorCode.RATE_LIMITED)
            }
            if (!response.isSuccessful) {
                return ResolveResult.Error(
                    ErrorCode.PRIVATE_OR_NOT_FOUND,
                    "Graph API HTTP ${response.code}: $bodyStr"
                )
            }
            return parseGraphApiMediaListJson(bodyStr, parsed)
        }
    }

    /**
     * Parses the official Meta Instagram Graph API `/media` response and extracts single or
     * carousel items matching the requested shortcode. Exposed internal for deterministic unit testing.
     */
    internal fun parseGraphApiMediaListJson(
        rawJson: String,
        parsed: InstagramUrlParser.ParsedInstagramUrl
    ): ResolveResult {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val dataArray: JsonArray = root["data"]?.jsonArray
            ?: return ResolveResult.Error(ErrorCode.PRIVATE_OR_NOT_FOUND)

        val matchedNode: JsonObject = dataArray
            .map { it.jsonObject }
            .firstOrNull { node ->
                val nodeShortcode = node["shortcode"]?.jsonPrimitive?.contentOrNull
                val permalink = node["permalink"]?.jsonPrimitive?.contentOrNull.orEmpty()
                nodeShortcode == parsed.shortcode || permalink.contains("/${parsed.shortcode}")
            } ?: return ResolveResult.Error(
                ErrorCode.PRIVATE_OR_NOT_FOUND,
                "Shortcode ${parsed.shortcode} was not found in the authenticated Professional account's media."
            )

        return parseSingleGraphMediaNode(matchedNode, parsed.shortcode, parsed.canonicalUrl)
    }

    internal fun parseSingleGraphMediaNode(
        node: JsonObject,
        shortcode: String,
        canonicalUrl: String
    ): ResolveResult {
        val mediaId = node["id"]?.jsonPrimitive?.contentOrNull ?: shortcode
        val mediaType = node["media_type"]?.jsonPrimitive?.contentOrNull ?: "IMAGE"
        val caption = node["caption"]?.jsonPrimitive?.contentOrNull
        val username = node["username"]?.jsonPrimitive?.contentOrNull
        val timestamp = node["timestamp"]?.jsonPrimitive?.contentOrNull

        val items = mutableListOf<MediaItem>()

        if (mediaType == "CAROUSEL_ALBUM") {
            val childrenData = node["children"]?.jsonObject?.get("data")?.jsonArray
            if (childrenData != null && childrenData.isNotEmpty()) {
                childrenData.forEachIndexed { idx, childElem ->
                    val child = childElem.jsonObject
                    val childId = child["id"]?.jsonPrimitive?.contentOrNull ?: "${mediaId}_$idx"
                    val childType = child["media_type"]?.jsonPrimitive?.contentOrNull ?: "IMAGE"
                    val childMediaUrl = child["media_url"]?.jsonPrimitive?.contentOrNull
                    val childThumb = child["thumbnail_url"]?.jsonPrimitive?.contentOrNull ?: childMediaUrl
                    val kind = if (childType == "VIDEO" || childType == "REELS") MediaKind.VIDEO else MediaKind.IMAGE

                    items.add(
                        MediaItem(
                            id = childId,
                            index = idx + 1,
                            kind = kind,
                            mediaUrl = childMediaUrl,
                            thumbnailUrl = childThumb,
                            isDownloadable = !childMediaUrl.isNullOrBlank(),
                            restrictionReason = if (childMediaUrl.isNullOrBlank()) {
                                "media_url omitted by Meta Graph API (copyrighted audio or Reel download control)"
                            } else null
                        )
                    )
                }
            }
        } else {
            val mediaUrl = node["media_url"]?.jsonPrimitive?.contentOrNull
            val thumbUrl = node["thumbnail_url"]?.jsonPrimitive?.contentOrNull ?: mediaUrl
            val kind = if (mediaType == "VIDEO" || mediaType == "REELS") MediaKind.VIDEO else MediaKind.IMAGE
            items.add(
                MediaItem(
                    id = mediaId,
                    index = 1,
                    kind = kind,
                    mediaUrl = mediaUrl,
                    thumbnailUrl = thumbUrl,
                    isDownloadable = !mediaUrl.isNullOrBlank(),
                    restrictionReason = if (mediaUrl.isNullOrBlank()) {
                        "media_url omitted by Meta Graph API (copyrighted audio or Reel download control)"
                    } else null
                )
            )
        }

        return ResolveResult.Success(
            ResolvedPost(
                shortcode = shortcode,
                canonicalUrl = canonicalUrl,
                authorUsername = username,
                caption = caption,
                mediaTypeLabel = mediaType,
                timestamp = timestamp,
                items = items,
                resolutionSource = "Meta Instagram Graph API v26.0"
            )
        )
    }

    private fun resolveViaPublicOEmbed(
        parsed: InstagramUrlParser.ParsedInstagramUrl
    ): ResolveResult {
        val encodedUrl = URLEncoder.encode(parsed.canonicalUrl, "UTF-8")
        val endpoint = "https://graph.facebook.com/v26.0/instagram_oembed?url=$encodedUrl&omitscript=true"
        val request = Request.Builder().url(endpoint).get().build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return ResolveResult.Error(ErrorCode.PRIVATE_OR_NOT_FOUND, bodyStr)
            }
            val obj = json.parseToJsonElement(bodyStr).jsonObject
            val authorName = obj["author_name"]?.jsonPrimitive?.contentOrNull
            val thumbUrl = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull
            val width = obj["thumbnail_width"]?.jsonPrimitive?.intOrNull
            val height = obj["thumbnail_height"]?.jsonPrimitive?.intOrNull

            // Honest reporting: Meta's public oEmbed endpoint only provides an embed snippet and
            // a single thumbnail preview, never full-resolution video streams or carousel items.
            val item = MediaItem(
                id = "${parsed.shortcode}_oembed_preview",
                index = 1,
                kind = if (parsed.kind == "reel") MediaKind.VIDEO else MediaKind.IMAGE,
                mediaUrl = null,
                thumbnailUrl = thumbUrl,
                width = width,
                height = height,
                isDownloadable = false,
                restrictionReason = "Public oEmbed provides metadata and thumbnail preview only. Configure Backend Proxy or Graph API in Settings to download original media."
            )

            return ResolveResult.Success(
                ResolvedPost(
                    shortcode = parsed.shortcode,
                    canonicalUrl = parsed.canonicalUrl,
                    authorUsername = authorName,
                    caption = null,
                    mediaTypeLabel = if (parsed.kind == "reel") "REEL (oEmbed Preview)" else "POST (oEmbed Preview)",
                    timestamp = null,
                    items = listOf(item),
                    resolutionSource = "Meta Public oEmbed API v26.0 (Preview Only)",
                    noticeMessage = "Public oEmbed does not expose raw video streams or carousel children. Connect a Backend Proxy or Graph API token in Settings for full downloads."
                )
            )
        }
    }
}
