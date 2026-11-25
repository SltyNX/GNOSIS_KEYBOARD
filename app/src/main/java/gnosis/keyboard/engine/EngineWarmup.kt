package gnosis.keyboard.engine

import android.content.Context
import android.os.Process
import android.util.Log
import gnosis.keyboard.EmojiSkinTones
import gnosis.keyboard.KaomojiData
import gnosis.keyboard.WordDictionary
import gnosis.keyboard.emoji.EmojiSuggestionEngine
import gnosis.keyboard.engine.ai.GnosisUniversalAiEngine
import gnosis.keyboard.engine.contractions.GnosisContractionEngine
import gnosis.keyboard.engine.delight.GnosisDelightBridge
import gnosis.keyboard.engine.grammar.GnosisGrammarDfaEngine
import gnosis.keyboard.engine.homophones.GnosisHomophoneEngine
import gnosis.keyboard.engine.prediction.GnosisBigramPredictionEngine
import gnosis.keyboard.engine.security.MessageArmourEngine
import gnosis.keyboard.engine.suggestion_typing.GnosisRadixFstEngine

object EngineWarmup {
    private const val TAG = "EngineWarmup"

    @Volatile
    var isWarmed: Boolean = false
        private set

    fun fireUpAllEngines(context: Context) {
        if (isWarmed) return
        val appContext = context.applicationContext

        val warmupThread = Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                val t0 = System.currentTimeMillis()

                // 1. Zero-copy Memory-mapped lexicon & gesture vocabulary
                WordDictionary.init(appContext)
                val words = WordDictionary.getCommonWords(appContext)
                WordDictionary.setGestureVocabulary(words.toList())

                // 2. Core candidate & AI intelligence engines
                GnosisEngine.init(appContext)
                GnosisUniversalAiEngine.getInstance(appContext)

                // 3. Statistical N-Gram & Bigram prediction engines
                GnosisBigramPredictionEngine.predictNext("")

                // 4. Radix FST Engine
                GnosisRadixFstEngine.instance.build(words)

                // 5. Offline Emoji engines & cached skin-tones
                EmojiSuggestionEngine.init(appContext)
                EmojiSkinTones.init(appContext)
                KaomojiData.prewarm(appContext)

                // 6. Security, contractions, grammar DFA & homophones
                MessageArmourEngine.scan("")
                GnosisContractionEngine.hasContraction("dont")
                GnosisGrammarDfaEngine.evaluate("")
                GnosisHomophoneEngine.isHomophone("there")

                // 7. Native Delight C++ libraries
                GnosisDelightBridge.loadAllNativeModules(appContext)

                isWarmed = true
                val elapsed = System.currentTimeMillis() - t0
                Log.d(TAG, "All Gnosis engines fired up instantly in ${elapsed}ms")
            } catch (t: Throwable) {
                Log.w(TAG, "Engine warmup encountered warning: ${t.message}", t)
            }
        }, "GnosisEngineWarmupWorker")

        warmupThread.priority = Thread.MAX_PRIORITY
        warmupThread.start()
    }
}
