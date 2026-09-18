package top.dingfengbo.mylibrary.ui.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which barcode a scan is allowed to accept.
 *
 * A book cover carries more than one code — the ISBN plus a price or supplementary barcode — and
 * the detector returns them in no particular order. Taking whichever came back first is how a scan
 * landed on the wrong code and needed repeating until the ISBN happened to come first.
 */
class IsbnBarcodeSelectionTest {

    @Test
    fun `takes the ISBN when the cover also carries a price barcode`() {
        assertEquals("9787020024759", isbnFrom(listOf("6901234567892", "9787020024759")))
    }

    @Test
    fun `takes the ISBN when it is the first code in the frame`() {
        assertEquals("9787020024759", isbnFrom(listOf("9787020024759", "6901234567892")))
    }

    @Test
    fun `delivers nothing rather than a barcode that is not an ISBN`() {
        assertNull(isbnFrom(listOf("6901234567892", "12345670")))
    }

    @Test
    fun `ignores a 12 digit UPC-A`() {
        assertNull(isbnFrom(listOf("012345678905")))
    }

    @Test
    fun `accepts the 979 range`() {
        assertEquals("9791234567896", isbnFrom(listOf("9791234567896")))
    }

    @Test
    fun `an empty frame delivers nothing`() {
        assertNull(isbnFrom(emptyList()))
    }
}
