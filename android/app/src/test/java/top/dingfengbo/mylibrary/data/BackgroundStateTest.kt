package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.dingfengbo.mylibrary.api.apis.BackgroundsApi
import top.dingfengbo.mylibrary.api.apis.ConfigApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.data.auth.SessionState

/**
 * Which picture the app paints behind itself: the account's own choice, the configured default when
 * there is none, and nothing at all once the session ends.
 *
 * The last part matters as much as the first: the web front end is careful never to leave one
 * account's picture on screen after a sign-out, and the app must not either.
 */
class BackgroundStateTest {

    private lateinit var server: MockWebServer
    private val session = MutableStateFlow<SessionState>(SessionState.Unknown)

    /** The state collects the session for as long as the app lives; a test must end it. */
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when {
                    request.path.orEmpty().endsWith("/api/backgrounds/me") -> """{"background_id":"b1"}"""
                    request.path.orEmpty().startsWith("/api/backgrounds") -> """
                        {"default_id":"b2","backgrounds":[
                          {"id":"b1","name":"One","url":"/images/background/b1.jpg"},
                          {"id":"b2","name":"Two","url":"/images/background/b2.jpg"}]}
                    """.trimIndent()
                    else -> "{}"
                }
                return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun state(): BackgroundState {
        val client = ApiClient(baseUrl = server.url("/").toString(), okHttpClientBuilder = OkHttpClient.Builder())
        return BackgroundState(
            preferences = PreferencesRepository(
                backgroundsApi = client.createService(BackgroundsApi::class.java),
                configApi = client.createService(ConfigApi::class.java),
            ),
            scope = scope,
            session = session,
        )
    }

    private suspend fun awaitUrl(state: BackgroundState): String =
        withTimeout(10_000) { state.url.filterNotNull().first() }

    @Test
    fun `paints the account's own choice`() = runBlocking {
        val state = state()
        session.value = SessionState.LoggedIn(Session("token", "reader", "uuid", Long.MAX_VALUE))

        // MediaUrls resolves against BuildConfig.BASE_URL, so the assertion pins the chosen path.
        val url = awaitUrl(state)
        assertTrue("got $url", url.endsWith("/api/media/images/background/b1.jpg"))
    }

    @Test
    fun `falls back to the configured default when nothing is chosen`() = runBlocking {
        // No personal selection: /api/backgrounds/me answers with a null id.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = if (request.path.orEmpty().endsWith("/me")) """{"background_id":null}"""
                else """{"default_id":"b2","backgrounds":[{"id":"b2","name":"Two","url":"/images/background/b2.jpg"}]}"""
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }
        val state = state()
        session.value = SessionState.LoggedIn(Session("token", "reader", "uuid", Long.MAX_VALUE))

        val url = awaitUrl(state)
        assertTrue("got $url", url.endsWith("/api/media/images/background/b2.jpg"))
    }

    @Test
    fun `drops the picture when the session ends`() = runBlocking {
        val state = state()
        session.value = SessionState.LoggedIn(Session("token", "reader", "uuid", Long.MAX_VALUE))
        awaitUrl(state)

        session.value = SessionState.LoggedOut()

        assertNull(state.url.value)
    }
}
