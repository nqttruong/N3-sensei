package com.n3sensei.app.domain.repository

import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ResolvedSource

/**
 * READ-ONLY check of Contrast items against the existing grammar / vocabulary / kanji rows
 * (no foreign key, D3). Implementations read the stored row values (not user edits) and decide
 * with [com.n3sensei.app.domain.contrast.ContrastSourceVerifier]; they never relink or delete.
 */
interface ContrastSourceResolver {
    /** Status of each item's source, keyed by item id; one query per content type. */
    suspend fun resolve(items: List<ContrastItem>): Map<String, ResolvedSource>
}
