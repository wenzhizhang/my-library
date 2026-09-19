package top.dingfengbo.mylibrary.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    /** A refresh makes both reads; answer either one with the picture [id] names. */
    private fun reply(path: String, id: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            if (path.endsWith("/api/backgrounds/me")) """{"background_id":"$id"}"""
            else """{"default_id":"$id","backgrounds":[{"id":"$id","name":"N","url":"/images/background/$id.jpg"}]}""",
        )

    /**
     * Wait out a refresh that should have been dropped. Nothing signals a coroutine that must *not*
     * run, so the URL has to be looked at again once the wire has gone quiet.
     */
    private suspend fun settle() = delay(500)

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

    @Test
    fun `a refresh in flight when the session ends never paints`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
                return reply(request.path.orEmpty(), "b1")
            }
        }
        val state = state()

        session.value = SessionState.LoggedIn(Session("token", "reader", "uuid", Long.MAX_VALUE))
        assertTrue("the refresh never reached the server", started.await(10, TimeUnit.SECONDS))

        // Sign out while the account's reads are still on the wire, then let them answer.
        session.value = SessionState.LoggedOut()
        release.countDown()
        settle()

        assertNull("the previous account's picture survived the sign-out", state.url.value)
    }

    @Test
    fun `the newest refresh wins even when it answers first`() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val reads = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val index = reads.incrementAndGet()
                // Hold the first refresh's read open, so its picture is guaranteed to arrive last.
                if (index == 1) {
                    firstStarted.countDown()
                    releaseFirst.await(10, TimeUnit.SECONDS)
                }
                // Reads two and three are the second refresh's, and answer with its picture.
                return reply(request.path.orEmpty(), if (index in 2..3) "b2" else "b1")
            }
        }
        val state = state()

        session.value = SessionState.LoggedIn(Session("token", "reader", "uuid", Long.MAX_VALUE))
        assertTrue("the first refresh never reached the server", firstStarted.await(10, TimeUnit.SECONDS))

        // The user picks another picture before the previous pick's reads have come back.
        state.refresh()
        val painted = awaitUrl(state)
        releaseFirst.countDown()
        settle()

        assertTrue("got $painted", painted.endsWith("/api/media/images/background/b2.jpg"))
        assertEquals("the replaced picture repainted itself", painted, state.url.value)
    }
}
