package gnosis.keyboard.emoji

import android.content.ClipDescription
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

object EmojiKitchenEngine {

    data class EmojiCombo(
        val first: String,
        val second: String,
        val result: String,
        val description: String
    )

    private val COMBOS = listOf(
        EmojiCombo("🐱", "👓", "🐱‍👓", "Cat with glasses"),
        EmojiCombo("🐱", "💻", "🐱‍💻", "Hacker cat"),
        EmojiCombo("🐱", "🚀", "🐱‍🚀", "Astro cat"),
        EmojiCombo("🐱", "🐉", "🐱‍🐉", "Dino cat"),
        EmojiCombo("🐱", "❤️", "😻", "Loving cat"),
        EmojiCombo("🐱", "😭", "😿", "Crying cat"),
        EmojiCombo("❤️", "🔥", "❤️‍🔥", "Heart on fire"),
        EmojiCombo("❤️", "🩹", "❤️‍🩹", "Mending heart"),
        EmojiCombo("❤️", "💀", "🖤", "Dark heart"),
        EmojiCombo("🔥", "💀", "💀🔥", "Flaming skull"),
        EmojiCombo("😂", "💀", "💀😭", "Dying laughing"),
        EmojiCombo("😭", "✨", "🥺✨", "Sparkly tears"),
        EmojiCombo("😎", "🔥", "😎🔥", "Super cool"),
        EmojiCombo("☕", "❤️", "☕💕", "Coffee love"),
        EmojiCombo("🎉", "🎂", "🥳🎂", "Birthday celebration"),
        EmojiCombo("🍕", "🤤", "🍕😋", "Craving pizza"),
        EmojiCombo("🤖", "💡", "🤖💡", "Smart AI"),
        EmojiCombo("🌙", "✨", "🌙🌟", "Starry night"),
        EmojiCombo("🐶", "👑", "🐶👑", "King pup"),
        EmojiCombo("😴", "💤", "😴💭", "Dreaming"),
        EmojiCombo("💯", "🔥", "💯🔥", "Straight fire"),
        EmojiCombo("⚡", "🚀", "⚡🚀", "Lightning fast"),
        EmojiCombo("👍", "✨", "👍✨", "Approved"),
        EmojiCombo("🙏", "💖", "🙏❤️", "Heartfelt thanks")
    )

    fun getCombinations(emoji: String): List<EmojiCombo> {
        val clean = emoji.trim()
        return COMBOS.filter { it.first == clean || it.second == clean }
    }

    fun combine(emoji1: String, emoji2: String): EmojiCombo? {
        val e1 = emoji1.trim()
        val e2 = emoji2.trim()
        return COMBOS.firstOrNull {
            (it.first == e1 && it.second == e2) || (it.first == e2 && it.second == e1)
        }
    }

    fun supportsRichMedia(editorInfo: EditorInfo?, mimeTypes: Array<String>): Boolean {
        if (editorInfo == null) return false
        val supported = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (supported.isEmpty()) return false
        for (mime in mimeTypes) {
            for (sup in supported) {
                if (ClipDescription.compareMimeTypes(mime, sup)) {
                    return true
                }
            }
        }
        return false
    }

    fun commitRichMedia(
        ic: InputConnection?,
        editorInfo: EditorInfo?,
        contentUri: Uri,
        description: String,
        mimeType: String
    ): Boolean {
        if (ic == null || editorInfo == null) return false
        return try {
            val inputContentInfo = InputContentInfoCompat(
                contentUri,
                ClipDescription(description, arrayOf(mimeType)),
                null
            )
            InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, 0, null)
        } catch (_: Exception) {
            false
        }
    }
}
