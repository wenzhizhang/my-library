package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.dingfengbo.mylibrary.data.model.BookScope

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

    /** What the book list can be asked to do while it is off screen. */
    sealed interface ListRequest {
        /** Open the search field. */
        data object Search : ListRequest

        /** Show one of the listings — the wishlist or the archived books. */
        data class ShowScope(val scope: BookScope) : ListRequest
    }

    private val _listRequest = MutableStateFlow<ListRequest?>(null)

    /**
     * A request for the book list, raised from the bar or from Mine.
     *
     * Neither can reach the list directly: it is not even composed while the reader is on another tab,
     * and the listings are no longer switched from the list itself. So the ask travels as a request
     * that stays until it is taken.
     */
    val listRequest: StateFlow<ListRequest?> = _listRequest.asStateFlow()

    fun requestList(request: ListRequest) = _listRequest.update { request }

    /** The list has taken it; clearing keeps the next visit from repeating it. */
    fun consumeListRequest() = _listRequest.update { null }
}
