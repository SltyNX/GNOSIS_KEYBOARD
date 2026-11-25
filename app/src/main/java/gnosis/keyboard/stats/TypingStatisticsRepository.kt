package gnosis.keyboard.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

enum class StatsPeriod {
    DAYS_7,
    DAYS_30,
    ALL_TIME
}

data class DailyRecord(
    val date: String,
    var keystrokes: Long = 0L,
    var corrections: Long = 0L,
    var suggestionsAccepted: Long = 0L,
    var typingDurationSeconds: Long = 0L,
    val wordFrequencies: MutableMap<String, Int> = mutableMapOf()
)

data class AggregatedStats(
    val totalKeystrokes: Long,
    val totalCorrections: Long,
    val suggestionsAccepted: Long,
    val timeSavedSeconds: Long,
    val timeSavedFormatted: String,
    val keystrokesTrendPct: Int,
    val correctionsTrendPct: Int,
    val timeSavedTrendPct: Int,
    val topSuggestedWords: List<Pair<String, Int>>,
    val dailyActivity: List<Pair<String, Long>>,
    val isEmpty: Boolean
)

class TypingStatisticsRepository(private val storageDir: File) {

    companion object {
        const val PREF_STATS_ENABLED = "pref_typing_stats_enabled"
        private const val STATS_FILE_NAME = "typing_statistics.json"

        @Volatile
        private var instance: TypingStatisticsRepository? = null

        fun getInstance(context: Context): TypingStatisticsRepository {
            return instance ?: synchronized(this) {
                instance ?: TypingStatisticsRepository(context.filesDir).also { instance = it }
            }
        }
    }

    private val statsFile: File = File(storageDir, STATS_FILE_NAME)
    private val records = mutableMapOf<String, DailyRecord>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var isDirty = false

    init {
        loadFromDisk()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private val persistHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var persistScheduled = false
    private val persistRunnable = Runnable {
        executor.execute {
            synchronized(this) {
                if (!isDirty) {
                    persistScheduled = false
                    return@synchronized
                }
                saveToDiskInternal()
                isDirty = false
                persistScheduled = false
            }
        }
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) return
        for (l in listeners) {
            try {
                l.invoke()
            } catch (_: Throwable) {}
        }
    }

    private fun getTodayDate(): String {
        return dateFormat.format(Date())
    }

    @Synchronized
    private fun getOrCreateTodayRecord(): DailyRecord {
        val today = getTodayDate()
        return records.getOrPut(today) { DailyRecord(today) }
    }

    @Synchronized
    fun recordKeystroke(count: Int = 1) {
        if (count <= 0) return
        val rec = getOrCreateTodayRecord()
        rec.keystrokes += count
        isDirty = true
        schedulePersist()
        notifyListeners()
    }

    @Synchronized
    fun recordCorrection() {
        val rec = getOrCreateTodayRecord()
        rec.corrections += 1
        isDirty = true
        schedulePersist()
        notifyListeners()
    }

    @Synchronized
    fun recordSuggestionAccepted(word: String, isPrediction: Boolean = false) {
        val rec = getOrCreateTodayRecord()
        rec.suggestionsAccepted += 1
        val clean = word.trim().lowercase(Locale.ROOT)
        if (isSafeWord(clean)) {
            val cur = rec.wordFrequencies[clean] ?: 0
            rec.wordFrequencies[clean] = cur + 1
        }
        isDirty = true
        schedulePersist()
        notifyListeners()
    }


    @Synchronized
    fun recordWordTyped(word: String) {
        val rec = getOrCreateTodayRecord()
        rec.keystrokes += word.length
        val clean = word.trim().lowercase(Locale.ROOT)
        if (isSafeWord(clean)) {
            val cur = rec.wordFrequencies[clean] ?: 0
            rec.wordFrequencies[clean] = cur + 1
        }
        isDirty = true
        schedulePersist()
        notifyListeners()
    }

    @Synchronized
    fun recordAutoCorrection(originalWord: String = "", correctedWord: String = "") {
        recordCorrection()
    }

    @Synchronized
    fun recordCharactersTyped(count: Int) {
        recordKeystroke(count)
    }
    @Synchronized
    fun recordTypingDuration(seconds: Long) {
        if (seconds <= 0) return
        val rec = getOrCreateTodayRecord()
        rec.typingDurationSeconds += seconds
        isDirty = true
        schedulePersist()
        notifyListeners()
    }

    private fun isSafeWord(w: String): Boolean {
        if (w.length < 2 || w.length > 25) return false
        if (!w.all { it.isLetter() }) return false
        return true
    }

    @Synchronized
    fun getStats(period: StatsPeriod): AggregatedStats {
        val now = Calendar.getInstance()
        val daysLimit = when (period) {
            StatsPeriod.DAYS_7 -> 7
            StatsPeriod.DAYS_30 -> 30
            StatsPeriod.ALL_TIME -> 3650
        }

        val targetDates = mutableSetOf<String>()
        val prevDates = mutableSetOf<String>()

        for (i in 0 until daysLimit) {
            val cal = now.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, -i)
            targetDates.add(dateFormat.format(cal.time))
        }

