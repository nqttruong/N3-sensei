package com.n3sensei.app.data.local

/** Builds LIKE patterns for queries declared with `ESCAPE '\'`. */
internal object SqlLike {
    fun contains(query: String): String = "%${escape(query)}%"

    fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
