package top.dingfengbo.mylibrary.data

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
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.data.model.BookSort

/**
 * Pins the wire contract of the list call: which endpoint, which query keys, and what happens to
 * blank filters. A silent change here (a filter that stops being sent, a sort that maps to the
 * wrong enum) is invisible in the UI and wrong in the results.
 */
class BookRepositoryQueryTest {
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

    private fun repository(): BookRepository {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return BookRepository(
            api = client.createService(BooksApi::class.java),
            isbnApi = client.createService(ISBNApi::class.java),
        )
    }

    private fun enqueuePage(books: String = "[]", totalPages: Int = 1, totalBooks: Int = 0) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"books":$books,"total_pages":$totalPages,"total_books":$totalBooks}""")
        )
    }

    @Test
    fun `sends text, filters and sort to the all-books endpoint`() = runBlocking {
        enqueuePage()
        repository().page(
            scope = BookScope.All,
            query = BookQuery(
                text = " 琴 ",
                title = "红楼",
                author = "苏轼",
                publisher = "中华书局",
                tag = "古琴",
                minPrice = "10",
                maxPrice = "99.5",
                purchaseYear = "2010",
                purchaseMonth = "9",
                sort = BookSort.Series,
            ),
            page = 2,
        )

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("/api/books", url.encodedPath)
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("book_series", url.queryParameter("sort_by"))
        assertEquals("琴", url.queryParameter("q"))
        assertEquals("红楼", url.queryParameter("title"))
        assertEquals("苏轼", url.queryParameter("author"))
        assertEquals("中华书局", url.queryParameter("publisher"))
        assertEquals("古琴", url.queryParameter("tag"))
        assertEquals("10", url.queryParameter("min_price"))
        assertEquals("99.5", url.queryParameter("max_price"))
        assertEquals("2010", url.queryParameter("purchase_year"))
        assertEquals("9", url.queryParameter("purchase_month"))
    }

    @Test
    fun `omits blank filters instead of sending empty values`() = runBlocking {
        enqueuePage()
        repository().page(scope = BookScope.All, query = BookQuery(text = "   "), page = 1)

        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("q"))
        assertNull(url.queryParameter("title"))
        assertNull(url.queryParameter("author"))
        assertNull(url.queryParameter("min_price"))
        assertNull(url.queryParameter("purchase_year"))
        assertEquals("title", url.queryParameter("sort_by"))
    }

    @Test
    fun `wishlist and archived scopes hit their own endpoints`() = runBlocking {
        enqueuePage()
        repository().page(scope = BookScope.Wishlist, query = BookQuery(sort = BookSort.CreatedAt), page = 1)
        val wishlist = server.takeRequest().requestUrl!!
        assertEquals("/api/books/wishlist", wishlist.encodedPath)
        assertEquals("created_at", wishlist.queryParameter("sort_by"))

        enqueuePage()
        repository().page(scope = BookScope.Archived, query = BookQuery(), page = 1)
        assertEquals("/api/books/archived", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `a page without a books array reads as empty with no further pages`() = runBlocking {
        // The generated model makes every field nullable, including the collection.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"total_books":0}""")
        )
        val page = repository().page(BookScope.All, BookQuery(), page = 1).getOrThrow()

        assertTrue(page.books.isEmpty())
        assertEquals(1, page.totalPages)
        assertEquals(0, page.totalBooks)
        assertFalse(page.hasMore)
    }

    @Test
    fun `an ISBN that matched nothing is a success, not an error`() = runBlocking {
        // The endpoint always answers 200; an unmatched ISBN comes back with an almost empty body,
        // and the form must treat that as "nothing to prefill" rather than a failure.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"isbn":"9780000000000","source":"none"}""")
        )
        val hit = repository().isbnLookup("9780000000000").getOrThrow()

        assertEquals("9780000000000", hit.isbn)
        assertNull(hit.title?.takeIf { it.isNotBlank() })
        assertNull(hit.authorIds?.takeIf { it.isNotEmpty() })
        assertEquals("/api/isbn/9780000000000", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `an ISBN hit carries the ids the form needs to fill the pickers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"isbn":"9787806631744","title":"琴学门径","title_cn":"琴學門徑",
                     "publisher_id":5,"publisher_name":"中国书店","author_ids":[226],
                     "author_names":["张子盛"],"publish_date":"2006-01-01T00:00:00",
                     "pages":231,"price":48.0,"source":"douban","thumb_image":"/images/books/9787806631744.webp"}
                    """.trimIndent()
                )
        )
        val hit = repository().isbnLookup("9787806631744").getOrThrow()

        assertEquals(5, hit.publisherId)
        assertEquals(listOf(226), hit.authorIds)
        assertEquals("2006-01-01T00:00:00", hit.publishDate)
        assertEquals(48.0.toBigDecimal(), hit.price)
    }
}
