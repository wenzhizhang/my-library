package top.dingfengbo.mylibrary.ui.books

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.dingfengbo.mylibrary.api.models.IsbnLookupResponse

/**
 * The form's conversion rules. Every one of these is a silent-wrong-data risk: a price that becomes
 * null, tags that stay glued together, or a lookup that overwrites what the user already typed.
 */
class BookFormStateTest {

    private fun lookup(
        title: String = "琴学门径",
        titleCn: String = "琴學門徑",
        authorIds: List<Int> = listOf(226),
        authorNames: List<String> = listOf("张子盛"),
        publisherId: Int? = 5,
        publisherName: String = "中国书店",
        tags: List<String> = listOf("古琴", "音乐"),
    ) = IsbnLookupResponse(
        isbn = "9787806631744",
        title = title,
        titleCn = titleCn,
        authorIds = authorIds,
        authorNames = authorNames,
        publisherId = publisherId,
        publisherName = publisherName,
        tagNames = tags,
        source = "douban",
    )

    @Test
    fun `lookup fills empty fields`() {
        val filled = BookFormState().mergedWith(lookup())

        assertEquals("琴学门径", filled.title)
        assertEquals("琴學門徑", filled.titleCn)
        assertEquals(listOf(RefChoice(226, "张子盛")), filled.authors)
        assertEquals(5, filled.publisher?.id)
        assertEquals("古琴, 音乐", filled.tags)
    }

    @Test
    fun `lookup never overwrites what the user typed`() {
        val typed = BookFormState(
            title = "我自己写的书名",
            publisher = RefChoice(9, "手工出版社"),
            tags = "我的标签",
        )

        val filled = typed.mergedWith(lookup())

        assertEquals("我自己写的书名", filled.title)
        assertEquals(RefChoice(9, "手工出版社"), filled.publisher)
        assertEquals("我的标签", filled.tags)
        assertEquals("琴學門徑", filled.titleCn) // still empty before the lookup, so it gets filled
    }

    @Test
    fun `submit needs an isbn and at least one title form`() {
        assertFalse(BookFormState(title = "x").canSubmit)
        assertFalse(BookFormState(isbn = "9787806631744").canSubmit)
        assertTrue(BookFormState(isbn = "9787806631744", title = "x").canSubmit)
        assertTrue(BookFormState(isbn = "9787806631744", titleCn = "中文名").canSubmit)
    }

    @Test
    fun `creation maps text fields to typed values and blanks to null`() {
        val state = BookFormState(
            isbn = " 9787806631744 ",
            title = " 琴学门径 ",
            titleCn = "琴學門徑",
            authors = listOf(RefChoice(226, "张子盛")),
            publisher = RefChoice(5, "中国书店"),
            pages = "231",
            price = "48.0",
            doubanScore = "8.1",
            tags = "古琴, 音乐，弦歌",
            readState = "reading",
            inWish = true,
            summary = "   ",
        )

        val created = state.toCreation()

        assertEquals("9787806631744", created.isbn)
        assertEquals("琴学门径", created.title)
        assertEquals(231, created.pages)
        assertEquals(BigDecimal("48.0"), created.price)
        assertEquals(listOf(226), created.authorIds)
        assertEquals(5, created.publisherId)
        assertEquals(listOf("古琴", "音乐", "弦歌"), created.tags)
        assertEquals("reading", created.readState)
        assertEquals(true, created.inWish)
        assertNull("a whitespace-only summary is not a value", created.summary)
        assertNull(created.introduction)
    }

    @Test
    fun `unparseable numbers become null instead of throwing`() {
        val created = BookFormState(
            isbn = "1",
            title = "t",
            pages = "十二页",
            price = "约五十元",
            printedNumber = "",
        ).toCreation()

        assertNull(created.pages)
        assertNull(created.price)
        assertNull(created.printedNumber)
    }

    @Test
    fun `update carries the link field that creation cannot send`() {
        val state = BookFormState(isbn = "1", title = "t", link = "https://example.com/book")

        assertEquals("https://example.com/book", state.toUpdate().link)
    }

    @Test
    fun `an existing book round-trips into the form and back`() {
        val form = BookFormState.from(
            top.dingfengbo.mylibrary.api.models.BookResponse(
                id = 7,
                isbn = "9787806631744",
                title = "琴学门径",
                titleCn = "琴學門徑",
                pages = 231,
                price = BigDecimal("48.0"),
                tags = listOf("古琴"),
                readState = "在读",
                inWish = false,
            )
        )

        assertEquals("琴学门径", form.title)
        assertEquals("231", form.pages)
        assertEquals("48.0", form.price)
        assertEquals("古琴", form.tags)
        // A value outside the four the web client writes must survive an edit untouched.
        assertEquals("在读", form.readState)
        assertNull(ReadState.labelResFor("在读"))
        assertEquals(231, form.toUpdate().pages)
    }
}
