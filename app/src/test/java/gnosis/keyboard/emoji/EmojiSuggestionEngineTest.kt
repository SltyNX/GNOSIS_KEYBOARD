package gnosis.keyboard.emoji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSuggestionEngineTest {

    @Test
    fun testEnglishKeywords() {
        val happy = EmojiSuggestionEngine.getEmojiSuggestions("happy")
        assertTrue("happy should suggest smiling emojis", happy.any { it in listOf("😊", "😀", "😃", "😁") })

        val fire = EmojiSuggestionEngine.getEmojiSuggestions("fire")
        assertTrue("fire should suggest flame", fire.contains("🔥"))

        val coffee = EmojiSuggestionEngine.getEmojiSuggestions("coffee")
        assertTrue("coffee should suggest coffee cup", fire.isNotEmpty() && coffee.contains("☕"))

        val love = EmojiSuggestionEngine.getEmojiSuggestions("love")
        assertTrue("love should suggest heart", love.contains("❤️"))

        val cry = EmojiSuggestionEngine.getEmojiSuggestions("cry")
        assertTrue("cry should suggest crying face", cry.contains("😭") || cry.contains("😢"))
    }

    @Test
    fun testHinglishKeywords() {
        val pyaar = EmojiSuggestionEngine.getEmojiSuggestions("pyaar")
        assertTrue("pyaar should suggest heart", pyaar.contains("❤️"))

        val chai = EmojiSuggestionEngine.getEmojiSuggestions("chai")
        assertTrue("chai should suggest tea", chai.contains("☕"))

        val khana = EmojiSuggestionEngine.getEmojiSuggestions("khana")
        assertTrue("khana should suggest food", khana.contains("🍲") || khana.contains("🍕"))

        val pagal = EmojiSuggestionEngine.getEmojiSuggestions("pagal")
        assertTrue("pagal should suggest crazy/zany face", pagal.contains("🤪"))

        val namaste = EmojiSuggestionEngine.getEmojiSuggestions("namaste")
        assertTrue("namaste should suggest folded hands", namaste.contains("🙏"))

        val shabash = EmojiSuggestionEngine.getEmojiSuggestions("shabash")
        assertTrue("shabash should suggest clapping hands", shabash.contains("👏"))
    }

    @Test
    fun testDynamicStemming() {
        val dogs = EmojiSuggestionEngine.getEmojiSuggestions("dogs")
        assertTrue("dogs should resolve to dog and suggest dog emojis", dogs.contains("🐕") || dogs.contains("🐶"))

        val kisses = EmojiSuggestionEngine.getEmojiSuggestions("kisses")
        assertTrue("kisses should resolve to kiss and suggest kiss emojis", kisses.contains("😘") || kisses.contains("💋"))

        val dancing = EmojiSuggestionEngine.getEmojiSuggestions("dancing")
        assertTrue("dancing should resolve to dance and suggest dancer", dancing.contains("💃") || dancing.contains("🕺"))

        val running = EmojiSuggestionEngine.getEmojiSuggestions("running")
        assertTrue("running should resolve to run and suggest runner", running.contains("🏃"))

        val celebrated = EmojiSuggestionEngine.getEmojiSuggestions("celebrated")
        assertTrue("celebrated should resolve to celebrate and suggest party", celebrated.contains("🎉") || celebrated.contains("🥳"))
    }

    @Test
    fun testCaseAndWhitespaceHandling() {
        val uppercaseLove = EmojiSuggestionEngine.getEmojiSuggestions("  LOVE  ")
        assertTrue("Uppercase and padded words should match correctly", uppercaseLove.contains("❤️"))

        val mixedAwesome = EmojiSuggestionEngine.getEmojiSuggestions("Awesome")
        assertTrue("Mixed case should match correctly", mixedAwesome.contains("🤩") || mixedAwesome.contains("😎"))

        val empty = EmojiSuggestionEngine.getEmojiSuggestions("")
        assertTrue("Empty input should return empty list", empty.isEmpty())
    }
}
