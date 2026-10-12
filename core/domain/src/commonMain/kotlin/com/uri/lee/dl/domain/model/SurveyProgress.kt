package com.uri.lee.dl.domain.model

/**
 * Where the user is with the invitation to the research survey: shown once they have opened the
 * app [askAtOpen] times, and again a few opens after "Not now" the first time; never after that,
 * nor once they have opened the survey.
 */
data class SurveyProgress(
    val appOpens: Int = 0,
    val askAtOpen: Int = FIRST_ASK,
    val finished: Boolean = false,
) {
    val due: Boolean get() = !finished && appOpens >= askAtOpen

    companion object {
        const val FIRST_ASK = 3
        const val ASK_AGAIN_AFTER = 5
    }
}
