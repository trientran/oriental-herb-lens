package com.uri.lee.dl.instantsearch

import android.os.Parcelable
import com.algolia.instantsearch.core.highlighting.HighlightedString
import com.algolia.instantsearch.highlighting.Highlightable
import com.algolia.search.model.Attribute
import kotlinx.android.parcel.Parcelize
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.RawValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.*

@Serializable
@Parcelize
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
    override val _highlightResult: @RawValue JsonObject?
) : Highlightable, Parcelable {
    @IgnoredOnParcel
    val herbAttributePerSystemLanguage =
        if (Locale.getDefault().displayLanguage == "English") ::sciName.name else ::viName.name

    @IgnoredOnParcel
    val highlightedName: HighlightedString?
        get() = getHighlight(Attribute(herbAttributePerSystemLanguage))
}