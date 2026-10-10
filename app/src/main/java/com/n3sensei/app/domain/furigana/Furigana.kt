package com.n3sensei.app.domain.furigana

/**
 * Furigana markup used by content packs: `{base|reading}` marks ruby text, everything else is
 * plain text. Example: `{日本|にほん}に{来|き}たばかりです。`
 *
 * Braces and pipes are not valid inside Japanese text, so no escaping is defined.
 */
object Furigana {

    data class Segment(val text: String, val reading: String? = null)

    class MalformedFuriganaException(message: String) : IllegalArgumentException(message)

    fun parse(markup: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        val plain = StringBuilder()
        var i = 0
        while (i < markup.length) {
            when (val c = markup[i]) {
                '{' -> {
                    val close = markup.indexOf('}', startIndex = i + 1)
                    if (close < 0) throw MalformedFuriganaException("Unclosed '{' at $i in \"$markup\"")
                    val body = markup.substring(i + 1, close)
                    val parts = body.split('|')
                    if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty() || '{' in body) {
                        throw MalformedFuriganaException("Expected {base|reading} at $i in \"$markup\"")
                    }
                    if (plain.isNotEmpty()) {
                        segments += Segment(plain.toString())
                        plain.clear()
                    }
                    segments += Segment(parts[0], parts[1])
                    i = close + 1
                }
                '}', '|' -> throw MalformedFuriganaException("Unexpected '$c' at $i in \"$markup\"")
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        if (plain.isNotEmpty()) segments += Segment(plain.toString())
        return segments
    }

    /** Text without readings, e.g. for matching against the stored expression. */
    fun baseText(markup: String): String = parse(markup).joinToString("") { it.text }

    /** Text with every ruby replaced by its reading. */
    fun readingText(markup: String): String = parse(markup).joinToString("") { it.reading ?: it.text }

    fun isValid(markup: String): Boolean = runCatching { parse(markup) }.isSuccess
}
