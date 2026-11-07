package com.uri.lee.dl.domain.model

/**
 * A language the app's interface is in: its code, its own name, and its writing direction. The code is
 * a BCP 47 tag: an ISO 639 language (two letters, or three for Santali), with a script or region only
 * where one language has two interfaces ("zh-Hant" next to "zh", "pt-BR" next to "pt").
 */
data class AppLanguage(val code: String, val nativeName: String, val rightToLeft: Boolean = false)

/**
 * The interface's languages: English and Vietnamese, written for the app, and the languages Pl@ntNet
 * offers, machine-translated (interface text only; species names, the privacy policy and the terms
 * stay as they are). Each has a `values-<code>` folder of strings in core/designsystem (`values-b+zh+Hant`
 * and `values-pt-rBR` for the two with a script or region); Android's locales_config.xml and iOS's
 * CFBundleLocalizations list the same codes.
 */
object AppLanguages {
    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("ar", "العربية", rightToLeft = true),
        AppLanguage("az", "Azərbaycanca"),
        AppLanguage("eu", "Euskara"),
        AppLanguage("be", "Беларуская"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("bs", "Bosanski"),
        AppLanguage("br", "Brezhoneg"),
        AppLanguage("bg", "Български"),
        AppLanguage("ca", "Català"),
        AppLanguage("zh", "简体中文"),
        AppLanguage("zh-Hant", "繁體中文"),
        AppLanguage("hr", "Hrvatski"),
        AppLanguage("cs", "Čeština"),
        AppLanguage("da", "Dansk"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("eo", "Esperanto"),
        AppLanguage("et", "Eesti"),
        AppLanguage("fi", "Suomi"),
        AppLanguage("fr", "Français"),
        AppLanguage("gl", "Galego"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("el", "Ελληνικά"),
        AppLanguage("he", "עברית", rightToLeft = true),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("hu", "Magyar"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("it", "Italiano"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("la", "Latina"),
        AppLanguage("ln", "Lingála"),
        AppLanguage("lt", "Lietuvių"),
        AppLanguage("mg", "Malagasy"),
        AppLanguage("ms", "Bahasa Melayu"),
        AppLanguage("ml", "മലയാളം"),
        AppLanguage("mr", "मराठी"),
        AppLanguage("ne", "नेपाली"),
        AppLanguage("nb", "Norsk bokmål"),
        AppLanguage("oc", "Occitan"),
        AppLanguage("fa", "فارسی", rightToLeft = true),
        AppLanguage("pl", "Polski"),
        AppLanguage("pt", "Português"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("ro", "Română"),
        AppLanguage("ru", "Русский"),
        AppLanguage("sat", "ᱥᱟᱱᱛᱟᱲᱤ"),
        AppLanguage("sr", "Српски"),
        AppLanguage("si", "සිංහල"),
        AppLanguage("sk", "Slovenčina"),
        AppLanguage("sl", "Slovenščina"),
        AppLanguage("es", "Español"),
        AppLanguage("sv", "Svenska"),
        AppLanguage("sw", "Kiswahili"),
        AppLanguage("th", "ไทย"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("uk", "Українська"),
        AppLanguage("ur", "اردو", rightToLeft = true),
        AppLanguage("cy", "Cymraeg"),
    )
    val CODES: List<String> = ALL.map { it.code }

    fun of(code: String): AppLanguage? = ALL.firstOrNull { it.code == code }

    fun isRightToLeft(code: String): Boolean = of(code)?.rightToLeft == true

    /** Regions whose Chinese is written in Traditional characters when the locale names no script. */
    private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

    /**
     * The interface language for a locale's [language], [script] and [region], as the platforms report
     * them: Android before 14 says "iw" and "in" for Hebrew and Indonesian, some systems say "no" for
     * Norwegian Bokmål or "zh-TW" without the script. A language without an interface comes back as is.
     */
    fun codeFor(language: String, script: String = "", region: String = ""): String {
        val lang = when (language.lowercase()) {
            "iw" -> "he"
            "in" -> "id"
            "no" -> "nb"
            else -> language.lowercase()
        }
        return when {
            lang == "zh" && (script == "Hant" || script.isEmpty() && region.uppercase() in TRADITIONAL_CHINESE_REGIONS) -> "zh-Hant"
            lang == "pt" && region.uppercase() == "BR" -> "pt-BR"
            else -> lang
        }
    }

    /** [codeFor] a BCP 47 tag such as a browser's "zh-TW" or "pt-BR". */
    fun codeForTag(tag: String): String {
        val parts = tag.replace('_', '-').split('-')
        val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }.orEmpty()
        val region = parts.drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }.orEmpty()
        return codeFor(parts[0], script.lowercase().replaceFirstChar { it.uppercase() }, region)
    }

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
