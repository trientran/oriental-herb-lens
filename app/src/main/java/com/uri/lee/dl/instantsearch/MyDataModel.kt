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
    // All fields must match the fields in Algolia database, unless we can annotate somehow?
    val objectID: String,
    val enDosing: String? = null,
    val enInteractions: String? = null,
    val enName: String? = null,
    val enOverview: String? = null,
    val enSideEffects: String? = null,
    val id: String? = null,
    val latinName: String? = null,
    val viDosing: String? = null,
    val viInteractions: String? = null,
    val viName: String? = null,
    val viOverview: String? = null,
    val viSideEffects: String? = null,
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
