package top.dingfengbo.mylibrary.ui.auth

import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.dingfengbo.mylibrary.MainDispatcherRule
import top.dingfengbo.mylibrary.api.apis.AuthApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.AuthRepository
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.data.auth.SessionManager
import top.dingfengbo.mylibrary.data.auth.SessionStore

private class MemoryStore(private var session: Session? = null) : SessionStore {
    override suspend fun current(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

private fun sessionToken(): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = encoder.encodeToString("""{"sub":"1","uuid":"u","exp":1900000000}""".toByteArray())
    return "$header.$payload.sig"
}

/**
 * The login form's state machine: which message survives what, and what the view model is still
 * holding once the credentials have been spent.
 */
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = SessionManager(
            store = MemoryStore(),
            scope = CoroutineScope(mainDispatcherRule.dispatcher),
            now = { 1_800_000_000 },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun viewModel(): AuthViewModel {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return AuthViewModel(AuthRepository(client.createService(AuthApi::class.java), session))
    }

    /** Drives the register tab into the state that raises the mismatch line. */
    private fun mismatchingForm(viewModel: AuthViewModel) {
        viewModel.onModeChange(AuthMode.Register)
        viewModel.onUsernameChange("u")
        viewModel.onPasswordChange("one")
        viewModel.onConfirmPasswordChange("two")
        viewModel.submit()
    }

    @Test
    fun `editing a field clears the password mismatch`() {
        val viewModel = viewModel()
        mismatchingForm(viewModel)
        assertTrue(viewModel.ui.value.passwordMismatch)

        viewModel.onConfirmPasswordChange("two")

        assertFalse(viewModel.ui.value.passwordMismatch)
    }

    @Test
    fun `switching between login and register clears the password mismatch`() {
        val viewModel = viewModel()
        mismatchingForm(viewModel)
        assertTrue(viewModel.ui.value.passwordMismatch)

        viewModel.onModeChange(AuthMode.Login)

        // The mismatch line is rendered with priority over `error`, so leaving it set would hide
        // whatever the next submit reports.
        assertFalse(viewModel.ui.value.passwordMismatch)
    }

    @Test
    fun `signing out leaves nothing behind in the form`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"${sessionToken()}","user_id":1,"username":"u","uuid":"u"}""")
        )
        val viewModel = viewModel()
        viewModel.onUsernameChange("u")
        viewModel.onPasswordChange("hunter2")
        viewModel.submit()
        awaitCredentialsDropped(viewModel)

        session.signOut()

        // This view model is activity-scoped and outlives the login screen, so anything kept here
        // would be typed back into a prefilled form after a sign-out — contradicting the sign-out
        // dialog and leaving the password resident in memory.
        assertEquals("", viewModel.ui.value.username)
        assertEquals("", viewModel.ui.value.password)
    }

    private suspend fun TestScope.awaitCredentialsDropped(viewModel: AuthViewModel) {
        repeat(300) {
            advanceUntilIdle()
            if (viewModel.ui.value.username.isEmpty()) return
            // The repository talks to a real socket, so virtual time alone cannot make progress.
            withContext(Dispatchers.IO) { Thread.sleep(25) }
        }
        error("the credentials were never dropped, last state was ${viewModel.ui.value}")
    }
}
