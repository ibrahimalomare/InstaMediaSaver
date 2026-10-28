package com.example.instamediasaver.util

/**
 * Parses and validates public Instagram Post (/p/) and Reel (/reel/, /reels/) URLs,
 * including extracting shared links embedded inside surrounding caption text from Android's Share menu.
 */
object InstagramUrlParser {

    data class ParsedInstagramUrl(
        val shortcode: String,
        val kind: String, // "p" or "reel"
        val canonicalUrl: String
    )

    // Matches https://(www.)instagram.com/(username/)?(p|reel|reels)/{shortcode} or instagr.am/p/{shortcode}
    private val URL_EXTRACT_REGEX = Regex(
        pattern = "(https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/[^\\s\"'<>]+)",
        option = RegexOption.IGNORE_CASE
    )

    private val PATH_SHORTCODE_REGEX = Regex(
        pattern = "^https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:[A-Za-z0-9_.]+/)?(p|reel|reels)/([A-Za-z0-9_-]{5,32})(?:[/?#].*)?$",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Extracts the first candidate URL from raw shared text (such as Intent.EXTRA_TEXT).
     */
    fun extractFirstUrlFromText(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()
        val match = URL_EXTRACT_REGEX.find(trimmed)
        return match?.value?.trimEnd('.', ',', ')', ']', ';') ?: trimmed
    }

    /**
     * Validates that the input is a supported public Instagram post or Reel URL
     * and returns its shortcode and canonical URL.
     * Explicitly rejects Stories (/stories/), private DMs (/direct/), or non-Instagram domains.
     */
    fun parse(rawInput: String?): ParsedInstagramUrl? {
        val candidate = extractFirstUrlFromText(rawInput) ?: return null
        val match = PATH_SHORTCODE_REGEX.matchEntire(candidate) ?: return null
        val rawKind = match.groupValues[1].lowercase()
        val normalizedKind = if (rawKind == "reels") "reel" else rawKind
        val shortcode = match.groupValues[2]
        val canonicalUrl = "https://www.instagram.com/$normalizedKind/$shortcode/"
        return ParsedInstagramUrl(
            shortcode = shortcode,
            kind = normalizedKind,
            canonicalUrl = canonicalUrl
        )
    }
}
