package top.dingfengbo.mylibrary.data

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import top.dingfengbo.mylibrary.data.ExportRepository.Format
import top.dingfengbo.mylibrary.data.ExportRepository.Scope
import top.dingfengbo.mylibrary.data.net.ApiException
import top.dingfengbo.mylibrary.data.net.ErrorKind

/**
 * Export is the one call that does not go through the generated client, so nothing else pins its
 * failure contract: a 403 there has to mean what a 403 means everywhere else, the backend's `detail`
 * is what the screen shows, and a cancelled download must unwind rather than paint an error.
 */
class ExportRepositoryTest {
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

    private fun repository(): ExportRepository =
        ExportRepository(OkHttpClient(), server.url("/").toString())

    @Test
    fun `a rejected export is classified and keeps the backend detail`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"detail":"该账号不能导出数据"}""")
        )

        val result = repository().download(Format.Csv, Scope.Books) { ByteArrayOutputStream() }

        val error = result.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.Unauthorized, error.kind)
        assertEquals("该账号不能导出数据", error.detail)
    }

    @Test
    fun `a failed export never opens the target file`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"detail":"not found"}""")
        )
        var opened = false

        val result = repository().downloadDatabase {
            opened = true
            ByteArrayOutputStream()
        }

        assertEquals(ErrorKind.NotFound, (result.exceptionOrNull() as ApiException).kind)
        assertFalse("a failed export must not leave an empty file behind", opened)
    }

    @Test
    fun `a successful export is streamed into the sink`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("id,title\n1,红楼梦\n"))
        val target = ByteArrayOutputStream()

        val result = repository().download(Format.Csv, Scope.Books) { target }

        assertTrue("export failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("id,title\n1,红楼梦\n", target.toString("UTF-8"))
    }

    @Test
    fun `a cancelled export unwinds instead of being reported as a failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("id,title\n1,红楼梦\n")
                .setHeadersDelay(1, TimeUnit.SECONDS)
        )
        var outcome: Result<Unit>? = null

        val job = launch(Dispatchers.IO) {
            outcome = repository().download(Format.Csv, Scope.Books) { ByteArrayOutputStream() }
        }
        // The request is on the wire, so the download is really in flight before it is cancelled.
        server.takeRequest()
        job.cancelAndJoin()

        assertNull("a cancelled export must not surface as a failed download", outcome)
    }
}
