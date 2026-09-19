package top.dingfengbo.mylibrary.ui.books

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.dingfengbo.mylibrary.MainDispatcherRule
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.data.model.BookSort

/**
 * Runs the list screen's state machine for real: paging, the search debounce, and the rule that
 * switching to wishlist/archived drops filters the backend would ignore.
 */
class BookListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private fun viewModel(events: LibraryEvents = LibraryEvents()): BookListViewModel {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return BookListViewModel(
            repository = BookRepository(
                api = client.createService(BooksApi::class.java),
                isbnApi = client.createService(ISBNApi::class.java),
            ),
            libraryEvents = events,
        )
    }

    /** Takes the book objects themselves so the page is always a JSON array, never a bare object. */
    private fun page(books: List<String>, totalPages: Int, totalBooks: Int) {
        val array = books.joinToString(",", prefix = "[", postfix = "]")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"books":$array,"total_pages":$totalPages,"total_books":$totalBooks}""")
        )
    }

    /**
     * Waits for the view model to react to something.
     *
     * The repository talks to a real MockWebServer over a real socket, so advancing virtual time is
     * not enough on its own: the HTTP round trip needs real time. Alternating the two keeps the
     * debounce (virtual) and the network (real) both working.
     *
     * The 300 x 25ms budget is wall clock, and it has to cover a socket round trip on a JVM that is
     * busy running every other test class: at 10ms per poll it was tight enough to fail roughly once
     * in ten full-suite runs while passing every time the class ran alone.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(
        viewModel: BookListViewModel,
        predicate: (BookListUiState) -> Boolean,
    ) {
        repeat(300) {
            advanceUntilIdle()
            if (predicate(viewModel.ui.value)) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Thread.sleep(25) }
        }
        val failure = viewModel.ui.value.error
        error(
            "state never satisfied, last was ${viewModel.ui.value}; " +
                "error=${failure?.let { it::class.simpleName + ": " + it.message }} " +
                "cause=${failure?.cause?.let { it::class.simpleName + ": " + it.message }}"
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.awaitRequests(count: Int) {
        repeat(300) {
            advanceUntilIdle()
            if (server.requestCount >= count) {
                advanceUntilIdle()
                return
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Thread.sleep(25) }
        }
        error("only ${server.requestCount} of $count requests arrived")
    }

    private fun book(id: Int, title: String) =
        """{"id":$id,"title":"$title","title_cn":"$title"}"""

    @Test
    fun `appends the next page when the end comes into view`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = listOf(book(1, "a"), book(2, "b")), totalPages = 2, totalBooks = 3)
        page(books = listOf(book(3, "c")), totalPages = 2, totalBooks = 3)

        val viewModel = viewModel()
        awaitState(viewModel) { it.books.size == 2 }

        assertEquals(listOf(1, 2), viewModel.ui.value.books.map { it.id })
        assertEquals(3, viewModel.ui.value.totalBooks)
        assertTrue(viewModel.ui.value.hasMore)

        // Scrolling near the end asks for page 2.
        viewModel.onListScrolledTo(lastVisibleIndex = 1)
        awaitState(viewModel) { it.books.size == 3 }

        assertEquals(listOf(1, 2, 3), viewModel.ui.value.books.map { it.id })
        assertFalse(viewModel.ui.value.hasMore)

        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
    }

    @Test
    fun `search is debounced and then sent as q`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = emptyList(), totalPages = 1, totalBooks = 0)
        page(books = listOf(book(9, "琴")), totalPages = 1, totalBooks = 1)

        val viewModel = viewModel()
        awaitRequests(1)
        server.takeRequest() // initial load

        viewModel.onSearchTextChange("琴")
        advanceTimeBy(100)
        // No advanceUntilIdle() here on purpose: it would run the pending debounce delay to
        // completion and the assertion below would no longer be testing anything.
        assertEquals("no request before the debounce elapses", 0, server.requestCount - 1)

        advanceTimeBy(400)
        awaitState(viewModel) { it.books.map { book -> book.id } == listOf(9) }

        assertEquals("琴", server.takeRequest().requestUrl!!.queryParameter("q"))
        assertEquals(listOf(9), viewModel.ui.value.books.map { it.id })
    }

    @Test
    fun `switching scope drops search text and filters`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // initial "all" load
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // wishlist load

        val viewModel = viewModel()
        awaitRequests(1)

        viewModel.onSearchTextChange("琴")
        advanceTimeBy(400)
        awaitRequests(2)
        viewModel.onApplyFilters(BookQuery(author = "苏轼"))
        awaitRequests(3)

        viewModel.onScopeChange(BookScope.Wishlist)
        awaitRequests(4)

        assertEquals("", viewModel.ui.value.searchText)
        assertEquals("", viewModel.ui.value.query.text)
        assertFalse("filters belong to the all-books endpoint only", viewModel.ui.value.query.hasFilters)

        val paths = generateSequence { server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .map { it.requestUrl!!.encodedPath }
            .toList()
        assertTrue("wishlist endpoint was used: $paths", paths.any { it == "/api/books/wishlist" })
    }

    @Test
    fun `changing the sort reloads and keeps the loaded sort`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // initial
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // after sort change

        val viewModel = viewModel()
        awaitRequests(1)
        server.takeRequest()

        viewModel.onSortChange(BookSort.Series)
        awaitRequests(2)

        assertEquals(BookSort.Series, viewModel.ui.value.query.sort)
        assertEquals("book_series", server.takeRequest().requestUrl!!.queryParameter("sort_by"))
    }

    @Test
    fun `a reset from the filter sheet keeps the term the search box shows`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // initial
        page(books = listOf(book(4, "苏轼集")), totalPages = 1, totalBooks = 1) // debounced search
        page(books = listOf(book(7, "东坡志林")), totalPages = 1, totalBooks = 1) // after Reset + Apply

        val viewModel = viewModel()
        awaitRequests(1)
        server.takeRequest()

        viewModel.onSearchTextChange("苏轼")
        advanceTimeBy(400)
        awaitRequests(2)
        assertEquals("苏轼", server.takeRequest().requestUrl!!.queryParameter("q"))

        // What the sheet's Reset sends: every filter field blank, including the term the sheet
        // never shows and the search box still holds.
        viewModel.onApplyFilters(viewModel.ui.value.query.cleared())
        awaitState(viewModel) { it.books.map { book -> book.id } == listOf(7) }

        assertEquals("苏轼", viewModel.ui.value.searchText)
        assertEquals(viewModel.ui.value.searchText, viewModel.ui.value.query.text)
        assertEquals("苏轼", server.takeRequest().requestUrl!!.queryParameter("q"))
    }

    @Test
    fun `a scope change inside the debounce window drops the pending search`() = runTest(mainDispatcherRule.dispatcher) {
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // initial "all" load
        page(books = emptyList(), totalPages = 1, totalBooks = 0) // wishlist load

        val viewModel = viewModel()
        awaitRequests(1)
        server.takeRequest()

        viewModel.onSearchTextChange("琴")
        advanceTimeBy(100) // well inside the debounce; the request has not gone out
        viewModel.onScopeChange(BookScope.Wishlist)
        advanceTimeBy(1_000) // the debounce deadline passes, the job is gone
        awaitRequests(2)

        assertEquals("", viewModel.ui.value.searchText)
        assertEquals("", viewModel.ui.value.query.text)

        val requests = generateSequence { server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .toList()
        assertEquals("only the wishlist load was sent, not the stale search", 1, requests.size)
        assertEquals("/api/books/wishlist", requests.single().requestUrl!!.encodedPath)
        assertNull(requests.single().requestUrl!!.queryParameter("q"))
    }

    @Test
    fun `a failed first page surfaces an error and no rows`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}"""))

        val viewModel = viewModel()
        awaitState(viewModel) { it.error != null }

        assertTrue(viewModel.ui.value.books.isEmpty())
        assertNotNull(viewModel.ui.value.error)
        assertFalse("no spinner left running", viewModel.ui.value.loading)
    }
}
