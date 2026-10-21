package com.uri.lee.dl.domain.model

/** A language the app's interface is in: its ISO 639-1 code, its own name, and its writing direction. */
data class AppLanguage(val code: String, val nativeName: String, val rightToLeft: Boolean = false)

/**
 * The interface's languages: English and Vietnamese, written for the app, and the languages Pl@ntNet
 * offers, machine-translated (interface text only; species names, the privacy policy and the terms
 * stay as they are). Each has a `values-<code>` folder of strings in core/designsystem; Android's
 * locales_config.xml and iOS's CFBundleLocalizations list the same codes.
 */
object AppLanguages {
    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("ar", "العربية", rightToLeft = true),
        AppLanguage("az", "Azərbaycanca"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("bs", "Bosanski"),
        AppLanguage("bg", "Български"),
        AppLanguage("zh", "简体中文"),
        AppLanguage("hr", "Hrvatski"),
        AppLanguage("cs", "Čeština"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("eo", "Esperanto"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("el", "Ελληνικά"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("hu", "Magyar"),
        AppLanguage("it", "Italiano"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("la", "Latina"),
        AppLanguage("ln", "Lingála"),
        AppLanguage("lt", "Lietuvių"),
        AppLanguage("mg", "Malagasy"),
        AppLanguage("mr", "मराठी"),
        AppLanguage("ne", "नेपाली"),
        AppLanguage("fa", "فارسی", rightToLeft = true),
        AppLanguage("pl", "Polski"),
        AppLanguage("pt", "Português"),
        AppLanguage("ro", "Română"),
        AppLanguage("ru", "Русский"),
        AppLanguage("sr", "Српски"),
        AppLanguage("si", "සිංහල"),
        AppLanguage("es", "Español"),
        AppLanguage("sv", "Svenska"),
        AppLanguage("sw", "Kiswahili"),
        AppLanguage("th", "ไทย"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("uk", "Українська"),
    )

    val CODES: List<String> = ALL.map { it.code }

    fun of(code: String): AppLanguage? = ALL.firstOrNull { it.code == code }

    fun isRightToLeft(code: String): Boolean = of(code)?.rightToLeft == true

    /** The languages written for the app: all there is while Remote Config's `all_languages` is off. */
    val WRITTEN: List<String> = listOf("en", "vi")

    /** This run's languages, for the interface and for name suggestions: [CODES], or [WRITTEN]. Set by [start]. */
    var offered: List<String> = CODES
        private set

    /**
     * Sets this run's languages from the last `all_languages` value saved (see [LanguageSwitchStore]),
     * before the app shows anything. Returns the language to show the interface in instead of the
     * system's [preferred] one (English, while the others are off), or null to keep it.
     */
    fun start(allLanguages: Boolean, preferred: String): String? {
        offered = if (allLanguages) CODES else WRITTEN
        return if (preferred in offered || allLanguages) null else NameLanguages.ENGLISH
    }
}

/**
 * Keeps Remote Config's `all_languages` for the next launch: the interface's language is fixed as the
 * app starts, before Remote Config can be read, so a change applies from the launch after it's seen.
 */
fun interface LanguageSwitchStore {
    fun save(allLanguages: Boolean)
}
