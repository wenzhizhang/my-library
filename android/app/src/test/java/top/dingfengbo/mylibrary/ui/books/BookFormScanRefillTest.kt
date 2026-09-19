package top.dingfengbo.mylibrary.ui.books

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.dingfengbo.mylibrary.MainDispatcherRule
import top.dingfengbo.mylibrary.api.apis.AuthorsApi
import top.dingfengbo.mylibrary.api.apis.BackgroundsApi
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.BookshelvesApi
import top.dingfengbo.mylibrary.api.apis.BrandsApi
import top.dingfengbo.mylibrary.api.apis.CategoriesApi
import top.dingfengbo.mylibrary.api.apis.ConfigApi
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.apis.PublishersApi
import top.dingfengbo.mylibrary.api.apis.SeriesApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.CatalogRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.PreferencesRepository
import top.dingfengbo.mylibrary.data.ScanHandoff

/**
 * A scan is a statement about *which book* is in the form, so a second scan must leave that second
 * book's data behind, not a mixture.
 *
 * The lookup result used to be merged into whatever the form already held, and that merge only fills
 * blank fields — so a second scan changed the ISBN field while every field the first book had
 * populated (title, author, publisher) kept its old value.
 *
 * While editing it is the other way round: the form is not a new book but the record an update writes
 * back, so a scan there must leave the record exactly as it loaded.
 */
class BookFormScanRefillTest {

    // Unconfined: the lookup's continuation has to run on the OkHttp callback thread, and this test
    // waits in real time rather than virtual time.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var server: MockWebServer
    private val scanHandoff = ScanHandoff()

    private val firstBook =
        """{"isbn":"9787020024759","title":"First Book","title_cn":"第一本书","publisher_id":11,""" +
            """"publisher_name":"甲出版社","author_ids":[101],"author_names":["曹雪芹"]}"""

    private val secondBook =
        """{"isbn":"9787536692930","title":"Second Book","title_cn":"第二本书","publisher_id":22,""" +
            """"publisher_name":"乙出版社","author_ids":[202],"author_names":["鲁迅"]}"""

    /** The record under edit, as `GET /api/books/55` answers it. */
    private val storedRecord =
        """{"id":55,"isbn":"9787506365437","title":"Record Book","title_cn":"在架的书",""" +
            """"publisher":{"id":33,"name":"丙出版社"},"authors":[{"id":303,"name":"老舍"}]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                // 66 exists but cannot be read: the failed detail load the form must not save over.
                if (path.contains("/api/books/66")) return MockResponse().setResponseCode(500)
                val body = when {
                    path.contains("/api/books/55") -> storedRecord
                    path.contains("9787020024759") -> firstBook
                    path.contains("9787536692930") -> secondBook
                    // The view model also probes server config on init; its content is irrelevant here.
                    else -> "{}"
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun viewModel(bookId: Int? = null): BookFormViewModel {
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder(),
        )
        return BookFormViewModel(
            books = BookRepository(
                api = client.createService(BooksApi::class.java),
                isbnApi = client.createService(ISBNApi::class.java),
            ),
            catalog = CatalogRepository(
                authorsApi = client.createService(AuthorsApi::class.java),
                publishersApi = client.createService(PublishersApi::class.java),
                brandsApi = client.createService(BrandsApi::class.java),
                seriesApi = client.createService(SeriesApi::class.java),
                categoriesApi = client.createService(CategoriesApi::class.java),
                bookshelvesApi = client.createService(BookshelvesApi::class.java),
            ),
            preferences = PreferencesRepository(
                backgroundsApi = client.createService(BackgroundsApi::class.java),
                configApi = client.createService(ConfigApi::class.java),
            ),
            libraryEvents = LibraryEvents(),
            scanHandoff = scanHandoff,
            bookId = bookId,
        )
    }

    /** Waits for the lookup of [isbn] to come back and land in the form. */
    private suspend fun awaitLookup(vm: BookFormViewModel, isbn: String) = withTimeout(10_000) {
        vm.ui.first { it.form.isbn == isbn && it.lookup is LookupOutcome.Filled }
    }

    @Test
    fun `a second scan replaces the fill instead of merging into the first book`() = runBlocking {
        val vm = viewModel()

        scanHandoff.publish("9787020024759")
        awaitLookup(vm, "9787020024759")
        assertEquals("第一本书", vm.ui.value.form.titleCn)

        scanHandoff.publish("9787536692930")
        awaitLookup(vm, "9787536692930")

        val form = vm.ui.value.form
        assertEquals("9787536692930", form.isbn)
        assertEquals("Second Book", form.title)
        assertEquals("第二本书", form.titleCn)
        assertEquals("乙出版社", form.publisher?.label)
        assertEquals("鲁迅", form.authors.single().label)
    }

    @Test
    fun `re-scanning the same book refills what was typed over`() = runBlocking {
        val vm = viewModel()

        scanHandoff.publish("9787020024759")
        awaitLookup(vm, "9787020024759")

        vm.edit { it.copy(titleCn = "被改坏了", publisher = null) }

        scanHandoff.publish("9787020024759")
        withTimeout(10_000) { vm.ui.first { it.form.titleCn == "第一本书" } }

        assertEquals("甲出版社", vm.ui.value.form.publisher?.label)
    }

    @Test
    fun `a scan while editing leaves the record being edited alone`() = runBlocking {
        val vm = viewModel(bookId = 55)
        withTimeout(10_000) { vm.ui.first { it.recordLoaded } }
        assertEquals("在架的书", vm.ui.value.form.titleCn)

        scanHandoff.publish("9787536692930")
        // Consuming the code is all that is owed here, so wait for the hand-off to empty, then give
        // the discarded lookup the same room the create flow needs to fill a form.
        withTimeout(10_000) { scanHandoff.isbn.first { it == null } }
        delay(500)

        val state = vm.ui.value
        assertEquals("9787506365437", state.form.isbn)
        assertEquals("Record Book", state.form.title)
        assertEquals("在架的书", state.form.titleCn)
        assertEquals(RefChoice(33, "丙出版社"), state.form.publisher)
        assertEquals(listOf(RefChoice(303, "老舍")), state.form.authors)
        assertNull("the scanned ISBN was never looked up", state.lookup)
    }

    @Test
    fun `a failed detail load cannot be saved over the record`() = runBlocking {
        val vm = viewModel(bookId = 66)

        val failed = withTimeout(10_000) { vm.ui.first { it.loadError != null } }
        assertFalse("the record never reached the form", failed.recordLoaded)
        assertFalse(failed.canSubmit)

        // The fallback form is blank and still editable, and the update endpoint replaces every
        // field: saving two typed-in values would erase the thirty that never loaded.
        vm.edit { it.copy(isbn = "9787506365437", title = "手打的书名") }
        assertTrue(vm.ui.value.form.canSubmit)
        assertFalse(vm.ui.value.canSubmit)
    }
}
