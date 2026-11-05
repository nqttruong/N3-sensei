package com.n3sensei.app.core.ui.state

import com.n3sensei.app.domain.course.ItemLocation
import com.n3sensei.app.domain.course.ItemLocations
import com.n3sensei.app.domain.course.SameWord
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where one item is studied, and the other lessons that have the same word (words only). */
data class DetailLocation(val location: ItemLocation, val sameWord: List<SameWord> = emptyList())

/** Live location of [ref]; null until computed (the screen then shows nothing rather than a guess). */
fun detailLocation(scope: CoroutineScope, contentRepository: ContentRepository, ref: ItemRef): StateFlow<DetailLocation?> =
    ItemLocations.observe(contentRepository)
        .map<ItemLocations, DetailLocation?> { all ->
            DetailLocation(all.of(ref), if (ref.type == ItemType.VOCABULARY) all.sameWord(ref.id) else emptyList())
        }
        .catch { emit(null) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

/** Live locations of every item, for list cards; empty until computed. */
fun itemLocations(scope: CoroutineScope, contentRepository: ContentRepository): StateFlow<ItemLocations> =
    ItemLocations.observe(contentRepository)
        .catch { emit(ItemLocations.EMPTY) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ItemLocations.EMPTY)
