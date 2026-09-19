package top.dingfengbo.mylibrary.ui.books

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.dingfengbo.mylibrary.MainDispatcherRule
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.LibraryEvents

/**
 * The edit form is pushed on top of the detail entry, so this view model — unlike the list's one —
 * stays alive across a save. It therefore has to follow the shared revision counter itself, and it
 * must not go looking for a book it knows is deleted.
 */
class BookDetailViewModelTest {
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

    private fun viewModel(events: LibraryEvents): BookDetailViewModel {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return BookDetailViewModel(
            repository = BookRepository(
                api = client.createService(BooksApi::class.java),
                isbnApi = client.createService(ISBNApi::class.java),
            ),
            libraryEvents = events,
            bookId = BOOK_ID,
        )
    }

    /** See [BookListViewModelTest.awaitState] for why virtual time alone is not enough. */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(
        viewModel: BookDetailViewModel,
        predicate: (BookDetailUiState) -> Boolean,
    ) {
        repeat(300) {
            advanceUntilIdle()
            if (predicate(viewModel.ui.value)) return
            withContext(Dispatchers.IO) { Thread.sleep(25) }
        }
        error("state never satisfied, last was ${viewModel.ui.value}")
    }

    private fun detail(titleCn: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":$BOOK_ID,"isbn":"","title":"","title_cn":"$titleCn"}""")
        )
    }

    private fun similar() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"similar_books":[]}""")
        )
    }

    @Test
    fun `a saved edit reloads the book instead of leaving the pre-edit copy on screen`() =
        runTest(mainDispatcherRule.dispatcher) {
            detail("旧名")
            similar()
            detail("新名")
            similar()

            val events = LibraryEvents()
            val viewModel = viewModel(events)
            awaitState(viewModel) { it.book?.titleCn == "旧名" }

            events.bump() // what the form does once its save succeeds

            awaitState(viewModel) { it.book?.titleCn == "新名" }
        }

    @Test
    fun `a deleted book is not fetched again when the revision bumps`() =
        runTest(mainDispatcherRule.dispatcher) {
            detail("旧名")
            similar()
            // The spec answers DELETE with 200 and {message}; a 204 would leave nothing for the
            // generated response model to decode.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"deleted"}""")
            )

            val events = LibraryEvents()
            val viewModel = viewModel(events)
            awaitState(viewModel) { it.book != null }

            viewModel.delete()
            awaitState(viewModel) { it.deleted }
            advanceUntilIdle()
            withContext(Dispatchers.IO) { Thread.sleep(150) }
            advanceUntilIdle()

            // detail, similar and the DELETE itself; the bump the delete triggers must not add a
            // fourth request for a book that no longer exists.
            assertEquals(3, server.requestCount)
            assertNull("a reload of the deleted book would have failed here", viewModel.ui.value.error)
        }

    private companion object {
        const val BOOK_ID = 1
    }
}
