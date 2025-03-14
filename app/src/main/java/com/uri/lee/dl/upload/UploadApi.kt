package com.uri.lee.dl.upload

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ImageApi {

    @POST("upload")
    @FormUrlEncoded
    suspend fun uploadImage(
        @Field("source") base64String: String,
        @Field("key") key: String = "imge_11zJ_34276db55badeb61b8f7259a29db882a00665519f10a20abbd0823b8ddb114a7b98bbfcf62cb73479f1d0272f7ed0d7ffe44035e0fc0f5167ac0e0a2ea58d3b6",
        @Field("format") format: String = "json"
    ): Response<UploadedImage>
}

data class UploadedImage(
    @Expose @SerializedName("image") val image: Image,
) {
    data class Image(
        @Expose @SerializedName("filename") val fileName: String,
        @Expose @SerializedName("url") val url: String,
    )
}

object RetrofitHelper {

    private const val baseUrl = "https://im.ge/api/1/"

    fun getInstance(): Retrofit {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        return Retrofit.Builder().baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            // we need to add converter factory to
            // convert JSON object to Java object
            .build()
    }
}
