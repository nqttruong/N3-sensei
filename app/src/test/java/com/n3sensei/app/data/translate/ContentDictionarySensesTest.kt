package com.n3sensei.app.data.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDictionarySensesTest {

    @Test
    fun eachSenseCounts_wholeOnly() {
        val senses = ContentDictionaryProvider.senses("khủng khiếp, rất nhiều")
        assertTrue("khủng khiếp" in senses)
        assertTrue("rất nhiều" in senses)
        assertTrue("khủng khiếp, rất nhiều" in senses)
        // Part of a sense is not a match.
        assertFalse("khủng" in senses)
        assertFalse("nhiều" in senses)
    }

    @Test
    fun caseSpacingAndBracketsAside() {
        assertEquals(setOf("chú ý"), ContentDictionaryProvider.senses("Chú  ý"))
        assertTrue("nuôi" in ContentDictionaryProvider.senses("nuôi (râu)"))
        assertTrue("trả lại" in ContentDictionaryProvider.senses("trở về; trả lại"))
    }
}
