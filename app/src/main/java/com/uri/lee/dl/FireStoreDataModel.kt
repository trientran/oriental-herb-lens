package com.uri.lee.dl

import com.uri.lee.dl.herbdetails.review.AddReviewState

// To map with FireStore document, each single property needs a default value, Long cannot be parsed as String
data class FireStoreHerb(
    val id: Long? = null,
    val enDosing: String = "",
    val enInteractions: String = "",
    val enName: String = "",
    val enOverview: String = "",
    val enSideEffects: String = "",
    val latinName: String = "",
    val viDosing: String = "",
    val viInteractions: String = "",
    val viName: String = "",
    val viOverview: String = "",
    val viSideEffects: String = "",
    val images: Map<String, String>? = null, // url - uid
    val reviews: Map<String, AddReviewState.Review>? = null, // instant - Review object
)
