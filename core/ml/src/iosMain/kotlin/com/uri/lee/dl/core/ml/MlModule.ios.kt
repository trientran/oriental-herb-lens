package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.moderation.PlantCheck
import org.koin.core.module.Module
import org.koin.dsl.module

/** Needs the Swift [NativeHerbLabeler], [NativeObjectDetector] and [NativeGeneralLabeler] in the graph (see startKoinIos). */
actual val mlModule: Module = module {
    single<HerbClassifier> { IosHerbClassifier(get(), get()) }
    single<ObjectFinder> { IosObjectFinder(get()) }
    single<ImageCropper> { IosImageCropper() }
    single<PlantCheck> { IosPlantCheck(get()) }
    single<PhotoReader> { IosPhotoReader() }
    single<ImageCompressor> { IosImageCompressor() }
}
