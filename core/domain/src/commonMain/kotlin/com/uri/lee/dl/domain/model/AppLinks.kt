package com.uri.lee.dl.domain.model

/** Where Profile sends people: the contact addresses and the dataset. Published remotely, so they change without a release. */
data class AppLinks(
    /** One email goes to all of them; the university's first. */
    val contactEmails: List<String> = DEFAULT_CONTACT_EMAILS,
    /** The species list with the image data, on Kaggle (which counts the downloads). */
    val datasetUrl: String = DEFAULT_DATASET_URL,
) {
    companion object {
        val DEFAULT_CONTACT_EMAILS = listOf("ttran72@myune.edu.au", "tptrien@gmail.com")
        const val DEFAULT_DATASET_URL = "https://www.kaggle.com/datasets/trientran/viet-medi-species-2026"
    }
}
