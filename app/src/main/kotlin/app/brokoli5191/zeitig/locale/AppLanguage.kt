package app.brokoli5191.zeitig.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** In-app language choice: system, German (Austrian formatting) or English. */
enum class AppLanguage(
    val tag: String?,
) {
    SYSTEM(null),
    GERMAN("de-AT"),
    ENGLISH("en"),
}

/**
 * Per-app language. Android 13+ uses [LocaleManager] (shows up in the system's app language
 * settings via `locales_config.xml`). Android 9–12 falls back to a stored tag applied in
 * `attachBaseContext` of the activity – notifications and widgets there follow the system language.
 */
object AppLanguages {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage {
        val tag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            }
        return when {
            tag == null -> AppLanguage.SYSTEM
            tag.startsWith("de") -> AppLanguage.GERMAN
            tag.startsWith("en") -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    /** Returns true when the caller must recreate the activity (pre-Android 13 only). */
    fun set(
        context: Context,
        language: AppLanguage,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            return false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, language.tag) }
        return true
    }

    /** Pre-Android 13: wraps the base context with the chosen locale. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
