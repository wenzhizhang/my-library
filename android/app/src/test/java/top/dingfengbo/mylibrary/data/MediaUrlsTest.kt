package top.dingfengbo.mylibrary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cover URLs are built in one place for a reason: the API returns paths like
 * `/images/books/x.webp`, and a stray double slash or a missing prefix breaks every image at once.
 */
class MediaUrlsTest {

    @Test
    fun `joins a leading-slash path without doubling the separator`() {
        val url = MediaUrls.image("/images/books/9787806631744.webp")
        assertTrue(url!!, url.endsWith("/api/media/images/books/9787806631744.webp"))
        assertTrue("no double slash: $url", !url.contains("/api/media//"))
    }

    @Test
    fun `joins a bare path too`() {
        val url = MediaUrls.image("images/books/x.webp")
        assertTrue(url!!, url.endsWith("/api/media/images/books/x.webp"))
    }

    @Test
    fun `leaves absolute urls alone`() {
        assertEquals(
            "https://cdn.example.com/x.webp",
            MediaUrls.image("https://cdn.example.com/x.webp"),
        )
    }

    @Test
    fun `returns null when there is no image`() {
        assertNull(MediaUrls.image(null))
        assertNull(MediaUrls.image(""))
        assertNull(MediaUrls.image("   "))
    }
}
