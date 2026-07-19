package com.fatih.litepdf

import com.fatih.litepdf.util.PageJumpValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageJumpValidatorTest {
    @Test
    fun validInputReturnsZeroBasedIndex() {
        assertEquals(4, PageJumpValidator.validate("5", pageCount = 10))
    }

    @Test
    fun invalidInputReturnsNull() {
        assertNull(PageJumpValidator.validate("0", pageCount = 10))
        assertNull(PageJumpValidator.validate("11", pageCount = 10))
        assertNull(PageJumpValidator.validate("abc", pageCount = 10))
        assertNull(PageJumpValidator.validate("1", pageCount = 0))
    }
}
