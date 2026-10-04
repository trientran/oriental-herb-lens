package com.uri.lee.dl.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.remoteconfig.remoteConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/** Firebase clients. The platform initialises Firebase itself (and App Check) before Koin starts. */
val firebaseModule: Module = module {
    single { AuthClient(Firebase.auth) }
    single { FirestoreClient(Firebase.firestore) }
    single { RemoteConfigClient(Firebase.remoteConfig, get()) }
}
