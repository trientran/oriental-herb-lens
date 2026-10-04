package com.uri.lee.dl.core.ml

import org.koin.core.module.Module

/** Which model file to load: a downloaded one when installed, otherwise the copy bundled with the app. */
sealed interface HerbModelFile {
    /** Changes whenever the model file changes, so cached labelers can be rebuilt. */
    val cacheKey: String

    /** Bundled with the app: an Android asset or an iOS bundle resource. */
    data class Bundled(val name: String) : HerbModelFile {
        override val cacheKey = "bundled"
    }

    data class Installed(val path: String, override val cacheKey: String) : HerbModelFile
}

/** Supplied by core:data, which knows where content sync installs files. */
fun interface HerbModelLocator {
    fun current(): HerbModelFile
}

/** Binds [com.uri.lee.dl.domain.ml.HerbClassifier]; needs a [HerbModelLocator] in the graph. */
expect val mlModule: Module
