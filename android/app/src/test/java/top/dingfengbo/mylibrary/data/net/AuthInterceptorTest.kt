package top.dingfengbo.mylibrary.data.net

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The interceptor is the only thing standing between a stale token and this backend's habit of
 * answering reads with 200 + demo.db data, so the fail-closed behaviour is pinned here.
 */
class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String?) =
        OkHttpClient.Builder().addInterceptor(AuthInterceptor { token }).build()

    private fun request(path: String) =
        Request.Builder().url(server.url(path)).build()

    @Test
    fun `attaches the bearer token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client("abc.def.ghi").newCall(request("/api/books")).execute().close()
        assertEquals("Bearer abc.def.ghi", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refuses to send a request with no usable token`() {
        val thrown = assertThrows(IOException::class.java) {
            client(null).newCall(request("/api/books")).execute()
        }
        assertEquals(SessionExpiredException::class.java, thrown::class.java)
        assertEquals("no request should reach the server", 0, server.requestCount)
    }

    @Test
    fun `login goes out without a token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client(null).newCall(request("/my-library/api/auth/login")).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `register goes out without a stale token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client("stale.token.here").newCall(request("/my-library/api/auth/register")).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
