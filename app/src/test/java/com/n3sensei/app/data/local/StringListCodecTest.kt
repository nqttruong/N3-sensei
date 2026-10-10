package com.n3sensei.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StringListCodecTest {

    @Test
    fun roundTripsReadingsWithPunctuation() {
        val readings = listOf("ま.す", "ふ.える", "-ぶ")
        assertEquals(readings, StringListCodec.decode(StringListCodec.encode(readings)))
    }

    @Test
    fun emptyListRoundTrips() {
        assertEquals("", StringListCodec.encode(emptyList()))
        assertEquals(emptyList<String>(), StringListCodec.decode(""))
    }

    @Test
    fun rejectsValueContainingSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            StringListCodec.encode(listOf("a\u001Fb"))
        }
    }
}
