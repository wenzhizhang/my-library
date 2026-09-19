package top.dingfengbo.mylibrary.ui.catalog

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import top.dingfengbo.mylibrary.api.apis.AuthorsApi
import top.dingfengbo.mylibrary.api.apis.BookshelvesApi
import top.dingfengbo.mylibrary.api.apis.BrandsApi
import top.dingfengbo.mylibrary.api.apis.CategoriesApi
import top.dingfengbo.mylibrary.api.apis.PublishersApi
import top.dingfengbo.mylibrary.api.apis.SeriesApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.CatalogRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.CatalogEntity

/**
 * Pins what a failure leaves behind: a failed reset must not show the previous query's rows as this
 * one's results, while a failed page must stay visible under the rows already loaded.
 */
class CatalogListViewModelTest {
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

    private fun viewModel(): CatalogListViewModel {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return CatalogListViewModel(
            catalog = CatalogRepository(
                authorsApi = client.createService(AuthorsApi::class.java),
                publishersApi = client.createService(PublishersApi::class.java),
                brandsApi = client.createService(BrandsApi::class.java),
                seriesApi = client.createService(SeriesApi::class.java),
                categoriesApi = client.createService(CategoriesApi::class.java),
                bookshelvesApi = client.createService(BookshelvesApi::class.java),
            ),
            libraryEvents = LibraryEvents(),
            entity = CatalogEntity.Author,
        )
    }

    private fun page(ids: List<Int>, totalPages: Int) {
        val array = ids.joinToString(",", prefix = "[", postfix = "]") { """{"id":$it,"name":"n$it"}""" }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"authors":$array,"total_pages":$totalPages,"total_authors":${ids.size}}""")
        )
    }

    private fun failure() = server.enqueue(
        MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}""")
    )

    /**
     * Waits for the view model to react to something, alternating virtual and real time: the
     * debounce is virtual, the HTTP round trip over the mock socket is real.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(
        viewModel: CatalogListViewModel,
        predicate: (CatalogListUiState) -> Boolean,
    ) {
        repeat(300) {
            advanceUntilIdle()
            if (predicate(viewModel.ui.value)) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Thread.sleep(25) }
        }
        error("state never satisfied, last was ${viewModel.ui.value}")
    }

    @Test
    fun `a failed reset drops the previous rows and reports the error`() = runTest(mainDispatcherRule.dispatcher) {
        page(ids = listOf(1, 2), totalPages = 1)
        // FIFO: the reload under test consumes the failure, the later reload consumes this page.
        failure()
        page(ids = listOf(9), totalPages = 1)

        val viewModel = viewModel()
        awaitState(viewModel) { it.rows.map { row -> row.id } == listOf(1, 2) }
        assertEquals("/api/authors", server.takeRequest().requestUrl!!.encodedPath)

        viewModel.reload()
        awaitState(viewModel) { it.error != null }

        assertTrue(
            "the previous query's rows must not stand in for the failed one",
            viewModel.ui.value.rows.isEmpty(),
        )
        assertFalse(viewModel.ui.value.hasMore)
        assertFalse("no spinner left running", viewModel.ui.value.loading)

        viewModel.reload()
        awaitState(viewModel) { it.rows.map { row -> row.id } == listOf(9) }
        assertEquals(listOf(9), viewModel.ui.value.rows.map { it.id })
    }

    @Test
    fun `a failed page keeps the rows and reports the error`() = runTest(mainDispatcherRule.dispatcher) {
        page(ids = listOf(1, 2), totalPages = 2)
        failure()

        val viewModel = viewModel()
        awaitState(viewModel) { it.rows.size == 2 }

        viewModel.onScrolledTo(lastVisibleIndex = 1)
        awaitState(viewModel) { it.error != null }

        assertEquals(listOf(1, 2), viewModel.ui.value.rows.map { it.id })
        assertFalse(viewModel.ui.value.loadingMore)
        assertTrue(viewModel.ui.value.hasMore)

        server.takeRequest()
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
    }
}
