package com.n3sensei.app.data.proverb

import android.content.Context
import com.n3sensei.app.domain.furigana.Furigana
import com.n3sensei.app.domain.proverb.Proverb
import com.n3sensei.app.domain.proverb.ProverbRepository
import com.n3sensei.app.domain.proverb.ProverbSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ProverbFileDto(
    val format: String,
    val formatVersion: Int,
    val source: String,
    val proverbs: List<ProverbDto>,
)

@Serializable
internal data class ProverbDto(val id: String, val furigana: String, val translationVi: String, val noteVi: String? = null)

/** Reads `assets/content/proverbs.json`, shipped with the app, so the home screen never waits for a network. */
class AssetProverbRepository(private val context: Context) : ProverbRepository {

    override suspend fun load(): ProverbSet = withContext(Dispatchers.IO) {
        parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    companion object {
        const val ASSET = "content/proverbs.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** Entries with broken furigana or no translation are left out rather than shown wrong. */
        fun parse(text: String): ProverbSet {
            val file = json.decodeFromString(ProverbFileDto.serializer(), text)
            require(file.format == "n3sensei-proverbs" && file.formatVersion == 1) { "Not a proverb file" }
            val proverbs = file.proverbs
                .filter { Furigana.isValid(it.furigana) && it.translationVi.isNotBlank() }
                .distinctBy { it.id }
                .map { Proverb(it.id, it.furigana, it.translationVi, it.noteVi?.ifBlank { null }) }
            return ProverbSet(proverbs, file.source)
        }
    }
}
