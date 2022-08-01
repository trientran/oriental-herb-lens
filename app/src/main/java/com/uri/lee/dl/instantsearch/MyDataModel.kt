package com.uri.lee.dl.instantsearch

import android.os.Parcelable
import com.algolia.instantsearch.core.highlighting.HighlightedString
import com.algolia.instantsearch.highlighting.Highlightable
import com.algolia.search.model.Attribute
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@Parcelize
data class Herb(
    val objectID: String,
    val viName: String,
    val latinName: String,
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
    @IgnoredOnParcel
    override val _highlightResult: JsonObject? = null
) : Highlightable, Parcelable {
    @IgnoredOnParcel
    val highlightedNameLatin: HighlightedString?
        get() = getHighlight(Attribute(::latinName.name))

    @IgnoredOnParcel
    val highlightedNameVi: HighlightedString?
        get() = getHighlight(Attribute(::viName.name))
}
