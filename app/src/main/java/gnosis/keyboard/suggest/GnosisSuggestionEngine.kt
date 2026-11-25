package gnosis.keyboard.suggest

import android.content.Context
import gnosis.keyboard.SuggestionExtensionManager
import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.emoji.EmojiSuggestionEngine
import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.vector.GnosisNeuralVectorEngine
import java.util.Locale

data class GnosisSuggestionResult(
    val layout: Gnosis3SlotLayout,
    val autoCorrectCandidate: GnosisCandidate?,
    val emojiSuggestions: List<String>,
    val shortcutExpansion: String? = null
) {
    val words: List<Candidate>
        get() = layout.toList().map {
            Candidate(
                word = it.text,
                score = it.score,
                kind = when (it.type) {
                    GnosisCandidateType.AUTO_CORRECT -> CandidateKind.CORRECTION
                    GnosisCandidateType.EXPANSION -> CandidateKind.CORRECTION
                    GnosisCandidateType.P13N -> CandidateKind.USER_LEARNED
                    GnosisCandidateType.PREDICTION -> CandidateKind.PREDICTION
                    else -> CandidateKind.COMPLETION
                }
            )
        }
}

typealias GboardSuggestionResult = GnosisSuggestionResult

class GnosisSuggestionEngine(
    private val typingEngine: TypingEngine,
    private val ngramPredictor: GnosisNgramPredictor,
    private val userDictionary: UserDictionaryRepository,
    private val context: Context? = null
) {

    fun generateSuggestions(
        typedPrefix: String,
        contextWords: List<String>,
        isPasswordField: Boolean = false,
        isUrlOrEmail: Boolean = false,
        language: String = "en"
    ): GnosisSuggestionResult {
        val clean = typedPrefix.trim()

        if (clean.isEmpty()) {
            if (isPasswordField || isUrlOrEmail) {
                return GnosisSuggestionResult(
                    layout = Gnosis3SlotLayout.empty(),
                    autoCorrectCandidate = null,
                    emojiSuggestions = emptyList()
                )
            }

            val predictions = mutableListOf<GnosisCandidate>()

            if (contextWords.isNotEmpty()) {
                val neuralPredictions = GnosisNeuralVectorEngine.predictNextWords(contextWords, language = language, maxResults = 3)
                for ((word, score) in neuralPredictions) {
                    if (word.isNotBlank() && predictions.none { it.text.equals(word, ignoreCase = true) }) {
                        predictions.add(GnosisCandidate(word, GnosisCandidateType.PREDICTION, score, "neural_vector_96d"))
                    }
                }
            }

            if (predictions.size < 3) {
                val basePredictions = ngramPredictor.predictNextWords(contextWords, limit = 3)
                for (bp in basePredictions) {
                    if (bp.text.isNotBlank() && predictions.none { it.text.equals(bp.text, ignoreCase = true) }) {
                        predictions.add(bp)
                    }
                }
            }

            val defaultStarters = when (language.lowercase(Locale.ROOT)) {
                "es" -> listOf("Hola", "¿Cómo", "Gracias", "Qué", "Por", "Yo", "Buenas")
                "fr" -> listOf("Bonjour", "Comment", "Merci", "C'est", "Pourquoi", "Je", "Bonsoir")
                "de" -> listOf("Hallo", "Wie", "Guten", "Danke", "Was", "Ich", "Bis")
                "hi" -> listOf("नमस्ते", "क्या", "आप", "कैसे", "हाँ", "ठीक", "कहाँ")
                "hinglish" -> listOf("Kya", "Kaise", "Haan", "Aap", "Theek", "Kahan", "Kaisa")
                "ru" -> listOf("Привет", "Как", "Спасибо", "Что", "Добрый", "Все", "Пожалуйста")
                "pt" -> listOf("Olá", "Como", "Obrigado", "Bom", "Tudo", "Por", "Boa")
                "it" -> listOf("Ciao", "Come", "Grazie", "Buon", "Per", "Tutto", "Ci")
                else -> listOf("How", "I", "The", "What", "You", "Can", "We")
            }
            for (st in defaultStarters) {
                if (predictions.size >= 3) break
                if (predictions.none { it.text.equals(st, ignoreCase = true) }) {
                    predictions.add(GnosisCandidate(st, GnosisCandidateType.PREDICTION, 0.80, "starter"))
                }
            }

            val p0 = predictions.getOrNull(0) ?: GnosisCandidate("How", GnosisCandidateType.PREDICTION, 0.80)
            val p1 = predictions.getOrNull(1) ?: GnosisCandidate("I", GnosisCandidateType.PREDICTION, 0.90)
            val p2 = predictions.getOrNull(2) ?: GnosisCandidate("The", GnosisCandidateType.PREDICTION, 0.85)

            val layout = Gnosis3SlotLayout(
                slot0Left = p0,
                slot1Center = p1,
                slot2Right = p2,
                emojiCandidate = null
            )
            return GnosisSuggestionResult(
                layout = layout,
                autoCorrectCandidate = null,
                emojiSuggestions = emptyList()
            )
        }

        val shortcut = userDictionary.getExpansion(clean)

        val semanticEmojis = if (context != null) SuggestionExtensionManager.getSemanticEmojis(context, clean) else emptyList()
        val emojis = (semanticEmojis + EmojiSuggestionEngine.getEmojiSuggestions(clean, limit = 1)).distinct().ifEmpty {
            if (contextWords.isNotEmpty()) {
                val phrase = (contextWords.takeLast(2) + clean).joinToString(" ")
                EmojiSuggestionEngine.getEmojiSuggestions(phrase, limit = 1).ifEmpty {
                    EmojiSuggestionEngine.getEmojiSuggestions("${contextWords.last()} $clean", limit = 1)
                }
            } else {
                emptyList()
            }
        }

        val candidates = mutableListOf<GnosisCandidate>()

        if (context != null) {
            val translit = SuggestionExtensionManager.getHinglishTransliteration(context, clean)
            if (translit != null && candidates.none { it.text.equals(translit, ignoreCase = true) }) {
                candidates.add(0, GnosisCandidate(translit, GnosisCandidateType.AUTO_CORRECT, 0.96, "transliteration"))
            }
        }

        if (shortcut != null) {
            val scCandidate = GnosisCandidate(
                text = shortcut,
                type = GnosisCandidateType.EXPANSION,
                score = 0.99,
                source = "shortcut"
            )
            candidates.add(scCandidate)
        }

        val spellingCorrection = GnosisSpellingScorer.findCorrection(clean)
        if (spellingCorrection != null) {
            val (corrText, corrScore) = spellingCorrection
            candidates.add(
                GnosisCandidate(
                    text = corrText,
                    type = GnosisCandidateType.AUTO_CORRECT,
                    score = corrScore,
                    source = "spelling_scorer"
                )
            )
        }

        if (contextWords.isNotEmpty()) {
            val phrase = (contextWords.takeLast(2) + clean).joinToString(" ")
            try {
                val gIssue = gnosis.keyboard.engine.grammar.GnosisGrammarEngine.checkGrammar(phrase, language = language)
                if (gIssue != null) {
                    val repWords = gIssue.correctedText.trim().split(" ")
                    val targetWord = repWords.lastOrNull()
                    if (targetWord != null) {
                        val formattedWord = GnosisSpellingScorer.applyCasing(clean, targetWord)
                        if (candidates.none { it.text.equals(formattedWord, ignoreCase = true) }) {
                            candidates.add(0, GnosisCandidate(formattedWord, GnosisCandidateType.AUTO_CORRECT, 0.99, "grammar_engine"))
                        }
                    }
                    if (repWords.size > 1 && candidates.none { it.text.equals(gIssue.correctedText.trim(), ignoreCase = true) }) {
                        candidates.add(0, GnosisCandidate(gIssue.correctedText.trim(), GnosisCandidateType.AUTO_CORRECT, 0.99, "grammar_engine_phrase"))
                    }
                }
                val issues = typingEngine.checkGrammar(phrase)
                for (issue in issues) {
                    for (rep in issue.suggestions) {
                        val repWords = rep.split(" ")
                        val targetWord = repWords.lastOrNull() ?: continue
                        val formattedWord = GnosisSpellingScorer.applyCasing(clean, targetWord)
                        if (candidates.none { it.text.equals(formattedWord, ignoreCase = true) }) {
                            candidates.add(
                                0,
                                GnosisCandidate(
                                    text = formattedWord,
                                    type = GnosisCandidateType.AUTO_CORRECT,
                                    score = 0.98,
                                    source = "grammar"
                                )
                            )
                        }
                        if (repWords.size > 1 && candidates.none { it.text.equals(rep, ignoreCase = true) }) {
                            candidates.add(
                                0,
                                GnosisCandidate(
                                    text = rep,
                                    type = GnosisCandidateType.AUTO_CORRECT,
                                    score = 0.99,
                                    source = "grammar_phrase"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val typingResult = typingEngine.onInputUpdate(clean, contextWords)
        for (c in typingResult.suggestions) {
            if (candidates.none { it.text.equals(c.word, ignoreCase = true) }) {
                val candType = when {
                    c.kind == CandidateKind.CORRECTION -> GnosisCandidateType.AUTO_CORRECT
                    c.kind == CandidateKind.CONTRACTION -> GnosisCandidateType.AUTO_CORRECT
                    c.kind == CandidateKind.USER_LEARNED -> GnosisCandidateType.P13N
                    c.kind == CandidateKind.PREDICTION -> GnosisCandidateType.PREDICTION
                    else -> GnosisCandidateType.RAW
                }
                candidates.add(
                    GnosisCandidate(
                        text = GnosisSpellingScorer.applyCasing(clean, c.word),
                        type = candType,
                        score = c.score,
                        source = "typing_engine"
                    )
                )
            }
        }

        typingResult.autoCorrect?.let { ac ->
            val formatted = GnosisSpellingScorer.applyCasing(clean, ac.word)
            val existingIdx = candidates.indexOfFirst { it.text.equals(formatted, ignoreCase = true) }
            val autoCandidate = GnosisCandidate(
                text = formatted,
                type = GnosisCandidateType.AUTO_CORRECT,
                score = ac.score.coerceAtLeast(0.95),
                source = "gnosis_autocorrect"
            )
            if (existingIdx >= 0) {
                candidates.removeAt(existingIdx)
            }
            candidates.add(0, autoCandidate)
        }

        for (w in userDictionary.getAllWords()) {
            if (w.startsWith(clean.lowercase(Locale.ROOT))) {
                if (candidates.none { it.text.equals(w, ignoreCase = true) }) {
                    val formatted = GnosisSpellingScorer.applyCasing(clean, w)
                    candidates.add(
                        0,
                        GnosisCandidate(
                            text = formatted,
                            type = GnosisCandidateType.P13N,
                            score = 0.95,
                            source = "user_dict"
                        )
                    )
                }
            }
        }

        val rankedCandidates = if (contextWords.isNotEmpty()) {
            GnosisNeuralVectorEngine.rerankCandidates(contextWords, candidates)
        } else {
            candidates
        }

        val centerCandidate = rankedCandidates.firstOrNull { it.isAutoCorrect }
            ?: candidates.firstOrNull()
            ?: GnosisCandidate(clean, GnosisCandidateType.RAW, 0.50)

        val others = rankedCandidates.filter { !it.text.equals(centerCandidate.text, ignoreCase = true) }

        val leftCandidate = if (!clean.equals(centerCandidate.text, ignoreCase = true)) {
            GnosisCandidate(clean, GnosisCandidateType.RAW, 0.50, rawTyped = clean)
        } else {
            others.firstOrNull()
        }

        val rightCandidate = others.firstOrNull { it.text != leftCandidate?.text && it.text != centerCandidate.text }

        val emojiCand = emojis.firstOrNull()?.let {
            GnosisCandidate(it, GnosisCandidateType.EMOJI, 0.90, "emoji")
        }

        val layout = Gnosis3SlotLayout(
            slot0Left = leftCandidate,
            slot1Center = centerCandidate,
            slot2Right = rightCandidate,
            emojiCandidate = emojiCand
        )

        val autoCorrect = if (centerCandidate.isAutoCorrect || centerCandidate.score >= 0.70) centerCandidate else null

        return GnosisSuggestionResult(
            layout = layout,
            autoCorrectCandidate = autoCorrect,
            emojiSuggestions = emojis,
            shortcutExpansion = shortcut
        )
    }
}

typealias GboardSuggestionEngine = GnosisSuggestionEngine
