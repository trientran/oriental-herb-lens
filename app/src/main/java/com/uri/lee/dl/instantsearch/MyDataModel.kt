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
    val viName: String? = null,
    val sciName: String? = null,
    val viUses: String? = null,
    val viUsages: String? = null,
    val viSideEffects: String? = null,
    val viPrecautions: String? = null,
    val viInteractions: String? = null,
    val enUses: String? = null,
    val enUsages: String? = null,
    val enSideEffects: String? = null,
    val enPrecautions: String? = null,
    val enInteractions: String? = null,
    @IgnoredOnParcel
    override val _highlightResult: JsonObject? = null
) : Highlightable, Parcelable {
    @IgnoredOnParcel
    val highlightedNameLatin: HighlightedString?
        get() = getHighlight(Attribute(::sciName.name)) // todo be careful if changing this because it needs to match with the field in Algolia database

    @IgnoredOnParcel
    val highlightedNameVi: HighlightedString?
        get() = getHighlight(Attribute(::viName.name))
}


data class Herb2(
    val viName: String? = null,
    val sciName: String? = null,
    val viUses: String? = null,
    val viUsages: String? = null,
    val viSideEffects: String? = null,
    val viPrecautions: String? = null,
    val viInteractions: String? = null,
    val enUses: String? = null,
    val enUsages: String? = null,
    val enSideEffects: String? = null,
    val enPrecautions: String? = null,
    val enInteractions: String? = null,
)