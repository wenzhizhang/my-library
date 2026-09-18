package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-off for a scanned ISBN.
 *
 * The scanner and the book form are separate screens; passing the code through the navigation key
 * would rebuild the form and throw away everything already typed. A one-slot channel keeps the form
 * instance alive and lets it fold the code in.
 */
class ScanHandoff {
    private val _isbn = MutableStateFlow<String?>(null)
    val isbn: StateFlow<String?> = _isbn.asStateFlow()

    fun publish(code: String) {
        _isbn.value = code
    }

    fun consume() {
        _isbn.value = null
    }
}
