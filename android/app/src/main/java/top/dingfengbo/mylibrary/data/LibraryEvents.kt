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
}
