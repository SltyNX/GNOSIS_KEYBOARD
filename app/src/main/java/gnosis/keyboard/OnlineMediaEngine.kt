package gnosis.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OnlineMediaEngine {

    data class MediaItem(
        val id: String,
        val title: String,
        val thumbUrl: String,
        val fullUrl: String,
        val mimeType: String,
        val isGif: Boolean
    )

    data class StickerPack(
        val id: String,
        val name: String,
        val query: String,
        val iconUrl: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null

    private val memoryDrawableCache = LruCache<String, Drawable.ConstantState>(128)
    private val searchResultsCache = LruCache<String, List<MediaItem>>(256)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private const val GIPHY_API_KEY = "sXpGFDGZs0Dv1mmNFvYaGUvYwKX0PWIh"

    val DEFAULT_STICKER_PACKS = listOf(
        StickerPack("mim_yam", "Gesture Pals", "hand gesture stickers"),
        StickerPack("milk_mocha", "Milk & Mocha", "milk mocha sticker"),
        StickerPack("peach_goma", "Peach & Goma", "peach and goma sticker"),
        StickerPack("love_couples", "Love & Romance", "love cute sticker"),
        StickerPack("funny_memes", "Memes", "funny meme sticker"),
        StickerPack("reactions", "Reactions", "reaction sticker"),
        StickerPack("anime_chibi", "Anime Chibi", "chibi anime sticker"),
        StickerPack("cute_animals", "Animals", "cute puppy rabbit sticker"),
        StickerPack("cartoons", "Cartoons", "funny cartoon stickers")
    )

    fun searchMedia(
        query: String,
        isSticker: Boolean,
        offset: Int = 0,
        callback: (List<MediaItem>) -> Unit
    ) {
        val cacheKey = "${if (isSticker) "stk" else "gif"}:${query.trim().lowercase(java.util.Locale.ROOT)}_$offset"
        val cached = searchResultsCache.get(cacheKey)
        if (cached != null) {
            callback(cached)
            return
        }

        scope.launch {
            val results = performSearch(query.trim(), isSticker, offset)
            if (results.isNotEmpty()) {
                searchResultsCache.put(cacheKey, results)
                appContext?.let { ctx ->
                    prebufferThumbnails(ctx, results)
                    prefetchFullAssets(ctx, results)
                }
            }
            withContext(Dispatchers.Main) {
                callback(results)
            }
        }
    }

    private fun prebufferThumbnails(context: Context, items: List<MediaItem>, limit: Int = 12) {
        val maxPrefetch = minOf(items.size, limit)
        for (i in 0 until maxPrefetch) {
            try {
                ensureCachedFile(context, items[i].thumbUrl)
            } catch (_: Throwable) {}
        }
    }

    fun prefetchFullAssets(context: Context, items: List<MediaItem>, limit: Int = 12) {
        scope.launch {
            val count = minOf(items.size, limit)
            for (i in 0 until count) {
                try {
                    val item = items[i]
                    if (item.fullUrl.isNotEmpty()) {
                        ensureCachedFile(context, item.fullUrl)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun performSearch(query: String, isSticker: Boolean, offset: Int = 0): List<MediaItem> {
        val effectiveQuery = when {
            query.isNotEmpty() -> query
            isSticker -> "sticker"
            else -> "trending"
        }

        if (isSticker) {
            // Fetch animated (and static) stickers from Giphy API
            val giphyItems = searchGiphy(effectiveQuery, isSticker = true, offset)
            // Also fetch static transparent PNG stickers from Wikimedia Commons
            val wikiItems = searchWikimedia(effectiveQuery, isSticker = true)

            // Interleave animated and static stickers so both are rich and populated
            val combined = mutableListOf<MediaItem>()
            val maxLen = maxOf(giphyItems.size, wikiItems.size)
            for (i in 0 until maxLen) {
                if (i < giphyItems.size) combined.add(giphyItems[i])
                if (i < wikiItems.size) combined.add(wikiItems[i])
            }
            if (combined.isNotEmpty()) {
                return combined.distinctBy { it.id }
            }
            if (giphyItems.isNotEmpty()) return giphyItems
            if (wikiItems.isNotEmpty()) return wikiItems
            return getCuratedFallback(effectiveQuery, true)
        } else {
            // GIFs from Giphy public API
            val giphyItems = searchGiphy(effectiveQuery, isSticker = false, offset)
            if (giphyItems.isNotEmpty()) return giphyItems
            val wikiItems = searchWikimedia(effectiveQuery, false)
            if (wikiItems.isNotEmpty()) return wikiItems
            return getCuratedFallback(effectiveQuery, false)
        }
    }

    private fun searchGiphy(query: String, isSticker: Boolean, offset: Int = 0): List<MediaItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val isTrendingQuery = query.equals("trending", ignoreCase = true) || query.isBlank()
        val base = if (isSticker) "https://api.giphy.com/v1/stickers" else "https://api.giphy.com/v1/gifs"
        val urlStr = if (isTrendingQuery) {
            "$base/trending?api_key=$GIPHY_API_KEY&limit=50&offset=$offset&rating=g"
        } else {
            "$base/search?q=$encoded&api_key=$GIPHY_API_KEY&limit=50&offset=$offset&rating=g"
        }

        val items = mutableListOf<MediaItem>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val rawStream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                    java.util.zip.GZIPInputStream(conn.inputStream)
                } else {
                    conn.inputStream
                }
                val jsonStr = rawStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        val id = obj.optString("id", i.toString())
                        val title = obj.optString("title", query)
                        val images = obj.optJSONObject("images")
                        if (images != null) {
                            val origObj = images.optJSONObject("original")
                            val origUrl = origObj?.optString("url") ?: ""
                            val origWebp = origObj?.optString("webp") ?: ""
                            val framesStr = origObj?.optString("frames", "1") ?: "1"
                            val frames = framesStr.toIntOrNull() ?: 1
                            val isAnimatedSticker = isSticker && (frames > 1 || origUrl.contains(".gif", ignoreCase = true))

                            // Reduced resolution thumbnails for instant loading & light memory footprint
                            val thumb = if (isSticker) {
                                if (isAnimatedSticker) {
                                    images.optJSONObject("fixed_height_small")?.optString("url")?.takeIf { it.isNotEmpty() }
                                        ?: images.optJSONObject("fixed_width_small")?.optString("url")?.takeIf { it.isNotEmpty() }
                                        ?: images.optJSONObject("preview_gif")?.optString("url")?.takeIf { it.isNotEmpty() }
                                        ?: origUrl
                                } else {
                                    images.optJSONObject("fixed_height_small")?.optString("webp")?.takeIf { it.isNotEmpty() }
                                        ?: images.optJSONObject("fixed_height_small")?.optString("url")?.takeIf { it.isNotEmpty() }
                                        ?: images.optJSONObject("fixed_height_still")?.optString("url")?.takeIf { it.isNotEmpty() }
                                        ?: origWebp.ifEmpty { origUrl }
                                }
                            } else {
                                images.optJSONObject("fixed_width_small")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("fixed_height_small")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("fixed_width_downsampled")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("preview_gif")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("fixed_width")?.optString("url")
                                    ?: ""
                            }

                            // Full high quality original assets for sending in pristine quality
                            val full = if (isSticker) {
                                if (isAnimatedSticker) {
                                    origUrl.ifEmpty { images.optJSONObject("fixed_height")?.optString("url") ?: thumb }
                                } else {
                                    origWebp.takeIf { it.isNotEmpty() } ?: origUrl.ifEmpty { thumb }
                                }
                            } else {
                                origUrl.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("downsized_large")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("downsized")?.optString("url")?.takeIf { it.isNotEmpty() }
                                    ?: images.optJSONObject("fixed_width")?.optString("url")
                                    ?: thumb
                            }

                            val isGifItem = if (isSticker) isAnimatedSticker else true
                            val mime = if (isGifItem) {
                                "image/gif"
                            } else {
                                if (full.contains(".webp", ignoreCase = true)) "image/webp" else "image/png"
                            }

                            if (thumb.isNotEmpty() && full.isNotEmpty()) {
                                items.add(
                                    MediaItem(
                                        id = id,
                                        title = title.ifBlank { if (isSticker) "Sticker" else "GIF" },
                                        thumbUrl = thumb,
                                        fullUrl = full,
                                        mimeType = mime,
                                        isGif = isGifItem
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
        return items
    }

    private fun searchWikimedia(query: String, isSticker: Boolean): List<MediaItem> {
        val encodedSearch = URLEncoder.encode(query, "UTF-8")
        val mimeFilter = if (isSticker) "filemime:image/png" else "filemime:image/gif"
        val urlStr = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedSearch+$mimeFilter&gsrnamespace=6&gsrlimit=30&prop=imageinfo&iiprop=url|mime&iiurlwidth=280&format=json"
        val items = mutableListOf<MediaItem>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "GnosisKeyboard/3.0 (Android IME; Media Engine)")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val queryObj = root.optJSONObject("query")
                val pagesObj = queryObj?.optJSONObject("pages")

                if (pagesObj != null) {
                    val keys = pagesObj.keys()
                    while (keys.hasNext()) {
                        val pageKey = keys.next()
                        val page = pagesObj.getJSONObject(pageKey)
                        val title = page.optString("title", "").replace("File:", "").replace(".gif", "").replace(".png", "")
                        val imageInfoArray = page.optJSONArray("imageinfo")
                        if (imageInfoArray != null && imageInfoArray.length() > 0) {
                            val info = imageInfoArray.getJSONObject(0)
                            val thumb = info.optString("thumburl", "")
                            val full = info.optString("url", "")
                            val mime = info.optString("mime", if (isSticker) "image/png" else "image/gif")

                            if (full.isNotEmpty()) {
                                items.add(
                                    MediaItem(
                                        id = page.optString("pageid", full.hashCode().toString()),
                                        title = title.ifBlank { if (isSticker) "Sticker" else "GIF" },
                                        thumbUrl = thumb.ifBlank { full },
                                        fullUrl = full,
                                        mimeType = mime,
                                        isGif = !isSticker || mime.contains("gif")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
        return items
    }

    private fun getCuratedFallback(query: String, isSticker: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        if (isSticker) {
            val stickerPacks = listOf(
                "Love Panda" to "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/Grosser_Panda.spar.jpg/260px-Grosser_Panda.spar.jpg",
                "Cute Heart" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Heart_coraz%C3%B3n.svg/240px-Heart_coraz%C3%B3n.svg.png",
                "Happy Cat" to "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/260px-Cat03.jpg",
                "Penguin Hug" to "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Emperor_Penguin_Manchot_empereur.jpg/240px-Emperor_Penguin_Manchot_empereur.jpg"
            )
            for ((idx, p) in stickerPacks.withIndex()) {
                items.add(
                    MediaItem(
                        id = "fallback_stk_$idx",
                        title = p.first,
                        thumbUrl = p.second,
                        fullUrl = p.second,
                        mimeType = "image/png",
                        isGif = false
                    )
                )
            }
        } else {
            val gifPacks = listOf(
                "Heart Pulse" to "https://upload.wikimedia.org/wikipedia/commons/1/14/Animated_heart_pulse.gif",
                "Rotating Earth" to "https://upload.wikimedia.org/wikipedia/commons/2/2c/Rotating_earth_%28large%29.gif"
            )
            for ((idx, p) in gifPacks.withIndex()) {
                items.add(
                    MediaItem(
                        id = "fallback_gif_$idx",
                        title = p.first,
                        thumbUrl = p.second,
                        fullUrl = p.second,
                        mimeType = "image/gif",
                        isGif = true
                    )
                )
            }
        }
        return items
    }

    fun ensureCachedFile(context: Context, urlStr: String): File? {
        if (urlStr.isBlank()) return null
        val cacheDir = File(context.cacheDir, "online_media_cache").apply { mkdirs() }
        val ext = when {
            urlStr.contains(".png", ignoreCase = true) -> "png"
            urlStr.contains(".webp", ignoreCase = true) -> "webp"
            urlStr.contains(".jpg", ignoreCase = true) || urlStr.contains(".jpeg", ignoreCase = true) -> "jpg"
            else -> "gif"
        }
        val hash = (urlStr.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val cachedFile = File(cacheDir, "$hash.$ext")

        // Invalidate if missing or suspiciously tiny (corrupt download)
        if (!cachedFile.exists() || cachedFile.length() < 300L) {
            val tmpFile = File(cacheDir, "$hash.tmp")
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    setRequestProperty("Connection", "Keep-Alive")
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val rawStream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                        java.util.zip.GZIPInputStream(conn.inputStream)
                    } else {
                        conn.inputStream
                    }
                    val expectedLen = conn.contentLength.toLong()
                    rawStream.buffered(65536).use { input ->
                        FileOutputStream(tmpFile).buffered(65536).use { output ->
                            input.copyTo(output, bufferSize = 65536)
                        }
                    }
                    val isComplete = if (expectedLen > 0L && !"gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                        tmpFile.length() == expectedLen
                    } else {
                        tmpFile.length() >= 300L
                    }
                    if (tmpFile.exists() && isComplete) {
                        if (cachedFile.exists()) cachedFile.delete()
                        tmpFile.renameTo(cachedFile)
                    } else {
                        try { tmpFile.delete() } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
                try { tmpFile.delete() } catch (_: Throwable) {}
                try { cachedFile.delete() } catch (_: Throwable) {}
                return null
            } finally {
                try { tmpFile.delete() } catch (_: Throwable) {}
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }

        return if (cachedFile.exists() && cachedFile.length() >= 300L) cachedFile else null
    }

    private fun decodeFileToDrawable(context: Context, file: File?): Drawable? {
        if (file == null || !file.exists() || file.length() < 300L) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                val decoded = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                if (decoded is AnimatedImageDrawable) {
                    decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                }
                decoded
            } else {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) BitmapDrawable(context.resources, bmp) else null
            }
        } catch (_: Throwable) {
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) BitmapDrawable(context.resources, bmp) else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun loadMediaDrawable(
        context: Context,
        urlStr: String,
        isGif: Boolean,
        targetW: Int,
        targetH: Int,
        callback: (Drawable?) -> Unit
    ) {
        val cached = memoryDrawableCache.get(urlStr)
        if (cached != null) {
            try {
                val d = cached.newDrawable().mutate()
                if (d is Animatable) {
                    d.start()
                }
                callback(d)
                return
            } catch (_: Throwable) {}
        }

        scope.launch {
            var file = ensureCachedFile(context, urlStr)
            var drawable = decodeFileToDrawable(context, file)

            // Self-healing: If corrupt cache caused decode to fail, purge corrupt file and retry download fresh once
            if (drawable == null && file != null && file.exists()) {
                try { file.delete() } catch (_: Throwable) {}
                memoryDrawableCache.remove(urlStr)
                file = ensureCachedFile(context, urlStr)
                drawable = decodeFileToDrawable(context, file)
            }

            if (drawable != null && drawable.constantState != null) {
                try {
                    memoryDrawableCache.put(urlStr, drawable.constantState!!)
                } catch (_: Throwable) {}
            }

            withContext(Dispatchers.Main) {
                if (drawable is Animatable) {
                    drawable.start()
                }
                callback(drawable)
            }
        }
    }

    fun loadThumbnail(
        context: Context,
        urlStr: String,
        targetW: Int,
        targetH: Int,
        callback: (Bitmap?) -> Unit
    ) {
        scope.launch {
            val file = ensureCachedFile(context, urlStr)
            val bmp = if (file != null && file.exists()) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    val w = opts.outWidth
                    val h = opts.outHeight
                    var sample = 1
                    if (w > targetW || h > targetH) {
                        val halfW = w / 2
                        val halfH = h / 2
                        while ((halfW / sample) >= targetW && (halfH / sample) >= targetH) {
                            sample *= 2
                        }
                    }
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sample.coerceAtLeast(1)
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                } catch (_: Throwable) {
                    null
                }
            } else null

            withContext(Dispatchers.Main) {
                callback(bmp)
            }
        }
    }

    fun downloadAndCommitMedia(
        context: Context,
        ic: InputConnection?,
        info: EditorInfo?,
        item: MediaItem,
        onDone: (Boolean) -> Unit
    ) {
        scope.launch {
            val isGifMedia = item.isGif
            val supportedMimes = if (info != null) EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
            val acceptsWebp = supportedMimes.any { it.equals("image/webp", ignoreCase = true) || it == "image/*" || it == "*/*" }
            val acceptsPng = supportedMimes.any { it.equals("image/png", ignoreCase = true) || it == "image/*" || it == "*/*" }

            val mimeType = if (isGifMedia) {
                "image/gif"
            } else {
                if (acceptsWebp || !acceptsPng) "image/webp" else "image/png"
            }

            // 1. Instant check: Retrieve full high-resolution asset from prefetch cache
            var sourceFile = if (item.fullUrl.isNotEmpty()) ensureCachedFile(context, item.fullUrl) else null
            if (sourceFile == null || !sourceFile.exists() || sourceFile.length() < 300L) {
                // Fallback to thumbUrl so sending never blocks or hangs
                sourceFile = ensureCachedFile(context, item.thumbUrl)
            }

            if (sourceFile == null || !sourceFile.exists() || sourceFile.length() < 300L) {
                withContext(Dispatchers.Main) { onDone(false) }
                return@launch
            }

            // 2. Direct high-speed commit (zero unnecessary disk copy or re-encoding)
            val fileToCommit = if (isGifMedia) {
                if (sourceFile.name.endsWith(".gif", ignoreCase = true)) {
                    sourceFile
                } else {
                    val mediaDir = File(context.cacheDir, "online_media").apply { mkdirs() }
                    val target = File(mediaDir, "media_${item.id}.gif")
                    if (!target.exists() || target.length() != sourceFile.length()) {
                        sourceFile.copyTo(target, overwrite = true)
                    }
                    target
                }
            } else {
                if (mimeType == "image/webp" && sourceFile.name.endsWith(".webp", ignoreCase = true)) {
                    sourceFile
                } else if (mimeType == "image/png" && sourceFile.name.endsWith(".png", ignoreCase = true)) {
                    sourceFile
                } else {
                    val mediaDir = File(context.cacheDir, "online_media").apply { mkdirs() }
                    val ext = if (mimeType == "image/webp") "webp" else "png"
                    val target = File(mediaDir, "media_${item.id}.$ext")
                    if (!target.exists() || target.length() < 300L) {
                        val bmp = BitmapFactory.decodeFile(sourceFile.absolutePath)
                        if (bmp != null) {
                            FileOutputStream(target).use { out ->
                                if (ext == "webp") {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        bmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        bmp.compress(Bitmap.CompressFormat.WEBP, 100, out)
                                    }
                                } else {
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }
                        } else {
                            sourceFile.copyTo(target, overwrite = true)
                        }
                    }
                    target
                }
            }

            val success = if (fileToCommit.exists() && fileToCommit.length() > 0L) {
                val clipItem = ClipboardHistory.ClipItem(
                    text = item.title,
                    isImage = true,
                    imagePath = fileToCommit.absolutePath,
                    mimeType = mimeType
                )
                ClipboardHistory.commitContent(context, ic, info, clipItem)
            } else {
                false
            }

            withContext(Dispatchers.Main) {
                onDone(success)
            }
        }
    }
}
