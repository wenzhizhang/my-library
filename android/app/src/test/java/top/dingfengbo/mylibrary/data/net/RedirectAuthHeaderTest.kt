package top.dingfengbo.mylibrary.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The backend answers the collection endpoints (`/api/books`, `/api/authors`, …) with a 307 to the
 * trailing-slash form. If that redirect dropped the Authorization header, the follow-up request
 * would arrive unauthenticated — and this backend answers unauthenticated reads with 200 + demo.db
 * data, so the app would quietly show the wrong library. The header surviving the hop is load
 * bearing, so it gets a test against a faithful stand-in for that redirect.
 */
class RedirectAuthHeaderTest {
    private lateinit var server: MockWebServer
    private val seen = mutableListOf<Pair<String, String?>>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request.path.orEmpty() to request.getHeader("Authorization")
                return if (request.path.orEmpty().endsWith("/api/books/")) {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"books":[],"total_pages":1,"total_books":0}""")
                } else {
                    MockResponse().setResponseCode(307).setHeader("Location", "/my-library/api/books/")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authorization survives the trailing-slash redirect`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "abc.def.ghi" })
            .build()

        client.newCall(
            Request.Builder().url(server.url("/my-library/api/books?limit=1")).build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals("requests seen: $seen", 2, seen.size)
        assertTrue("first hop: ${seen[0]}", seen[0].first.startsWith("/my-library/api/books?limit=1"))
        assertTrue("second hop: ${seen[1]}", seen[1].first.endsWith("/api/books/"))
        assertEquals("Bearer abc.def.ghi", seen[0].second)
        assertEquals("Bearer abc.def.ghi", seen[1].second)
    }
}
