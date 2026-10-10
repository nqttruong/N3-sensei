package com.n3sensei.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(values: List<String>): String = StringListCodec.encode(values)

    @TypeConverter
    fun toStringList(value: String): List<String> = StringListCodec.decode(value)
}

/**
 * Stores short string lists (e.g. kanji readings) in one column, separated by the ASCII Unit
 * Separator, which cannot occur in content text.
 */
internal object StringListCodec {
    private const val SEPARATOR = '\u001F'

    fun encode(values: List<String>): String {
        require(values.none { SEPARATOR in it }) { "List value contains the reserved separator" }
        return values.joinToString(SEPARATOR.toString())
    }

    fun decode(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)
}
