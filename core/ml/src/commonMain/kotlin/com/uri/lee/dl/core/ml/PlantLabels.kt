package com.uri.lee.dl.core.ml

/**
 * Labels of ML Kit's general image labeller that mean a photo shows a plant. One is enough for
 * a photo to be shared; photos of people or explicit content get none of these.
 */
internal val PLANT_LABELS = setOf(
    "Plant", "Flower", "Petal", "Leaf", "Tree", "Herb", "Vegetable", "Fruit", "Garden", "Forest",
    "Moss", "Bonsai", "Flowerpot", "Grass", "Cactus", "Bamboo", "Lily", "Sunflower", "Rose", "Vegetation",
)

/** Labels this certain or more count. */
internal const val PLANT_LABEL_CONFIDENCE = 0.5f
