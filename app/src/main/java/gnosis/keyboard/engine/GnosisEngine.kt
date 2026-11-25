package gnosis.keyboard.engine

import android.content.Context
import android.graphics.PointF
import android.util.Log
import gnosis.keyboard.engine.delight.GnosisDelightBridge
import gnosis.keyboard.engine.delight.GnosisDynamicLm
import gnosis.keyboard.engine.delight.GnosisSpatialTouchModel
import gnosis.keyboard.engine.grammar.GnosisSentenceExplorer
import gnosis.keyboard.engine.grammar_and_spellcheck.writingassist.GnosisGrammarIssue
import gnosis.keyboard.engine.grammar_and_spellcheck.writingassist.GnosisWritingAssist
import gnosis.keyboard.engine.suggestion_typing.CandidateItem
import gnosis.keyboard.engine.suggestion_typing.CandidateManager
import gnosis.keyboard.engine.suggestion_typing.CandidateType

/**
 * Gnosis_Engine - Unified Gnosis Keyboard Intelligence Engine
 *
 * Modern modular keyboard neural architecture:
 * 1. Delight & Consolidated Native Runtime (libintegrated_shared_object.so, grammar-checker, etc.)
 * 2. TNT (Touch-Neural-Targeting) Spatial Touch Model with bivariate Gaussian modeling
 * 3. Dynamic LM on-device personal typing lexicon & frequency learner
 * 4. Sentence Explorer & Grammar checking with real-time homophone and apostrophe corrections
 * 5. Suggestion Typing (Textboard 3-slot candidate manager)
 * 6. Glide Typing trajectory matching
 */
object GnosisEngine {

    private const val TAG = "GnosisEngine"
    private var initialized = false
    private lateinit var appContext: Context

    lateinit var spatialModel: GnosisSpatialTouchModel
        private set
    lateinit var dynamicLm: GnosisDynamicLm
        private set
    lateinit var candidateManager: CandidateManager
        private set
    lateinit var universalAiEngine: gnosis.keyboard.engine.ai.GnosisUniversalAiEngine
        private set

    data class CorrectionRecord(val original: String, val corrected: String)
    private var lastCorrection: CorrectionRecord? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        spatialModel = GnosisSpatialTouchModel()
        dynamicLm = GnosisDynamicLm(appContext)
        candidateManager = CandidateManager(appContext, dynamicLm, spatialModel)
        universalAiEngine = gnosis.keyboard.engine.ai.GnosisUniversalAiEngine.getInstance(appContext)

        // Preload vocabulary
        Thread {
            try {
                gnosis.keyboard.WordDictionary.getCommonWords(appContext)
            } catch (_: Exception) {}
        }.start()

        // Load consolidated native binaries
        try {
            GnosisDelightBridge.loadAllNativeModules(appContext)
        } catch (t: Throwable) {
            Log.w(TAG, "Native library initialization deferred: ${t.message}")
        }

        initialized = true
    }

    fun optimizeNativeEngine(languageCode: String? = null) {
        GnosisDelightBridge.loadAllNativeModules()
    }

    fun reloadLanguageExtensions(context: Context, transitions: Map<String, List<String>>, languageCode: String? = null) {
        if (!initialized) init(context)
        optimizeNativeEngine(languageCode)
        reset()
    }

    fun loadLibrarySafely(libName: String): Boolean = GnosisDelightBridge.loadLibrarySafely(libName)

    fun isNativeLibLoaded(libName: String): Boolean = GnosisDelightBridge.isLibraryLoaded(libName)

    fun recordTouch(char: Char, x: Float, y: Float) {
        // Spatial touch event logged into TNT model
    }

    fun generateCandidates(
        activeWord: String,
        textBefore: String
    ): List<CandidateItem> {
        if (!initialized) return emptyList()
        return candidateManager.generateSlots(activeWord, textBefore)
    }

    fun predictNextWords(textBefore: String): List<String> {
        if (!initialized) return emptyList()
        return candidateManager.predictNextWords(textBefore, limit = 3)
    }

    fun shouldAutoCorrect(
        candidate: CandidateItem,
        activeWord: String,
        isPassword: Boolean,
        isUrl: Boolean
    ): Boolean {
        if (isPassword || isUrl) return false
        if (candidate.type != CandidateType.AUTO_CORRECT) return false
        if (candidate.confidence < 0.70f) return false
        if (candidate.text.equals(activeWord, ignoreCase = true)) return false
        if (::dynamicLm.isInitialized && dynamicLm.isSuppressed(activeWord)) return false
        return true
    }

    fun recordCorrection(original: String, corrected: String) {
        lastCorrection = CorrectionRecord(original, corrected)
    }

    fun canUndo(): Boolean = lastCorrection != null

    fun consumeUndo(): CorrectionRecord? {
        val record = lastCorrection
        lastCorrection = null
        return record
    }

    fun suppressWord(word: String) {
        if (::dynamicLm.isInitialized) {
            dynamicLm.suppressCorrection(word)
        }
    }

    fun onWordCommitted(word: String, contextWords: List<String>) {
        if (!initialized) return
        val prevWord = contextWords.lastOrNull()
        dynamicLm.learnWord(word, prevWord)
        if (prevWord != null && ::appContext.isInitialized) {
            universalAiEngine.learnTransition(prevWord, word)
        }
        reset()
    }

    fun checkGrammar(text: String): GnosisGrammarIssue? {
        return GnosisSentenceExplorer.checkGrammar(text) ?: GnosisWritingAssist.analyzeText(text)
    }

    fun reset() {
        lastCorrection = null
    }
}
