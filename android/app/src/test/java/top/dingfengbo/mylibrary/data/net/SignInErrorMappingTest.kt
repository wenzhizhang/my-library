package top.dingfengbo.mylibrary.data.net

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import top.dingfengbo.mylibrary.api.apis.AuthApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.api.models.UserLogin

/**
 * 401 has two meanings in this app and the login screen depends on telling them apart:
 * a rejected sign-in ("wrong username or password") versus a session that ran out.
 */
class SignInErrorMappingTest {

    @Test
    fun `a 401 from login is a credential failure, not an expired session`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"Incorrect username or password"}""")
        )
        server.start()

        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        ).createService(AuthApi::class.java)

        var mapped: ApiException? = null
        try {
            api.apiAuthLoginPost(UserLogin(username = "someone", password = "wrong"))
        } catch (throwable: Throwable) {
            mapped = ApiErrors.asSignInFailure(throwable)
        }
        server.shutdown()

        assertNotNull("the call must fail", mapped)
        assertEquals(ErrorKind.Unauthorized, mapped!!.kind)
        assertEquals(401, mapped.httpCode)
        assertEquals("Incorrect username or password", mapped.detail)
    }

    @Test
    fun `a refused request with no usable token reports an expired session`() {
        // The interceptor throws this locally when there is no valid token to attach; the app shell
        // keys off it to bounce to the login screen instead of letting reads hit demo.db.
        assertEquals(ErrorKind.Expired, ApiErrors.map(SessionExpiredException()).kind)
    }
}
