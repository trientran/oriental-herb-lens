package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.model.AppLanguages
import com.uri.lee.dl.domain.model.LanguageSwitchStore
import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
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

    /** Call at launch, before anything reads the locale. */
    fun apply() {
        val all = defaults.objectForKey(ALL_LANGUAGES) == null || defaults.boolForKey(ALL_LANGUAGES)
        val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String)?.let { NSLocale(it) }
        val shown = AppLanguages.start(all, preferred?.objectForKey(NSLocaleLanguageCode) as? String ?: "en") ?: return
        val region = preferred?.objectForKey(NSLocaleCountryCode) as? String
        val arguments = defaults.volatileDomainForName(NSArgumentDomain).toMutableMap()
        arguments["AppleLanguages"] = listOf(if (region == null) shown else "$shown-$region")
        defaults.removeVolatileDomainForName(NSArgumentDomain)
        defaults.setVolatileDomain(arguments, NSArgumentDomain)
    }
}
