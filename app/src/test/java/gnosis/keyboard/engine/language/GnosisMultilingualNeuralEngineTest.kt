package gnosis.keyboard.engine.language

import gnosis.keyboard.engine.grammar.GnosisGrammarEngine
import gnosis.keyboard.engine.vector.GnosisNeuralVectorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnosisMultilingualNeuralEngineTest {

    @Test
    fun testLanguageManagerCatalog() {
        val es = GnosisLanguageManager.getLanguageByCode("es")
        assertEquals("Spanish", es.name)
        assertEquals("ES", es.shortLabel)
        assertEquals("spanish", es.defaultLayout)

        val fr = GnosisLanguageManager.getLanguageByCode("fr")
        assertEquals("French", fr.name)
        assertEquals("FR", fr.shortLabel)
        assertEquals("azerty", fr.defaultLayout)

        val de = GnosisLanguageManager.getLanguageByCode("de")
        assertEquals("German", de.name)
        assertEquals("DE", de.shortLabel)
        assertEquals("qwertz", de.defaultLayout)

        val hi = GnosisLanguageManager.getLanguageByCode("hi")
        assertEquals("Hindi", hi.name)
        assertEquals("HI", hi.shortLabel)

        val ru = GnosisLanguageManager.getLanguageByCode("ru")
        assertEquals("Russian", ru.name)
        assertEquals("RU", ru.shortLabel)
    }

    @Test
    fun testLayoutToLanguageMapping() {
        assertEquals("es", GnosisLanguageManager.getLanguageForLayout("spanish").code)
        assertEquals("fr", GnosisLanguageManager.getLanguageForLayout("azerty").code)
        assertEquals("de", GnosisLanguageManager.getLanguageForLayout("qwertz").code)
        assertEquals("hi", GnosisLanguageManager.getLanguageForLayout("deva").code)
        assertEquals("ru", GnosisLanguageManager.getLanguageForLayout("russian").code)
        assertEquals("pt", GnosisLanguageManager.getLanguageForLayout("portuguese").code)
    }

    @Test
    fun testMultilingualNeuralVectorProjection() {
        val vecEn = GnosisNeuralVectorEngine.getVector("restaurant")
        assertEquals(GnosisNeuralVectorEngine.VECTOR_DIM, vecEn.size)

        val vecEs = GnosisNeuralVectorEngine.getVector("comida")
        assertEquals(GnosisNeuralVectorEngine.VECTOR_DIM, vecEs.size)

        val vecDeva = GnosisNeuralVectorEngine.getVector("नमस्ते")
        assertEquals(GnosisNeuralVectorEngine.VECTOR_DIM, vecDeva.size)

        val vecRu = GnosisNeuralVectorEngine.getVector("привет")
        assertEquals(GnosisNeuralVectorEngine.VECTOR_DIM, vecRu.size)

        val simSelf = GnosisNeuralVectorEngine.cosineSimilarity(vecEs, vecEs)
        assertTrue(simSelf > 0.99f)
    }

    @Test
    fun testLanguageAwareNextWordPredictions() {
        val esPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("cómo"), language = "es")
        assertTrue(esPreds.any { it.first.equals("estás", ignoreCase = true) })

        val graciasPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("muchas", "gracias"), language = "es")
        assertTrue(graciasPreds.any { it.first.equals("por", ignoreCase = true) })

        val frPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("comment"), language = "fr")
        assertTrue(frPreds.any { it.first.equals("vas-tu", ignoreCase = true) || it.first.equals("ça", ignoreCase = true) })

        val merciPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("merci"), language = "fr")
        assertTrue(merciPreds.any { it.first.equals("beaucoup", ignoreCase = true) })

        val dePreds = GnosisNeuralVectorEngine.predictNextWords(listOf("wie"), language = "de")
        assertTrue(dePreds.any { it.first.equals("geht's", ignoreCase = true) })

        val dankPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("vielen"), language = "de")
        assertTrue(dankPreds.any { it.first.equals("Dank", ignoreCase = true) })

        val hiPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("kya"), language = "hinglish")
        assertTrue(hiPreds.any { it.first.equals("kar", ignoreCase = true) || it.first.equals("hua", ignoreCase = true) })

        val kaisePreds = GnosisNeuralVectorEngine.predictNextWords(listOf("kaise"), language = "hinglish")
        assertTrue(kaisePreds.any { it.first.equals("ho", ignoreCase = true) })

        val ruPreds = GnosisNeuralVectorEngine.predictNextWords(listOf("как"), language = "ru")
        assertTrue(ruPreds.any { it.first.equals("дела", ignoreCase = true) })
    }

    @Test
    fun testSpanishGrammarRules() {
        val deEl = GnosisGrammarEngine.checkGrammar("vamos de el parque", language = "es")
        assertNotNull(deEl)
        assertEquals("del", deEl?.correctedText)

        val aEl = GnosisGrammarEngine.checkGrammar("voy a el cine", language = "es")
        assertNotNull(aEl)
        assertEquals("al", aEl?.correctedText)

        val haiga = GnosisGrammarEngine.checkGrammar("espero que haiga comida", language = "es")
        assertNotNull(haiga)
        assertEquals("haya", haiga?.correctedText)

        val problema = GnosisGrammarEngine.checkGrammar("tengo la problema", language = "es")
        assertNotNull(problema)
        assertEquals("el problema", problema?.correctedText)
    }

    @Test
    fun testFrenchGrammarRules() {
        val cEst = GnosisGrammarEngine.checkGrammar("oui c est vrai", language = "fr")
        assertNotNull(cEst)
        assertEquals("c'est", cEst?.correctedText)

        val jAi = GnosisGrammarEngine.checkGrammar("j ai faim", language = "fr")
        assertNotNull(jAi)
        assertEquals("j'ai", jAi?.correctedText)

        val dAccord = GnosisGrammarEngine.checkGrammar("d accord avec toi", language = "fr")
        assertNotNull(dAccord)
        assertEquals("d'accord", dAccord?.correctedText)
    }

    @Test
    fun testGermanGrammarRules() {
        val seitIhr = GnosisGrammarEngine.checkGrammar("wo seit ihr", language = "de")
        assertNotNull(seitIhr)
        assertEquals("seid ihr", seitIhr?.correctedText)
    }

    @Test
    fun testHindiHinglishGrammarRules() {
        val mainHoon = GnosisGrammarEngine.checkGrammar("main karta hai", language = "hinglish")
        assertNotNull(mainHoon)
        assertEquals("main karta hoon", mainHoon?.correctedText)

        val aapKaiseHain = GnosisGrammarEngine.checkGrammar("aap kaise ho", language = "hinglish")
        assertNotNull(aapKaiseHain)
        assertEquals("aap kaise hain", aapKaiseHain?.correctedText)
    }

    @Test
    fun testUniversalPunctuationRule() {
        val spacePunct = GnosisGrammarEngine.checkGrammar("hello , world", language = "en")
        assertNotNull(spacePunct)
        assertEquals("hello,", spacePunct?.correctedText)
    }
}
