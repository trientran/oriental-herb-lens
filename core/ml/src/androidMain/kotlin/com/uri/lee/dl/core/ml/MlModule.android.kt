package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val mlModule: Module = module {
    single<HerbClassifier> { MlKitHerbClassifier(get()) }
    single<ObjectFinder> { MlKitObjectFinder() }
    single<PhotoReader> { AndroidPhotoReader(androidContext()) }
    single<ImageCompressor> { AndroidImageCompressor(androidContext()) }
}
