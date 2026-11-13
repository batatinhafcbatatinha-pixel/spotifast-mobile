package io.github.batatinha.spotifast

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingTest {
    @Test
    fun the_rest_of_a_list_is_every_page_after_the_first() {
        assertEquals(listOf(50, 100, 150, 200), remainingOffsets(entriesRead = 50, total = 201, limit = 50))
        assertEquals(listOf(100), remainingOffsets(entriesRead = 100, total = 150, limit = 100))
    }

    @Test
    fun a_list_that_fits_one_page_needs_no_more() {
        assertEquals(emptyList<Int>(), remainingOffsets(entriesRead = 14, total = 14, limit = 100))
    }

    @Test
    fun an_empty_first_page_does_not_loop() {
        assertEquals(emptyList<Int>(), remainingOffsets(entriesRead = 0, total = 500, limit = 50))
    }
}
