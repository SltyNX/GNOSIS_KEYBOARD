package gnosis.keyboard.engine.grammar

import gnosis.keyboard.engine.GrammarIssue

class HarperBridge : GrammarChecker {

    private val fallbackEngine = OfflineGrammarEngine()
    private var nativeAvailable = false

    init {
        try {
            System.loadLibrary("harper_jni")
            nativeAvailable = true
        } catch (_: UnsatisfiedLinkError) {
            nativeAvailable = false
        } catch (_: Exception) {
            nativeAvailable = false
        }
    }

    override fun check(text: String): List<GrammarIssue> {
        if (nativeAvailable) {
            try {
                return nativeCheck(text)
            } catch (_: Exception) {
            }
        }
        return fallbackEngine.check(text)
    }

    private external fun nativeCheck(text: String): List<GrammarIssue>
}
