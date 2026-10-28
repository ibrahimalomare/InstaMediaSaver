package com.example.instamediasaver

import com.example.instamediasaver.util.InstagramUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun parsesStandardPostAndReelUrls() {
        val post = InstagramUrlParser.parse("https://www.instagram.com/p/C9xYz123AbC/?igsh=MTIz")
        assertNotNull(post)
        assertEquals("C9xYz123AbC", post?.shortcode)
        assertEquals("p", post?.kind)
        assertEquals("https://www.instagram.com/p/C9xYz123AbC/", post?.canonicalUrl)

        val reel = InstagramUrlParser.parse("https://instagram.com/reel/D1aBc987XyZ/")
        assertNotNull(reel)
        assertEquals("D1aBc987XyZ", reel?.shortcode)
        assertEquals("reel", reel?.kind)
    }

    @Test
    fun extractsUrlFromAndroidShareText() {
        val sharedCaption = "Check out this reel by @creator https://www.instagram.com/reels/AbCdEfG1234/?utm_source=ig_web_copy_link!"
        val parsed = InstagramUrlParser.parse(sharedCaption)
        assertNotNull(parsed)
        assertEquals("AbCdEfG1234", parsed?.shortcode)
        assertEquals("reel", parsed?.kind)
    }

    @Test
    fun rejectsStoriesPrivateAndNonInstagramUrls() {
        assertNull(InstagramUrlParser.parse("https://www.instagram.com/stories/username/123456789/"))
        assertNull(InstagramUrlParser.parse("https://www.example.com/p/C9xYz123AbC/"))
        assertNull(InstagramUrlParser.parse(""))
    }
}
