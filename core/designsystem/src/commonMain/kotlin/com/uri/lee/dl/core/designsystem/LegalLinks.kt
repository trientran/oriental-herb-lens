package com.uri.lee.dl.core.designsystem

/**
 * The project site on Cloudflare Pages (website/ in this repository), by its Pages address, which
 * is free and stays the project's: while the site has a custom domain (medherblens.com), a
 * Cloudflare redirect sends these links there, and if the domain goes, they still work. Also listed
 * in the store listings and the Google sign-in consent screen, so update those when these change.
 */
object LegalLinks {
    const val SITE = "https://med-herb-lens.pages.dev/"

    /** Made from the repository's README.md when the site is built (webApp: aboutPage). */
    const val ABOUT = "https://med-herb-lens.pages.dev/about.html"

    /** The same in Vietnamese, from README.vi.md. */
    const val ABOUT_VI = "https://med-herb-lens.pages.dev/about-vi.html"
    const val PRIVACY_POLICY = "https://med-herb-lens.pages.dev/pages/privacy-policy.html"
    const val TERMS_OF_SERVICE = "https://med-herb-lens.pages.dev/pages/terms-of-service.html"

    /** Translations of the legal pages; where they differ, the English ones prevail. */
    const val PRIVACY_POLICY_VI = "https://med-herb-lens.pages.dev/pages/privacy-policy-vi.html"
    const val TERMS_OF_SERVICE_VI = "https://med-herb-lens.pages.dev/pages/terms-of-service-vi.html"

    /** Each page in the app's [language]: Vietnamese for "vi", English otherwise. */
    fun about(language: String) = if (language == "vi") ABOUT_VI else ABOUT
    fun privacyPolicy(language: String) = if (language == "vi") PRIVACY_POLICY_VI else PRIVACY_POLICY
    fun termsOfService(language: String) = if (language == "vi") TERMS_OF_SERVICE_VI else TERMS_OF_SERVICE

    /** The apps' store pages, for the web app's pointers to what only the apps do. */
    const val GOOGLE_PLAY = "https://play.google.com/store/apps/details?id=com.uri.lee.dl"
    const val APP_STORE = "https://apps.apple.com/app/id6819173768"
}
