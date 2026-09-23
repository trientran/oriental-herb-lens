package com.uri.lee.dl

import com.google.firebase.firestore.DocumentSnapshot
import com.uri.lee.dl.instantsearch.Herb

fun DocumentSnapshot.toHerb() = herbFromFields(id) { field -> getString(field) }

/** Builds a [Herb] from a herb document's id and a reader of its string fields. */
internal fun herbFromFields(id: String, stringField: (name: String) -> String?) = Herb(
    objectID = id,
    id = id,
    enDosing = stringField("enDosing"),
    enInteractions = stringField("enInteractions"),
    enName = stringField("enName"),
    enOverview = stringField("enOverview"),
    enSideEffects = stringField("enSideEffects"),
    latinName = stringField("latinName"),
    viDosing = stringField("viDosing"),
    viInteractions = stringField("viInteractions"),
    viName = stringField("viName"),
    viOverview = stringField("viOverview"),
    viSideEffects = stringField("viSideEffects"),
)
