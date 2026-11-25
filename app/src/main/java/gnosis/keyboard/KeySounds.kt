package gnosis.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class KeySounds(private val ctx: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var defaultId = 0
    private val pianoIds = IntArray(PIANO_NOTES)
    private var drumId = 0
    private var mechanicalId = 0
    private var typewriterId = 0
    private var bubbleId = 0

    init {
        defaultId = load("keypress")
        for (i in 0 until PIANO_NOTES) pianoIds[i] = load("piano_%02d".format(i))
        drumId = load("drum_tom_hi")
        mechanicalId = load("key_mechanical")
        typewriterId = load("key_typewriter")
        bubbleId = load("key_bubble")
    }

    private fun load(name: String): Int = try {
        val res = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        if (res != 0) pool.load(ctx, res, 1) else 0
    } catch (_: Exception) { 0 }

    fun play(tone: String, key: CharSequence?, volume: Float) {
        when (tone) {
            "multi", "piano" -> playPiano(key?.toString(), volume)
            "drum", "drums" -> if (drumId != 0) pool.play(drumId, volume, volume, 0, 0, 1f)
            "mechanical" -> if (mechanicalId != 0) pool.play(mechanicalId, volume, volume, 0, 0, 1f) else if (defaultId != 0) pool.play(defaultId, volume, volume, 0, 0, 1f)
            "typewriter" -> if (typewriterId != 0) pool.play(typewriterId, volume, volume, 0, 0, 1f) else if (defaultId != 0) pool.play(defaultId, volume, volume, 0, 0, 1f)
            "bubble" -> if (bubbleId != 0) pool.play(bubbleId, volume, volume, 0, 0, 1f) else if (defaultId != 0) pool.play(defaultId, volume, volume, 0, 0, 1f)
            else -> if (defaultId != 0) pool.play(defaultId, volume, volume, 0, 0, 1f)
        }
    }

    private fun playPiano(key: String?, volume: Float) {
        if (key == "SPACE") {
            pool.play(pianoIds[0], volume, volume, 0, 0, 0.5f)
            return
        }
        val c: Char? = key?.takeIf { it.length == 1 }?.get(0)?.lowercaseChar()
        val idx = when {
            c == null -> 4
            c in 'a'..'z' -> QWERTY.indexOf(c) * (PIANO_NOTES - 1) / (QWERTY.length - 1)
            c in '0'..'9' -> (if (c == '0') 9 else c - '1') * (PIANO_NOTES - 1) / 9
            else -> 7
        }
        pool.play(pianoIds[idx], volume, volume, 0, 0, 1f)
    }

    fun release() = pool.release()

    companion object {
        private const val PIANO_NOTES = 15
        private const val QWERTY = "qwertyuiopasdfghjklzxcvbnm"
        val TONES = listOf(
            "default" to "Default Click",
            "multi" to "Piano Chords",
            "drum" to "Acoustic Drums",
            "mechanical" to "Mechanical Switch",
            "typewriter" to "Vintage Typewriter",
            "bubble" to "Bubble Pop"
        )
    }
}
