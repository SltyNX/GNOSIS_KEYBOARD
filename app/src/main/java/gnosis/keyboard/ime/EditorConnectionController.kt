package gnosis.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

class EditorConnectionController(private val context: Context? = null) {

    fun deleteCodePointsBeforeCursor(ic: InputConnection, count: Int = 1): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return ic.deleteSurroundingTextInCodePoints(count, 0)
        }

        val before = ic.getTextBeforeCursor(4, 0) ?: return ic.deleteSurroundingText(count, 0)
        if (before.isEmpty()) return false

        val lastChar = before.last()
        val toDelete = if (Character.isLowSurrogate(lastChar) && before.length >= 2 && Character.isHighSurrogate(before[before.length - 2])) {
            2
        } else {
            1
        }
        return ic.deleteSurroundingText(toDelete, 0)
    }

    fun isSentenceStart(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(3, 0) ?: return true
        if (before.isEmpty()) return true
        val trimmed = before.toString()
        return trimmed.endsWith(". ") || trimmed.endsWith("? ") || trimmed.endsWith("! ") || trimmed.endsWith("\n")
    }

    fun handleDoubleSpacePeriod(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(3, 0) ?: return false
        if (before.length >= 2 && before[before.length - 1] == ' ') {
            val prev = before[before.length - 2]
            if (Character.isLetterOrDigit(prev) || prev == ')' || prev == ']' || prev == '}' || prev == '"' || prev == '\'') {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                return true
            }
        }
        return false
    }

    fun commitSticker(
        ic: InputConnection?,
        editorInfo: EditorInfo?,
        file: File,
        mimeType: String = "image/png",
        descriptionText: String = "Sticker"
    ): Boolean {
                val ctx = context ?: return false
        if (ic == null || editorInfo == null || !file.exists()) return false

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                ctx,
                "gnosis.keyboard.fileprovider",
                file
            )

            val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            val isSupported = mimeTypes.any { ClipDescription.compareMimeTypes(it, mimeType) }

            if (isSupported) {
                val description = ClipDescription(descriptionText, arrayOf(mimeType))
                val inputContentInfo = InputContentInfoCompat(contentUri, description, null)

                var flags = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                }

                val success = InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, flags, null)
                if (success) return true
            }

            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newUri(ctx.contentResolver, descriptionText, contentUri)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(ctx, "App does not support direct image insertion. Sticker copied to clipboard!", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Failed to insert sticker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        return false
    }
}
