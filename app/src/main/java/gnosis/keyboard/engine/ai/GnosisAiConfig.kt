package gnosis.keyboard.engine.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Gnosis AI Intelligence Configuration
 *
 * Persists and manages configuration toggles for the universal on-device
 * text intelligence and neural prediction pipeline.
 */
object GnosisAiConfig {

    const val PREF_AI_ENABLED = "ai_intelligence_enabled"
    const val PREF_AI_CONTEXTUAL_PREDICTIONS = "ai_contextual_predictions"
    const val PREF_AI_SMART_AUTOCOMPLETE = "ai_smart_autocomplete"
    const val PREF_AI_SPELL_CORRECTION = "ai_spell_correction"
    const val PREF_AI_PROCESSING_MODE = "ai_processing_mode"
    const val PREF_AI_PERFORMANCE_PROFILE = "ai_performance_profile"

    const val MODE_LOCAL_ON_DEVICE = "local_on_device"
    const val MODE_CLOUD = "cloud"

    const val PROFILE_BALANCED = "balanced"
    const val PROFILE_LIGHTWEIGHT = "lightweight"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("gnosis_ai_config", Context.MODE_PRIVATE)
    }

    fun isAiEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_AI_ENABLED, true)
    fun setAiEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AI_ENABLED, enabled).apply()
    }

    fun isContextualPredictionsEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_AI_CONTEXTUAL_PREDICTIONS, true)
    fun setContextualPredictionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AI_CONTEXTUAL_PREDICTIONS, enabled).apply()
    }

    fun isSmartAutocompleteEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_AI_SMART_AUTOCOMPLETE, true)
    fun setSmartAutocompleteEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AI_SMART_AUTOCOMPLETE, enabled).apply()
    }

    fun isAiSpellCorrectionEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_AI_SPELL_CORRECTION, true)
    fun setAiSpellCorrectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AI_SPELL_CORRECTION, enabled).apply()
    }

    fun getProcessingMode(context: Context): String = prefs(context).getString(PREF_AI_PROCESSING_MODE, MODE_LOCAL_ON_DEVICE) ?: MODE_LOCAL_ON_DEVICE
    fun setProcessingMode(context: Context, mode: String) {
        prefs(context).edit().putString(PREF_AI_PROCESSING_MODE, mode).apply()
    }

    fun getPerformanceProfile(context: Context): String = prefs(context).getString(PREF_AI_PERFORMANCE_PROFILE, PROFILE_BALANCED) ?: PROFILE_BALANCED
    fun setPerformanceProfile(context: Context, profile: String) {
        prefs(context).edit().putString(PREF_AI_PERFORMANCE_PROFILE, profile).apply()
    }

    fun clearLearnedData(context: Context) {
        GnosisUniversalAiEngine.getInstance(context).clearLearnedData()
    }
}
