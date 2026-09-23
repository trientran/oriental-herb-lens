package com.uri.lee.dl.domain.model

/** One raw classifier output: the model label and its confidence (0..1). */
data class Classification(val label: String, val confidence: Float)

/** A classifier result joined with its catalog entry; [species] is null for a label the catalog lacks. */
data class RecognizedHerb(
    val label: String,
    val confidence: Float,
    val species: Species?,
)
