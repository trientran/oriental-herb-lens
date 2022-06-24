package com.uri.lee.dl.instantsearch

import com.algolia.instantsearch.core.highlighting.HighlightedString
import com.algolia.instantsearch.highlighting.Highlightable
import com.algolia.search.model.Attribute
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Herb(
    val objectID: String,
    val viName: String,
    val sciName: String,
    val viUses: String,
    val viUsages: String,
    val viSideEffects: String,
    val viPrecautions: String,
    val viInteractions: String,
    val enUses: String,
    val enUsages: String,
    val enSideEffects: String,
    val enPrecautions: String,
    val enInteractions: String,
    override val _highlightResult: JsonObject?
) : Highlightable {

    public val highlightedName: HighlightedString?
        get() = getHighlight(Attribute("name"))
}