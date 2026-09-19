package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.dingfengbo.mylibrary.api.apis.AuthApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.api.models.ApiAuthDbInfoGet200Response
import top.dingfengbo.mylibrary.api.models.TokenResponse
import top.dingfengbo.mylibrary.api.models.UserInfo
import top.dingfengbo.mylibrary.api.models.UserLogin
import top.dingfengbo.mylibrary.api.models.UserRegister
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.data.auth.SessionManager
import top.dingfengbo.mylibrary.data.auth.SessionState
import top.dingfengbo.mylibrary.data.auth.SessionStore

private class FakeSessionStore(private var session: Session? = null) : SessionStore {
    var cleared = false
        private set

    override suspend fun current(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
        cleared = false
    }

    override suspend fun clear() {
        session = null
        cleared = true
    }
}

private fun jwt(exp: Long): String {
    val enc = java.util.Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString("""{"sub":"1","uuid":"u","exp":$exp}""".toByteArray())
    return "$header.$payload.sig"
}

/** Stands in for the caller going away mid-request, which is how Retrofit resumes a cancelled call. */
private class CancellingAuthApi : AuthApi {
    override suspend fun apiAuthLoginPost(userLogin: UserLogin): TokenResponse =
        throw CancellationException("cancelled")

    override suspend fun apiAuthRegisterPost(userRegister: UserRegister): TokenResponse =
        throw CancellationException("cancelled")

    override suspend fun apiAuthMeGet(): UserInfo = throw CancellationException("cancelled")

    override suspend fun apiAuthDbInfoGet(): ApiAuthDbInfoGet200Response =
        throw CancellationException("cancelled")
}

/**
 * The rules that decide when a stored session is dropped.
 *
 * These are the difference between "the app logged me out for no reason" and "a token that no longer
 * works kept the UI in a signed-in state". The 404 case is a regression test: this backend answers
 * 404 from `/api/auth/me` when the account behind a token was deleted, and the app used to stay
 * signed in forever (found by running it against a deleted account).
 */
class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: FakeSessionStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeSessionStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(manager: SessionManager): AuthRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        ).createService(AuthApi::class.java)
        return AuthRepository(api, manager)
    }

    private fun manager(now: Long = 1_800_000_000): SessionManager = SessionManager(
        store = store,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        now = { now },
    )

    private fun enqueueMe(code: Int) {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(if (code == 200) """{"id":1,"username":"u","uuid":"u"}""" else """{"detail":"nope"}""")
        )
    }

    @Test
    fun `a valid session survives the probe`() = runTest {
        enqueueMe(200)
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")

        assertTrue(repository(manager).verifySession())
        assertEquals(SessionState.LoggedIn::class, manager.state.value::class)
    }

    @Test
    fun `a 401 signs out`() = runTest {
        enqueueMe(401)
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")

        assertFalse(repository(manager).verifySession())
        assertTrue(store.cleared)
        assertEquals(true, (manager.state.value as SessionState.LoggedOut).expired)
    }

    @Test
    fun `a 404 means the account is gone, so it also signs out`() = runTest {
        enqueueMe(404)
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")

        assertFalse(repository(manager).verifySession())
        assertTrue(store.cleared)
        assertEquals(SessionState.LoggedOut::class, manager.state.value::class)
    }

    @Test
    fun `a server error keeps the session`() = runTest {
        enqueueMe(500)
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")

        assertFalse(repository(manager).verifySession())
        assertFalse("a hiccup must not log the user out", store.cleared)
        assertEquals(SessionState.LoggedIn::class, manager.state.value::class)
    }

    @Test
    fun `an unreachable server keeps the session`() = runTest {
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")
        server.shutdown()

        assertFalse(repository(manager).verifySession())
        assertFalse(store.cleared)
        assertEquals(SessionState.LoggedIn::class, manager.state.value::class)
    }

    @Test
    fun `restore drops an already expired token`() = runTest {
        store.save(Session("tok", "u", "u", expiresAtEpochSeconds = 1_000))
        val manager = manager(now = 2_000)

        manager.restore()

        assertTrue(store.cleared)
        assertEquals(true, (manager.state.value as SessionState.LoggedOut).expired)
        assertNull(manager.validToken())
    }

    @Test
    fun `restore keeps the expired explanation when the store is already empty`() = runTest {
        store.save(Session("tok", "u", "u", expiresAtEpochSeconds = 1_000))
        val manager = manager(now = 2_000)
        manager.restore()

        // AppNavigation restores on every ON_START, and the store is empty by the second pass: the
        // bounce must still read as "expired" instead of turning into a plain logout on rotation.
        manager.restore()

        assertEquals(true, (manager.state.value as SessionState.LoggedOut).expired)
    }

    @Test
    fun `validToken refuses to hand out a token inside the expiry window`() = runTest {
        val manager = manager(now = 1_900_000_000)
        store.save(Session("tok", "u", "u", expiresAtEpochSeconds = 1_900_000_030))

        manager.restore()

        // 30 seconds left is inside the 60 second skew window: the server would reject it on arrival,
        // and a rejected token makes read endpoints answer with demo.db data instead of an error.
        assertNull(manager.validToken())
        assertTrue(store.cleared)
    }

    @Test
    fun `a cancelled sign-in is unwound instead of reported as a failure`() = runTest {
        val repository = AuthRepository(CancellingAuthApi(), manager())

        var thrown: Throwable? = null
        try {
            repository.login("u", "hunter2")
        } catch (cancellation: CancellationException) {
            thrown = cancellation
        }

        // Mapped into Result.failure this reached the login form as an error line on a screen the
        // caller had already abandoned.
        assertTrue("a cancelled sign-in must unwind", thrown != null)
    }

    @Test
    fun `a cancelled session probe is unwound instead of returning a result`() = runTest {
        val manager = manager()
        manager.signIn(jwt(1_900_000_000), "u", "u")
        val repository = AuthRepository(CancellingAuthApi(), manager)

        var thrown: Throwable? = null
        try {
            repository.verifySession()
        } catch (cancellation: CancellationException) {
            thrown = cancellation
        }

        assertTrue("a cancelled session probe must unwind", thrown != null)
        // A cancellation is not a rejection: the token the user is holding must survive it.
        assertEquals(SessionState.LoggedIn::class, manager.state.value::class)
    }
}
