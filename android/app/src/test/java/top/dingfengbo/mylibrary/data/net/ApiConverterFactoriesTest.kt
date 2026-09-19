package top.dingfengbo.mylibrary.data.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.api.models.BookCreation
import top.dingfengbo.mylibrary.api.models.BookUpdate

/**
 * Pins the null split between the two write endpoints. An update that drops a null leaves the old
 * value in the column while the form reports success, and a create that keeps one is rejected for
 * the non-optional fields that have defaults — neither is visible in the UI.
 */
class ApiConverterFactoriesTest {
    private lateinit var server: MockWebServer
    private lateinit var books: BooksApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            ApiClient(
                baseUrl = server.url("/").toString(),
                okHttpClientBuilder = OkHttpClient.Builder(),
                converterFactories = apiConverterFactories,
            )
        books = client.createService(BooksApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueBook() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":7,"isbn":"978","title":"T","title_cn":"题"}""")
        )
    }

    private fun sentBody(): JsonObject {
        val body = server.takeRequest().body.readUtf8()
        return Json.parseToJsonElement(body).jsonObject
    }

    @Test
    fun `a field the edit form cleared is sent as an explicit null`() = runBlocking {
        enqueueBook()

        books.apiBooksBookIdPut(
            7,
            BookUpdate(
                isbn = "978",
                title = "T",
                titleCn = "题",
                price = null,
                publisherId = null,
                tags = null,
            ),
        )

        val body = sentBody()
        assertEquals(JsonNull, body["price"])
        assertEquals(JsonNull, body["publisher_id"])
        assertEquals(JsonNull, body["tags"])
        assertEquals("T", body["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an update leaves the server-managed updated_at out of the body`() = runBlocking {
        enqueueBook()

        books.apiBooksBookIdPut(7, BookUpdate(isbn = "978", title = "T", titleCn = "题"))

        assertFalse(sentBody().containsKey("updated_at"))
    }

    @Test
    fun `creation sends no nulls for the fields the create model defaults`() = runBlocking {
        enqueueBook()

        books.apiBooksPost(BookCreation(isbn = "978", title = "T", titleCn = "题"))

        val body = sentBody()
        assertEquals("978", body["isbn"]!!.jsonPrimitive.content)
        assertEquals("T", body["title"]!!.jsonPrimitive.content)
        assertEquals("题", body["title_cn"]!!.jsonPrimitive.content)
        assertTrue(body.values.none { it is JsonNull })
    }
}
