package top.dingfengbo.mylibrary.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.CatalogRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.PreferencesRepository
import top.dingfengbo.mylibrary.data.ScanHandoff
import top.dingfengbo.mylibrary.data.model.NamedRef
import top.dingfengbo.mylibrary.data.net.ApiErrors
import top.dingfengbo.mylibrary.data.net.ApiException
import top.dingfengbo.mylibrary.data.net.ErrorKind

/** Outcome of an ISBN lookup, phrased for the user. */
sealed interface LookupOutcome {
    data object Running : LookupOutcome
    data class Filled(val source: String?) : LookupOutcome
    data object NothingFound : LookupOutcome
    data class Failed(val throwable: Throwable) : LookupOutcome
}

data class BookFormUiState(
    val form: BookFormState = BookFormState(),
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val loadError: Throwable? = null,
    val submitting: Boolean = false,
    val submitError: Throwable? = null,
    val lookup: LookupOutcome? = null,
    val purchaseStores: List<String> = emptyList(),
    val openPicker: RefKind? = null,
    val saved: Boolean = false,
)

class BookFormViewModel(
    private val books: BookRepository,
    private val catalog: CatalogRepository,
    private val preferences: PreferencesRepository,
    private val libraryEvents: LibraryEvents,
    private val scanHandoff: ScanHandoff,
    private val bookId: Int?,
) : ViewModel() {

    private val _ui = MutableStateFlow(BookFormUiState(isEdit = bookId != null))
    val ui: StateFlow<BookFormUiState> = _ui.asStateFlow()

    /**
     * A code arrived from the scanner, which means a different book: the previous lookup's fill is
     * discarded before looking this one up. Merging into it instead is why a second scan left the
     * form showing the first book — every field the new book does not report kept its old value, so
     * the ISBN changed while title, author and publisher stayed behind.
     */
    fun startWithIsbn(isbn: String) {
        _ui.update { it.copy(form = BookFormState(isbn = isbn), lookup = null) }
        lookupIsbn()
    }

    init {
        if (bookId != null) load()
        viewModelScope.launch {
            preferences.purchaseStores()
                .onSuccess { stores -> _ui.update { it.copy(purchaseStores = stores) } }
        }
        viewModelScope.launch {
            scanHandoff.isbn.filterNotNull().collect { code ->
                scanHandoff.consume()
                startWithIsbn(code)
            }
        }
    }

    fun load() {
        val id = bookId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, loadError = null) }
            books.detail(id)
                .onSuccess { book ->
                    _ui.update { it.copy(form = BookFormState.from(book), loading = false) }
                }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, loadError = throwable) } }
        }
    }

    fun edit(transform: (BookFormState) -> BookFormState) =
        _ui.update { it.copy(form = transform(it.form), submitError = null) }

    fun openPicker(kind: RefKind?) = _ui.update { it.copy(openPicker = kind) }

    fun setChoice(kind: RefKind, choices: List<RefChoice>) = edit { state ->
        when (kind) {
            RefKind.Author -> state.copy(authors = choices)
            RefKind.Publisher -> state.copy(publisher = choices.firstOrNull())
            RefKind.Brand -> state.copy(brand = choices.firstOrNull())
            RefKind.Series -> state.copy(series = choices.firstOrNull())
            RefKind.Category -> state.copy(category = choices.firstOrNull())
            RefKind.Bookshelf -> state.copy(bookshelf = choices.firstOrNull())
            RefKind.PurchaseStore -> state.copy(purchaseStore = choices.firstOrNull()?.label.orEmpty())
        }
    }.also { openPicker(null) }

    fun choicesFor(kind: RefKind): List<RefChoice> = with(_ui.value.form) {
        when (kind) {
            RefKind.Author -> authors
            RefKind.Publisher -> listOfNotNull(publisher)
            RefKind.Brand -> listOfNotNull(brand)
            RefKind.Series -> listOfNotNull(series)
            RefKind.Category -> listOfNotNull(category)
            RefKind.Bookshelf -> listOfNotNull(bookshelf)
            RefKind.PurchaseStore -> purchaseStore.takeIf { it.isNotBlank() }
                ?.let { listOf(RefChoice(-1, it)) }.orEmpty()
        }
    }

    suspend fun search(kind: RefKind, query: String): Result<List<NamedRef>> = when (kind) {
        RefKind.Author -> catalog.authors(query).map { it.items }
        RefKind.Publisher -> catalog.publishers(query).map { it.items }
        RefKind.Brand -> catalog.brands(query).map { it.items }
        RefKind.Series -> catalog.series(query).map { it.items }
        RefKind.Category -> catalog.categories(query).map { it.items }
        RefKind.Bookshelf -> catalog.bookshelves(query).map { it.items }
        // Served from the already-loaded config: it rarely changes and the endpoint takes no query.
        RefKind.PurchaseStore -> Result.success(
            _ui.value.purchaseStores
                .filter { it.contains(query, ignoreCase = true) }
                .mapIndexed { index, store -> NamedRef(index, store) }
        )
    }

    suspend fun create(kind: RefKind, values: List<String>): Result<NamedRef> = when (kind) {
        RefKind.Author -> catalog.createAuthor(name = values[0], nameCn = values.getOrElse(1) { values[0] })
        RefKind.Publisher -> catalog.createPublisher(values[0])
        RefKind.Brand -> catalog.createBrand(values[0])
        RefKind.Series -> catalog.createSeries(values[0])
        RefKind.Category -> catalog.createCategory(values[0])
        RefKind.Bookshelf -> catalog.createBookshelf(values[0])
        // Purchase stores come from server configuration; the picker offers no create action for them.
        RefKind.PurchaseStore -> Result.failure(
            ApiException(ErrorKind.Unknown, detail = "purchase stores cannot be created from the app")
        )
    }

    /**
     * Looks the ISBN in the field up and fills only the gaps.
     *
     * That is deliberate: this is also the button pressed after typing corrections by hand, and
     * overwriting a typed title would be worse than leaving it.
     *
     * ponytail: a *changed* ISBN looked up from here still merges into the previous book's fill. The
     * scanner path resets first (see [startWithIsbn]); reset here too if that is ever reported.
     */
    fun lookupIsbn() {
        val isbn = _ui.value.form.isbn.trim()
        if (isbn.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(lookup = LookupOutcome.Running) }
            books.isbnLookup(isbn)
                .onSuccess { hit ->
                    val hasData = !hit.title.isNullOrBlank() ||
                        !hit.titleCn.isNullOrBlank() ||
                        !hit.authorNames.isNullOrEmpty() ||
                        !hit.thumbImage.isNullOrBlank()
                    _ui.update {
                        it.copy(
                            form = it.form.mergedWith(hit),
                            lookup = if (hasData) LookupOutcome.Filled(hit.source) else LookupOutcome.NothingFound,
                        )
                    }
                }
                .onFailure { throwable ->
                    // The endpoint answers 404 when nothing matched; that is "type it in yourself",
                    // not an error worth showing as one.
                    val outcome = if (ApiErrors.map(throwable).kind == ErrorKind.NotFound) {
                        LookupOutcome.NothingFound
                    } else {
                        LookupOutcome.Failed(throwable)
                    }
                    _ui.update { it.copy(lookup = outcome) }
                }
        }
    }

    fun submit() {
        val state = _ui.value
        if (!state.form.canSubmit || state.submitting) return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, submitError = null) }
            val result = if (bookId == null) {
                books.create(state.form.toCreation()).map { }
            } else {
                books.update(bookId, state.form.toUpdate())
            }
            result
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(submitting = false, saved = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(submitting = false, submitError = throwable) } }
        }
    }
}
