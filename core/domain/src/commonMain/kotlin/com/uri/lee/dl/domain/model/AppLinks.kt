package com.uri.lee.dl.domain.model

/** Where Profile sends people: the contact addresses and the dataset. Published remotely, so they change without a release. */
data class AppLinks(
    /** One email goes to all of them; the university's first. */
    val contactEmails: List<String> = DEFAULT_CONTACT_EMAILS,
    /** The species list with the image data, on Kaggle (which counts the downloads). */
    val datasetUrl: String = DEFAULT_DATASET_URL,
    /** The research survey (Qualtrics); null once the study has closed. */
    val surveyUrl: String? = DEFAULT_SURVEY_URL,
) {
    /** The survey in the app's language: Qualtrics shows its Vietnamese translation for "vi", English otherwise. */
    fun surveyLink(language: String): String? = surveyUrl?.let {
        it + (if ('?' in it) "&" else "?") + "Q_Language=" + (if (language == "vi") "VI" else "EN")
    }

    companion object {
        val DEFAULT_CONTACT_EMAILS = listOf("ttran72@myune.edu.au", "tptrien@gmail.com")
        const val DEFAULT_DATASET_URL = "https://www.kaggle.com/datasets/trientran/viet-medi-species-2026"
        const val DEFAULT_SURVEY_URL = "https://unesurveys.au1.qualtrics.com/jfe/form/SV_6MtFwcscSiEnLvw"
    }
}
