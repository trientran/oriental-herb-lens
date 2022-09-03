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
        @Field("key") key: String = "26qrIzVXzPe1m1NrnbvgRvMslW0NAvzPmrCgWLDd",
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
