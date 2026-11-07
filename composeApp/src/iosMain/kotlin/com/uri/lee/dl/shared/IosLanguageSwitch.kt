package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.model.AppLanguages
import com.uri.lee.dl.domain.model.LanguageSwitchStore
import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/**
 * Remote Config's `all_languages`, saved for the next launch. While it's off, a language other than
 * English or Vietnamese is replaced by English, region kept, for this run only: in the launch arguments'
 * defaults, which come first and aren't saved, so the user's own language settings stay as they are.
 * Compose's text and resources read NSLocale.preferredLanguages, which follows it straight away.
 */
internal object IosLanguageSwitch : LanguageSwitchStore {

    private const val ALL_LANGUAGES = "herblens.allLanguages"
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override fun save(allLanguages: Boolean) = defaults.setBool(allLanguages, ALL_LANGUAGES)

    /**
     * Call at launch, before anything reads the locale. Chinese for Hong Kong, Macau or Taiwan without a
     * script ("zh-HK") is given its script the same way, as Compose's resources find Traditional Chinese by it.
     */
    fun apply() {
        val all = defaults.objectForKey(ALL_LANGUAGES) == null || defaults.boolForKey(ALL_LANGUAGES)
        val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String)?.let { NSLocale(it) }
        val language = preferred?.objectForKey(NSLocaleLanguageCode) as? String ?: "en"
        val script = preferred?.objectForKey(NSLocaleScriptCode) as? String
        val region = preferred?.objectForKey(NSLocaleCountryCode) as? String
        val code = AppLanguages.codeFor(language, script.orEmpty(), region.orEmpty())
        val shown = AppLanguages.start(all, code)
            ?: if (code == "zh-Hant" && script == null) "zh-Hant" else return
        val arguments = defaults.volatileDomainForName(NSArgumentDomain).toMutableMap()
        arguments["AppleLanguages"] = listOf(if (region == null) shown else "$shown-$region")
        defaults.removeVolatileDomainForName(NSArgumentDomain)
        defaults.setVolatileDomain(arguments, NSArgumentDomain)
    }
}
