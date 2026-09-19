package top.dingfengbo.mylibrary.data.net

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cancellation is not an error category: the caller replaced the request (new search, scope change,
 * tab switch) and a list must not answer that with "出错了，请重试" over the screen the user asked for.
 * Genuine failures still map, or the change would have swapped one wrong screen for another.
 */
class ApiErrorsTest {

    @Test
    fun `a cancelled call rethrows instead of becoming a failure`() = runBlocking {
        val thrown = try {
            ApiErrors.call<Unit> { throw CancellationException("superseded") }
            null
        } catch (throwable: Throwable) {
            throwable
        }

        assertTrue("cancellation must propagate, got $thrown", thrown is CancellationException)
    }

    @Test
    fun `a socket timeout is still mapped as a network failure`() = runBlocking {
        val result = ApiErrors.call<Unit> { throw SocketTimeoutException("read timed out") }

        assertTrue("the call must fail", result.isFailure)
        assertEquals(ErrorKind.Network, (result.exceptionOrNull() as ApiException).kind)
    }
}
