package top.dingfengbo.mylibrary.data.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 * The production failure this guards against: `/api/books` answers 307 with
 * `Location: http://<host>/my-library/api/books/`, and in a release build following that is a
 * cleartext request, which the network security policy refuses — every list screen showed
 * "network unavailable" while the endpoints that answer directly kept working.
 */
class SafeRedirectInterceptorTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun dispatcherFor(location: String, redirectCode: Int = 307) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path.orEmpty()
                return if (request.path.orEmpty().endsWith("/api/books/")) {
                    MockResponse().setResponseCode(200).setBody("""{"books":[],"total_pages":1,"total_books":0}""")
                } else {
                    MockResponse().setResponseCode(redirectCode).setHeader("Location", location)
                }
            }
        }
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .addInterceptor(SafeRedirectInterceptor())
        .build()

    private fun get(path: String) =
        client().newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `follows the trailing-slash redirect on the same authority`() {
        dispatcherFor("/api/books/")

        get("/api/books?limit=20").use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(
            listOf("/api/books?limit=20", "/api/books/"),
            paths,
        )
    }

    @Test
    fun `follows a same-host redirect issued with a different scheme, staying on our own port`() {
        // The production shape: we talk https on one port and are told to go to http://same-host/...
        // which resolves to a different default port. Only the path may be taken from it.
        dispatcherFor("http://${server.hostName}:80/api/books/")

        get("/api/books?limit=20").use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("/api/books?limit=20", "/api/books/"), paths)
    }

    @Test
    fun `a POST keeps its method and body when the redirect is followed`() {
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                methods += request.method.orEmpty()
                bodies += request.body.readUtf8()
                return if (request.path.orEmpty().endsWith("/api/books/")) {
                    MockResponse().setResponseCode(200).setBody("""{"id":1}""")
                } else {
                    MockResponse().setResponseCode(307).setHeader("Location", "/api/books/")
                }
            }
        }

        val payload = """{"isbn":"9787806631744","title":"x","title_cn":"y","author_ids":[]}"""
        client().newCall(
            Request.Builder()
                .url(server.url("/api/books"))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("POST", "POST"), methods)
        assertEquals(listOf(payload, payload), bodies)
    }

    @Test
    fun `refuses a redirect to another host`() {
        dispatcherFor("https://evil.example/my-library/api/books/")

        get("/api/books?limit=20").use { response ->
            assertEquals("a cross-origin redirect must be handed back, not followed", 307, response.code)
        }

        assertEquals(listOf("/api/books?limit=20"), paths)
    }

    @Test
    fun `follows 308 as well`() {
        dispatcherFor("/api/books/", redirectCode = 308)

        get("/api/books").use { response ->
            assertEquals(200, response.code)
        }

        assertTrue("expected the redirect to be followed: $paths", paths.contains("/api/books/"))
    }

    @Test
    fun `stops after the hop limit instead of looping`() {
        // A Location that keeps pointing somewhere new each time.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path
                val next = if (path.contains("a")) path.replace("a", "b") else path + "a"
                return MockResponse().setResponseCode(307).setHeader("Location", next)
            }
        }

        get("/api/books").use { response ->
            assertEquals(307, response.code)
        }

        assertTrue("must not spin forever, paths seen: $paths", paths.size <= 3)
    }
}
