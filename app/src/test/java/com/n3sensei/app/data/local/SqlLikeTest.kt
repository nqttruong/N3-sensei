package com.n3sensei.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SqlLikeTest {

    @Test
    fun wrapsInWildcards() {
        assertEquals("%経験%", SqlLike.contains("経験"))
    }

    @Test
    fun escapesLikeMetacharacters() {
        assertEquals("%100\\%\\_a\\\\b%", SqlLike.contains("100%_a\\b"))
    }
}
