package com.uri.lee.dl.feature.training

import org.koin.core.module.dsl.viewModelOf
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.training.scoped
import com.uri.lee.dl.domain.upload.UploadQueue
import org.koin.dsl.bind
import org.koin.dsl.module

val trainingModule = module {
    single { UserModelStore(get()) }
    single { ModelShareQueue(get<AppFiles>().scoped("uploads/models"), get(), get(), get(), get()) } bind UploadQueue::class
    viewModelOf(::TrainingViewModel)
}
