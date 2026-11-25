package gnosis.keyboard.engine.language

import android.content.Context
import gnosis.keyboard.LayoutExtensionManager
import gnosis.keyboard.Skins
import java.util.Locale

data class LanguageDefinition(
    val code: String,
    val name: String,
    val nativeName: String,
    val defaultLayout: String,
    val shortLabel: String,
    val spaceLabel: String
)

object GnosisLanguageManager {

    val SUPPORTED_LANGUAGES = listOf(
        LanguageDefinition("en", "English", "English", "qwerty", "EN", "English"),
        LanguageDefinition("es", "Spanish", "Español", "spanish", "ES", "Español"),
        LanguageDefinition("fr", "French", "Français", "azerty", "FR", "Français"),
        LanguageDefinition("de", "German", "Deutsch", "qwertz", "DE", "Deutsch"),
        LanguageDefinition("hi", "Hindi", "हिन्दी", "deva", "HI", "हिन्दी"),
        LanguageDefinition("hinglish", "Hinglish", "Hinglish", "qwerty", "HI-EN", "Hinglish"),
        LanguageDefinition("ru", "Russian", "Русский", "russian", "RU", "Русский"),
        LanguageDefinition("pt", "Portuguese", "Português", "portuguese", "PT", "Português"),
        LanguageDefinition("it", "Italian", "Italiano", "qwerty", "IT", "Italiano"),
        LanguageDefinition("ar", "Arabic", "العربية", "arabic", "AR", "العربية"),
        LanguageDefinition("tr", "Turkish", "Türkçe", "turkish", "TR", "Türkçe")
    )

    private val DEFAULT_LANGUAGE = SUPPORTED_LANGUAGES[0]

    fun getLanguageByCode(code: String): LanguageDefinition {
        val clean = code.trim().lowercase(Locale.ROOT)
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(clean, ignoreCase = true) }
            ?: DEFAULT_LANGUAGE
    }

    fun getLanguageForLayout(layoutId: String): LanguageDefinition {
        val clean = layoutId.trim().lowercase(Locale.ROOT)
        return when (clean) {
            "spanish" -> getLanguageByCode("es")
            "azerty" -> getLanguageByCode("fr")
            "qwertz" -> getLanguageByCode("de")
            "deva" -> getLanguageByCode("hi")
            "russian" -> getLanguageByCode("ru")
            "portuguese" -> getLanguageByCode("pt")
            "arabic" -> getLanguageByCode("ar")
            "turkish" -> getLanguageByCode("tr")
            else -> DEFAULT_LANGUAGE
        }
    }

    fun resolveEffectiveLanguage(context: Context): LanguageDefinition {
        val prefs = Skins.prefs(context)
        val mode = prefs.getString(Skins.KEY_LANGUAGE_MODE, Skins.MODE_LANG_SYSTEM) ?: Skins.MODE_LANG_SYSTEM

        if (mode == Skins.MODE_LANG_SYSTEM) {
            val systemLocale = Locale.getDefault()
            val sysLang = systemLocale.language.lowercase(Locale.ROOT)
            val sysCountry = systemLocale.country.uppercase(Locale.ROOT)

            val matched = when (sysLang) {
                "es" -> getLanguageByCode("es")
                "fr" -> getLanguageByCode("fr")
                "de" -> getLanguageByCode("de")
                "hi" -> getLanguageByCode("hi")
                "ru" -> getLanguageByCode("ru")
                "pt" -> getLanguageByCode("pt")
                "it" -> getLanguageByCode("it")
                "ar" -> getLanguageByCode("ar")
                "tr" -> getLanguageByCode("tr")
                "en" -> getLanguageByCode("en")
                else -> null
            }

            if (matched != null) return matched

            val curLayout = prefs.getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
            if (curLayout != "qwerty") {
                return getLanguageForLayout(curLayout)
            }
            return DEFAULT_LANGUAGE
        }

        val manualCode = prefs.getString(Skins.KEY_ACTIVE_LANGUAGE, "en") ?: "en"
        return getLanguageByCode(manualCode)
    }

    fun isSystemLanguageMode(context: Context): Boolean {
        val prefs = Skins.prefs(context)
        return prefs.getString(Skins.KEY_LANGUAGE_MODE, Skins.MODE_LANG_SYSTEM) == Skins.MODE_LANG_SYSTEM
    }

    fun setLanguageMode(context: Context, mode: String) {
        val prefs = Skins.prefs(context)
        prefs.edit().putString(Skins.KEY_LANGUAGE_MODE, mode).apply()
    }

    fun setActiveLanguage(context: Context, languageCode: String) {
        val prefs = Skins.prefs(context)
        val lang = getLanguageByCode(languageCode)
        prefs.edit()
            .putString(Skins.KEY_LANGUAGE_MODE, Skins.MODE_LANG_MANUAL)
            .putString(Skins.KEY_ACTIVE_LANGUAGE, lang.code)
            .apply()
    }

    fun cycleLanguage(context: Context): LanguageDefinition {
        val current = resolveEffectiveLanguage(context)
        val idx = SUPPORTED_LANGUAGES.indexOfFirst { it.code == current.code }
        val nextIdx = if (idx >= 0) (idx + 1) % SUPPORTED_LANGUAGES.size else 0
        val nextLang = SUPPORTED_LANGUAGES[nextIdx]

        val prefs = Skins.prefs(context)
        prefs.edit()
            .putString(Skins.KEY_LANGUAGE_MODE, Skins.MODE_LANG_MANUAL)
            .putString(Skins.KEY_ACTIVE_LANGUAGE, nextLang.code)
            .apply()

        val installedLayouts = LayoutExtensionManager.getInstalledLayouts(context).map { it.id }
        if (installedLayouts.contains(nextLang.defaultLayout) || LayoutExtensionManager.BUILTIN_LAYOUTS.containsKey(nextLang.defaultLayout)) {
            prefs.edit().putString(Skins.KEY_LAYOUT, nextLang.defaultLayout).apply()
        }

        return nextLang
    }
}
