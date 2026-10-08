package com.uri.lee.dl.core.designsystem

/**
 * The project site on Cloudflare Pages (website/ in this repository). Also listed in the store
 * listings and the Google sign-in consent screen, so update those when these change.
 */
object LegalLinks {
    const val SITE = "https://med-herb-lens.pages.dev/"

    /** Made from the repository's README.md when the site is built (webApp: aboutPage). */
    const val ABOUT = "https://med-herb-lens.pages.dev/about.html"

    /** The same in Vietnamese, from README.vi.md. */
    const val ABOUT_VI = "https://med-herb-lens.pages.dev/about-vi.html"
    const val PRIVACY_POLICY = "https://med-herb-lens.pages.dev/pages/privacy-policy.html"
    const val TERMS_OF_SERVICE = "https://med-herb-lens.pages.dev/pages/terms-of-service.html"

    /** The apps' store pages, for the web app's pointers to what only the apps do. */
    const val GOOGLE_PLAY = "https://play.google.com/store/apps/details?id=com.uri.lee.dl"
    const val APP_STORE = "https://apps.apple.com/app/id6819173768"
}
