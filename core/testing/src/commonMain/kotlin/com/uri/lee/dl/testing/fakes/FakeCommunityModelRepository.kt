package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.sharing.CommunityModel
import com.uri.lee.dl.domain.sharing.CommunityModelRepository
import com.uri.lee.dl.domain.sharing.HuggingFaceStatus
import com.uri.lee.dl.domain.sharing.ModelReportReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class FakeCommunityModelRepository(private val uid: () -> String? = { "user-1" }) : CommunityModelRepository {
    val shared = MutableStateFlow<List<CommunityModel>>(emptyList())
    val files = mutableMapOf<String, ByteArray>()
    val reports = mutableListOf<Pair<String, ModelReportReason>>()
    private val hiddenModels = MutableStateFlow(emptySet<String>())
    private val hiddenPeople = MutableStateFlow(emptySet<String>())
    var failUpload = false

    override fun observe(): Flow<List<CommunityModel>> = combine(shared, hiddenModels, hiddenPeople) { models, ids, people ->
        models.filter { it.id !in ids && it.uploaderId !in people }
    }

    override suspend fun share(
        name: String,
        species: List<String>,
        backbone: String,
        trainable: Boolean,
        file: ByteArray,
        offerToHuggingFace: Boolean,
    ): CommunityModel {
        if (failUpload) error("upload failed")
        val uploader = checkNotNull(uid()) { "Sign in to share" }
        val id = "shared-${shared.value.size + 1}"
        files[id] = file
        val model = CommunityModel(
            id, name, species, backbone, trainable, "https://r2/models/$id.tflite", file.size, uploader,
            huggingFace = if (offerToHuggingFace) HuggingFaceStatus.REQUESTED else HuggingFaceStatus.NONE,
        )
        shared.update { listOf(model) + it }
        return model
    }

    override suspend fun remove(model: CommunityModel) = shared.update { list -> list.filterNot { it.id == model.id } }

    override suspend fun download(model: CommunityModel): ByteArray = files.getValue(model.id)

    override suspend fun report(model: CommunityModel, reason: ModelReportReason) {
        hiddenModels.update { it + model.id }
        reports += model.id to reason
    }

    override suspend fun hideUploader(uploaderId: String) = hiddenPeople.update { it + uploaderId }
}
