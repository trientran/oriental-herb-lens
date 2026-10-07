package com.uri.lee.dl.domain.sharing

import kotlinx.coroutines.flow.Flow

/** A model someone shared from the Train tab, for anyone to add to their own models. */
data class CommunityModel(
    val id: String,
    val name: String,
    /** What it can identify, as its sharer named them. */
    val species: List<String>,
    /** The image embedder it was trained on, e.g. "mobilenet_v3_large". */
    val backbone: String,
    /** Made in Herb Lens with what it needs to go on learning where it's added. */
    val trainable: Boolean,
    val url: String,
    val sizeBytes: Int,
    val uploaderId: String,
    /** Whether it's also on Hugging Face, in the project's organisation (after review). */
    val huggingFace: HuggingFaceStatus = HuggingFaceStatus.NONE,
    /** Its page there, once published. */
    val huggingFaceUrl: String? = null,
)

/**
 * Publishing a shared model in the project's Hugging Face organisation, where researchers find
 * models: the sharer asks for it, an administrator reviews it and publishes it (tools/moderate.py).
 */
enum class HuggingFaceStatus { NONE, REQUESTED, PUBLISHED, DECLINED }

enum class ModelReportReason { OFFENSIVE, PERSONAL_INFORMATION, MISLEADING, SPAM, OTHER }

/**
 * Models shared with everyone (App Store guideline 1.2 and Google Play's user-generated content
 * policy apply, as for photos): sharing needs an account; anyone may browse; each model can be
 * reported, and a sharer's models hidden.
 */
interface CommunityModelRepository {
    /** Newest first, without the ones this user hid. */
    fun observe(): Flow<List<CommunityModel>>

    /**
     * Uploads [file] (a .tflite of at most [SharingRules.MAX_BYTES]) as the shared model [id] (a UUID
     * the app picks) and lists it; [offerToHuggingFace] publishes it on Hugging Face too. Safe to
     * repeat with the same [id] after a failure: steps already done are kept. Needs a signed-in user.
     */
    suspend fun share(
        id: String,
        name: String,
        species: List<String>,
        backbone: String,
        trainable: Boolean,
        file: ByteArray,
        offerToHuggingFace: Boolean = false,
    ): CommunityModel

    /** Takes down a model this user shared. */
    suspend fun remove(model: CommunityModel)

    suspend fun download(model: CommunityModel): ByteArray

    /** Reports a model to the administrator and hides it for this user. Needs a signed-in user. */
    suspend fun report(model: CommunityModel, reason: ModelReportReason)

    /** Hides everything this sharer shared, models and photos, for this user. */
    suspend fun hideUploader(uploaderId: String)
}

/** Why a model can't be shared as it is; the user can rename it, or its species, and try again. */
enum class SharingProblem { NAME_LENGTH, TOO_FEW_SPECIES, CONTACT_DETAILS, OFFENSIVE_WORDS, TOO_LARGE }

/**
 * Checks made before a model is shared. Names are typed by users and shown to everyone, so they
 * mustn't carry contact details or obvious abuse; reports and the administrator catch the rest.
 */
object SharingRules {
    const val MAX_BYTES = 25 * 1024 * 1024
    const val MAX_NAME = 60
    const val MAX_SPECIES = 500
    const val LICENSE = "CC-BY-4.0"

    private val email = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")
    private val link = Regex("""(https?://|www\.)|\b[\w-]+\.(com|net|org|io|vn|edu|gov|info|me)\b""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""(\+?\d[\d .()-]{6,}\d)""")

    /** Matched as whole words; Vietnamese with its diacritics, as many folded forms are ordinary words. */
    private val blocked = setOf(
        "fuck", "fucking", "fucker", "shit", "cunt", "bitch", "porn", "porno", "nazi",
        "địt", "lồn", "cặc", "đụ", "đéo", "buồi", "đĩ",
    )

    fun problem(name: String, species: List<String>, sizeBytes: Int): SharingProblem? {
        val texts = listOf(name) + species
        return when {
            name.isBlank() || name.length > MAX_NAME -> SharingProblem.NAME_LENGTH
            species.size < 2 || species.size > MAX_SPECIES -> SharingProblem.TOO_FEW_SPECIES
            sizeBytes > MAX_BYTES -> SharingProblem.TOO_LARGE
            texts.any { email.containsMatchIn(it) || link.containsMatchIn(it) || phone.containsMatchIn(it) } -> SharingProblem.CONTACT_DETAILS
            texts.any { text -> text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).any { it in blocked } } -> SharingProblem.OFFENSIVE_WORDS
            else -> null
        }
    }
}
