package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A shared revision counter bumped by every mutation (archive, delete, and later create/edit).
 *
 * Open lists watch it and refresh themselves, which is what keeps the book list honest after you
 * archive something from the detail screen — without every screen having to hand back a result.
 */
class LibraryEvents {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() = _revision.update { it + 1 }

    private val _searchRequested = MutableStateFlow(false)

    /**
     * A request to open the book list's search, raised from the bar's quick actions.
     *
     * The bar can see the button but not the list's own mode, and the list is not even composed while
     * the reader is on another tab, so the ask travels as a request that stays until it is taken.
     */
    val searchRequested: StateFlow<Boolean> = _searchRequested.asStateFlow()

    fun requestSearch() = _searchRequested.update { true }

    /** The list has taken it; clearing keeps the next visit out of search mode. */
    fun consumeSearchRequest() = _searchRequested.update { false }
}
