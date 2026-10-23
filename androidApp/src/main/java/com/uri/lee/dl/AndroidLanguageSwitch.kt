package com.uri.lee.dl

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import com.uri.lee.dl.domain.model.AppLanguages
import com.uri.lee.dl.domain.model.LanguageSwitchStore
import java.util.Locale

/**
 * Remote Config's `all_languages`, saved for the next launch. While it's off, a system (or per-app)
 * language other than English or Vietnamese is replaced by English, region kept, for the whole app:
 * the default locale, which Compose's resources read, and the activity's configuration.
 */
internal class AndroidLanguageSwitch(context: Context) : LanguageSwitchStore {

    private val prefs = context.getSharedPreferences("interface_languages", Context.MODE_PRIVATE)

    override fun save(allLanguages: Boolean) = prefs.edit { putBoolean(ALL_LANGUAGES, allLanguages) }

    /** [base] in the language to show, which is [base] itself unless the switch replaces the system's. */
    fun apply(base: Context): Context {
        val system = base.resources.configuration.locales[0]
        // Read once per run: the launch's fetch saves the next value, which a recreated activity mustn't pick up
        val all = thisRun ?: prefs.getBoolean(ALL_LANGUAGES, true).also { thisRun = it }
        val shown = AppLanguages.start(all, system.language) ?: return base
        val locale = Locale.Builder().setLanguage(shown).setRegion(system.country).build()
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    private companion object {
        const val ALL_LANGUAGES = "all_languages"
        var thisRun: Boolean? = null
    }
}