        for (i in daysLimit until (daysLimit * 2)) {
            val cal = now.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, -i)
            prevDates.add(dateFormat.format(cal.time))
        }

        var totalKeystrokes = 0L
        var totalCorrections = 0L
        var totalSuggestions = 0L
        val wordFreq = mutableMapOf<String, Int>()
        val dailyActivity = mutableListOf<Pair<String, Long>>()

        var prevKeystrokes = 0L
        var prevCorrections = 0L
        var prevSuggestions = 0L

        for ((date, record) in records) {
            if (period == StatsPeriod.ALL_TIME || targetDates.contains(date)) {
                totalKeystrokes += record.keystrokes
                totalCorrections += record.corrections
                totalSuggestions += record.suggestionsAccepted
                for ((w, c) in record.wordFrequencies) {
                    wordFreq[w] = (wordFreq[w] ?: 0) + c
                }
                dailyActivity.add(date to record.keystrokes)
            } else if (prevDates.contains(date)) {
                prevKeystrokes += record.keystrokes
                prevCorrections += record.corrections
                prevSuggestions += record.suggestionsAccepted
            }
        }

        dailyActivity.sortBy { it.first }

        val timeSavedSec = ((totalSuggestions * 1.2) + (totalCorrections * 1.8)).toLong()
        val prevTimeSavedSec = ((prevSuggestions * 1.2) + (prevCorrections * 1.8)).toLong()

        val timeSavedFormatted = formatDuration(timeSavedSec)

        val keystrokesTrend = calculateTrend(totalKeystrokes, prevKeystrokes)
        val correctionsTrend = calculateTrend(totalCorrections, prevCorrections)
        val timeSavedTrend = calculateTrend(timeSavedSec, prevTimeSavedSec)

        val topWords = wordFreq.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        val isEmpty = totalKeystrokes == 0L && totalCorrections == 0L && totalSuggestions == 0L

        return AggregatedStats(
            totalKeystrokes = totalKeystrokes,
            totalCorrections = totalCorrections,
            suggestionsAccepted = totalSuggestions,
            timeSavedSeconds = timeSavedSec,
            timeSavedFormatted = timeSavedFormatted,
            keystrokesTrendPct = keystrokesTrend,
            correctionsTrendPct = correctionsTrend,
            timeSavedTrendPct = timeSavedTrend,
            topSuggestedWords = topWords,
            dailyActivity = dailyActivity,
            isEmpty = isEmpty
        )
    }

    private fun calculateTrend(current: Long, previous: Long): Int {
        if (previous <= 0L) return if (current > 0L) 100 else 0
        val diff = current - previous
        return ((diff.toDouble() / previous.toDouble()) * 100.0).toInt().coerceIn(-99, 999)
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0m"
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${seconds}s"
        }
    }

    @Synchronized
    fun clearAllStats() {
        records.clear()
        isDirty = true
        persistHandler.removeCallbacks(persistRunnable)
        persistScheduled = false
        executor.execute {
            synchronized(this) {
                saveToDiskInternal()
                isDirty = false
            }
        }
        notifyListeners()
    }

    private fun schedulePersist() {
        if (!persistScheduled) {
            persistScheduled = true
            persistHandler.postDelayed(persistRunnable, 4000L)
        }
    }

    @Synchronized
    fun flush() {
        persistHandler.removeCallbacks(persistRunnable)
        persistScheduled = false
        if (isDirty) {
            saveToDiskInternal()
            isDirty = false
        }
    }

    private fun saveToDiskInternal() {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            for ((date, rec) in records) {
                val obj = JSONObject().apply {
                    put("date", date)
                    put("keystrokes", rec.keystrokes)
                    put("corrections", rec.corrections)
                    put("suggestions", rec.suggestionsAccepted)
                    put("duration", rec.typingDurationSeconds)
                    val wordsObj = JSONObject()
                    for ((w, c) in rec.wordFrequencies) {
                        wordsObj.put(w, c)
                    }
                    put("words", wordsObj)
                }
                arr.put(obj)
            }
            root.put("records", arr)
            statsFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {}
    }

    private fun loadFromDisk() {
        if (!statsFile.exists()) return
        try {
            val text = statsFile.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val arr = root.optJSONArray("records") ?: return
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val date = obj.getString("date")
                val rec = DailyRecord(
                    date = date,
                    keystrokes = obj.optLong("keystrokes", 0L),
                    corrections = obj.optLong("corrections", 0L),
                    suggestionsAccepted = obj.optLong("suggestions", 0L),
                    typingDurationSeconds = obj.optLong("duration", 0L)
                )
                val wordsObj = obj.optJSONObject("words")
                if (wordsObj != null) {
                    val keys = wordsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        rec.wordFrequencies[k] = wordsObj.optInt(k, 0)
                    }
                }
                records[date] = rec
            }
        } catch (_: Throwable) {}
    }
}
