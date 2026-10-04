package com.uri.lee.dl.labeling

import com.uri.lee.dl.domain.model.RecognizedHerb

/** Maps domain recognition results to the scan screens' display model, keeping result order. */
fun List<RecognizedHerb>.toHerbs(): List<Herb> = map { it.toHerb() }

fun RecognizedHerb.toHerb() = Herb(
    id = label,
    latinName = species?.scientificName,
    viName = species?.preferredVietnameseName,
    enName = species?.preferredEnglishName,
    confidence = confidence,
)
