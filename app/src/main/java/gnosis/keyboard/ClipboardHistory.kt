package gnosis.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections
import java.util.UUID

object ClipboardHistory {
    private const val PREFS = "gnosis_clipboard"
    private const val PREFS_CONSUMED = "gnosis_clipboard_consumed"
    private const val KEY_CONSUMED_SET = "consumed_keys"
    private const val KEY_ITEMS = "clipboard_items_json"
    private const val KEY_DISMISSED_SOURCES = "clipboard_dismissed_sources"
    private const val MAX_ITEMS = 30
    private const val FILE_PROVIDER_AUTHORITY = "gnosis.keyboard.fileprovider"

    const val CAT_TEXT = "text"
    const val CAT_SCREENSHOT = "screenshot"
    private const val LEGACY_IMAGE = "image"
    val CATEGORIES = listOf(CAT_TEXT, CAT_SCREENSHOT)

    private val inMemoryConsumed = Collections.synchronizedSet(mutableSetOf<String>())
    private var consumedLoaded = false

    private fun loadConsumed(ctx: Context) {
        if (consumedLoaded) return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_CONSUMED, Context.MODE_PRIVATE)
            val saved = prefs.getStringSet(KEY_CONSUMED_SET, emptySet()) ?: emptySet()
            inMemoryConsumed.clear()
            inMemoryConsumed.addAll(saved)
            consumedLoaded = true
        } catch (_: Exception) {}
    }

    private fun saveConsumed(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS_CONSUMED, Context.MODE_PRIVATE)
            val bounded = if (inMemoryConsumed.size > 200) inMemoryConsumed.toList().takeLast(200).toSet() else inMemoryConsumed.toSet()
            prefs.edit().putStringSet(KEY_CONSUMED_SET, bounded).apply()
        } catch (_: Exception) {}
    }

    fun isConsumed(ctx: Context, item: ClipItem): Boolean {
        loadConsumed(ctx)
        if (inMemoryConsumed.contains(item.id)) return true
        if (item.isImage) {
            if (item.sourceId > 0L && inMemoryConsumed.contains("src_${item.sourceId}")) return true
            if (item.imagePath.isNotEmpty() && inMemoryConsumed.contains("path_${item.imagePath}")) return true
        } else {
            if (item.text.isNotEmpty() && inMemoryConsumed.contains("text_${item.text.hashCode()}")) return true
        }
        return false
    }

    fun markConsumed(ctx: Context, item: ClipItem) {
        loadConsumed(ctx)
        inMemoryConsumed.add(item.id)
        if (item.isImage) {
            if (item.sourceId > 0L) inMemoryConsumed.add("src_${item.sourceId}")
            if (item.imagePath.isNotEmpty()) inMemoryConsumed.add("path_${item.imagePath}")
        } else {
            if (item.text.isNotEmpty()) inMemoryConsumed.add("text_${item.text.hashCode()}")
        }
        saveConsumed(ctx)
    }

    private fun unmarkConsumedText(ctx: Context, text: String) {
        loadConsumed(ctx)
        inMemoryConsumed.remove("text_${text.hashCode()}")
        saveConsumed(ctx)
    }

    private fun unmarkConsumedImage(ctx: Context, sourceId: Long, path: String) {
        loadConsumed(ctx)
        if (sourceId > 0L) inMemoryConsumed.remove("src_$sourceId")
        if (path.isNotEmpty()) inMemoryConsumed.remove("path_$path")
        saveConsumed(ctx)
    }

    fun canAcceptImages(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false
        val supportedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (supportedMimes.isNullOrEmpty()) return false
        for (mime in supportedMimes) {
            val lower = mime.lowercase()
            if (lower == "*/*" || lower.startsWith("image/")) {
                return true
            }
        }
        return false
    }

    fun categoryLabel(category: String): String = when (category) {
        CAT_SCREENSHOT -> "Screenshots"
        else -> "Text"
    }

    fun prefKey(category: String): String = when (category) {
        CAT_SCREENSHOT -> "clip_screenshots"
        else -> "clip_text"
    }

    fun isEnabled(ctx: Context, category: String): Boolean =
        Skins.prefs(ctx).getBoolean(prefKey(category), category == CAT_TEXT)

    fun setEnabled(ctx: Context, category: String, enabled: Boolean) {
        val e = Skins.prefs(ctx).edit().putBoolean(prefKey(category), enabled)
        if (enabled && category == CAT_SCREENSHOT) {
            e.putLong(ScreenshotWatcher.KEY_LAST_SCAN, System.currentTimeMillis() / 1000L)
        }
        e.apply()
    }

    fun isDismissedSource(ctx: Context, sourceId: Long): Boolean {
        if (sourceId <= 0L) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DISMISSED_SOURCES, null) ?: return false
        return set.contains(sourceId.toString())
    }

    fun markSourceDismissed(ctx: Context, sourceId: Long) {
        if (sourceId <= 0L) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_DISMISSED_SOURCES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(sourceId.toString())
        val bounded = if (current.size > 200) current.toList().takeLast(200).toSet() else current
        prefs.edit().putStringSet(KEY_DISMISSED_SOURCES, bounded).apply()
    }

    fun imagesDir(ctx: Context): File = File(ctx.filesDir, "clipboard_images").apply { mkdirs() }

    data class ClipItem(
        val id: String = UUID.randomUUID().toString(),
        val isImage: Boolean = false,
        val text: String = "",
        val imagePath: String = "",
        val mimeType: String = "text/plain",
        val timestamp: Long = System.currentTimeMillis(),
        var isPinned: Boolean = false,
        val category: String = CAT_TEXT,
        val sourceId: Long = -1L
    )

    @Volatile
    var changeListener: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun notifyChanged() {
        mainHandler.post { changeListener?.invoke() }
    }

    private val inMemoryHistory = mutableListOf<ClipItem>()
    private var initialized = false
    private var lastProcessedClipSignature: String? = null

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, "") ?: ""
        inMemoryHistory.clear()
        if (raw.isNotEmpty()) {
            try {
                val array = JSONArray(raw)
                val seenImageKeys = mutableSetOf<String>()
                val seenTexts = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val item = ClipItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        isImage = obj.optBoolean("isImage", false),
                        text = obj.optString("text", ""),
                        imagePath = obj.optString("imagePath", ""),
                        mimeType = obj.optString("mimeType", "text/plain"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false),
                        category = obj.optString(
                            "category",
                            if (obj.optBoolean("isImage", false)) LEGACY_IMAGE else CAT_TEXT
                        ),
                        sourceId = obj.optLong("sourceId", -1L)
                    )
                    if (item.category == LEGACY_IMAGE) {
                        if (item.imagePath.isNotEmpty()) File(item.imagePath).delete()
                        continue
                    }
                    if (item.isImage) {
                        val file = File(item.imagePath)
                        if (!file.exists()) continue
                        val dedupKey = if (item.sourceId > 0L) "src_${item.sourceId}" else "len_${file.length()}"
                        if (seenImageKeys.contains(dedupKey)) {
                            if (!inMemoryHistory.any { it.imagePath == item.imagePath }) {
                                try { file.delete() } catch (_: Exception) {}
                            }
                            continue
                        }
                        seenImageKeys.add(dedupKey)
                        inMemoryHistory.add(item)
                    } else {
                        if (item.text.isNotEmpty()) {
                            if (!item.isPinned && seenTexts.contains(item.text)) continue
                            seenTexts.add(item.text)
                            inMemoryHistory.add(item)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        loadConsumed(ctx)
        initialized = true
    }

    fun syncFromSystem(ctx: Context) {
        init(ctx)
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return

            val desc = clip.description
            val item = clip.getItemAt(0) ?: return

            val clipTimestamp = if (android.os.Build.VERSION.SDK_INT >= 26) desc?.timestamp ?: 0L else 0L
            val itemUriStr = item.uri?.toString() ?: ""
            val itemTextStr = item.text?.toString() ?: ""
            val currentSignature = "$clipTimestamp|$itemUriStr|${itemTextStr.hashCode()}|${clip.itemCount}"
            if (currentSignature == lastProcessedClipSignature) {
                return
            }
            lastProcessedClipSignature = currentSignature

            var isImageMime = false
            var mime = "image/png"
            if (desc != null) {
                for (i in 0 until desc.mimeTypeCount) {
                    val m = desc.getMimeType(i)
                    if (m.startsWith("image/")) {
                        isImageMime = true
                        mime = m
                        break
                    }
                }
            }

            val uri = item.uri
            if (isImageMime || uri != null) {
                if (uri != null && isEnabled(ctx, CAT_SCREENSHOT)) {
                    val actualMime = if (isImageMime) mime else (ctx.contentResolver.getType(uri) ?: "image/png")
                    if (actualMime.startsWith("image/")) {
                        val sourceId = try {
                            if (uri.authority == "media" || uri.toString().contains("media/external/images/media")) {
                                ContentUris.parseId(uri)
                            } else -1L
                        } catch (_: Exception) { -1L }

                        if (sourceId > 0L) {
                            if (isDismissedSource(ctx, sourceId) || hasSource(ctx, CAT_SCREENSHOT, sourceId)) {
                                return
                            }
                        }

                        val ext = if (actualMime.contains("jpeg") || actualMime.contains("jpg")) ".jpg" else ".png"
                        val filename = if (sourceId > 0L) "shot_$sourceId$ext" else "clip_${Math.abs(uri.toString().hashCode())}$ext"
                        val target = File(imagesDir(ctx), filename)

                        if (inMemoryHistory.any { it.isImage && ((sourceId > 0L && it.sourceId == sourceId) || it.imagePath == target.absolutePath) }) {
                            return
                        }

                        if (!target.exists() || target.length() == 0L) {
                            ctx.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(target).use { output -> input.copyTo(output) }
                            }
                        }

                        if (target.length() > 0) {
                            addImage(ctx, target.absolutePath, actualMime, category = CAT_SCREENSHOT, sourceId = sourceId)
                        } else {
                            target.delete()
                        }
                    }
                }
                return
            }

            val text = item.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length < 5000) {
                addText(ctx, text)
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun addText(ctx: Context, text: String) {
        init(ctx)
        if (!isEnabled(ctx, CAT_TEXT)) return
        val clean = text.trim()
        if (clean.isEmpty()) return

        unmarkConsumedText(ctx, clean)
        inMemoryHistory.removeAll { !it.isPinned && !it.isImage && it.text == clean }

        inMemoryHistory.add(0, ClipItem(
            isImage = false,
            text = clean,
            mimeType = "text/plain"
        ))

        trimHistory(ctx)
        save(ctx)
        notifyChanged()
    }

    @Synchronized
    fun hasSource(ctx: Context, category: String, sourceId: Long): Boolean {
        init(ctx)
        return inMemoryHistory.any { it.category == category && it.sourceId == sourceId }
    }

    @Synchronized
    fun addImage(
        ctx: Context,
        filePath: String,
        mimeType: String,
        category: String,
        sourceId: Long = -1L
    ) {
        init(ctx)
        if (!isEnabled(ctx, category)) return
        if (filePath.isEmpty() || !File(filePath).exists()) return

        unmarkConsumedImage(ctx, sourceId, filePath)
        inMemoryHistory.removeAll { !it.isPinned && it.isImage && ((sourceId > 0L && it.sourceId == sourceId) || it.imagePath == filePath) }

        inMemoryHistory.add(0, ClipItem(
            isImage = true,
            imagePath = filePath,
            mimeType = mimeType,
            category = category,
            sourceId = sourceId
        ))

        trimHistory(ctx)
        save(ctx)
        notifyChanged()
    }

    private fun trimHistory(ctx: Context) {
        for (cat in CATEGORIES) {
            val unpinned = inMemoryHistory.filter { it.category == cat && !it.isPinned }
            if (unpinned.size <= MAX_ITEMS) continue
            for (old in unpinned.drop(MAX_ITEMS)) {
                if (old.isImage) {
                    if (old.sourceId > 0L) {
                        markSourceDismissed(ctx, old.sourceId)
                    }
                    try { File(old.imagePath).delete() } catch (_: Exception) {}
                }
                inMemoryHistory.remove(old)
            }
        }
    }

    @Synchronized
    fun getItems(ctx: Context, category: String? = null): List<ClipItem> {
        init(ctx)
        return if (category == null) inMemoryHistory.toList()
        else inMemoryHistory.filter { it.category == category }
    }

    @Synchronized
    fun togglePin(ctx: Context, id: String) {
        init(ctx)
        val item = inMemoryHistory.find { it.id == id } ?: return
        item.isPinned = !item.isPinned
        save(ctx)
        notifyChanged()
    }

    @Synchronized
    fun delete(ctx: Context, id: String) {
        init(ctx)
        val item = inMemoryHistory.find { it.id == id } ?: return
        if (item.isImage) {
            if (item.sourceId > 0L) {
                markSourceDismissed(ctx, item.sourceId)
            }
            try { File(item.imagePath).delete() } catch (_: Exception) {}
        } else if (item.text.isNotEmpty()) {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val currentSysText = clip.getItemAt(0)?.text?.toString()?.trim()
                    if (currentSysText == item.text) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            cm.clearPrimaryClip()
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        inMemoryHistory.remove(item)
        save(ctx)
        notifyChanged()
    }

    @Synchronized
    fun clear(ctx: Context, category: String? = null) {
        init(ctx)
        val doomed = inMemoryHistory.filter { !it.isPinned && (category == null || it.category == category) }
        val doomedTexts = doomed.filter { !it.isImage }.map { it.text }.toSet()
        doomed.forEach {
            if (it.isImage) {
                if (it.sourceId > 0L) {
                    markSourceDismissed(ctx, it.sourceId)
                }
                try { File(it.imagePath).delete() } catch (_: Exception) {}
            }
        }
        if (category == null || category == CAT_TEXT) {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val currentSysText = clip.getItemAt(0)?.text?.toString()?.trim()
                    if (currentSysText != null && doomedTexts.contains(currentSysText)) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            cm.clearPrimaryClip()
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        inMemoryHistory.removeAll(doomed.toSet())
        save(ctx)
        notifyChanged()
    }

    @Synchronized
    fun unpinnedCount(ctx: Context, category: String? = null): Int {
        init(ctx)
        return inMemoryHistory.count { !it.isPinned && (category == null || it.category == category) }
    }

    fun commitContent(
        ctx: Context,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        item: ClipItem
    ): Boolean {
        if (!item.isImage || inputConnection == null || editorInfo == null) return false

        val file = File(item.imagePath)
        if (!file.exists()) return false

        try {
            val contentUri = FileProvider.getUriForFile(ctx, FILE_PROVIDER_AUTHORITY, file)
            val description = ClipDescription("Clipboard Image", arrayOf(item.mimeType))

            val supportedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            var supported = false
            for (mime in supportedMimes) {
                if (description.hasMimeType(mime) || mime == "*/*" || mime == "image/*") {
                    supported = true
                    break
                }
            }

            if (supported) {
                val inputContentInfo = InputContentInfoCompat(contentUri, description, null)
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                return InputConnectionCompat.commitContent(inputConnection, editorInfo, inputContentInfo, flags, null)
            } else {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = ClipData.newUri(ctx.contentResolver, "Clipboard Image", contentUri)
                cm?.setPrimaryClip(clipData)
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    fun loadThumbnail(filePath: String, targetW: Int, targetH: Int): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(filePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun save(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in inMemoryHistory) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("isImage", item.isImage)
                put("text", item.text)
                put("imagePath", item.imagePath)
                put("mimeType", item.mimeType)
                put("timestamp", item.timestamp)
                put("isPinned", item.isPinned)
                put("category", item.category)
                put("sourceId", item.sourceId)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
