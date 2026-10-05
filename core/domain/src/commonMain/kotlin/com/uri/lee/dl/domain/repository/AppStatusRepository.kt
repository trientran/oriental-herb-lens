package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.AppStatus
import kotlinx.coroutines.flow.Flow

interface AppStatusRepository {
    fun observe(): Flow<AppStatus>
}
