package com.uri.lee.dl.data.upload

import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.upload.ImageApi
import java.io.IOException
import java.util.Base64

/** Uploads to im.ge. Replaced in Phase 2 by R2 uploads through a Cloudflare Worker (decision D4). */
class ImGeImageHost(private val api: ImageApi) : ImageHost {
    override suspend fun upload(jpeg: ByteArray): String {
        val response = api.uploadImage(Base64.getEncoder().encodeToString(jpeg))
        return response.body()?.image?.url
            ?: throw IOException("im.ge upload failed: HTTP ${response.code()}")
    }
}
