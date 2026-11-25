package gnosis.keyboard.engine.delight

import android.content.Context
import android.util.Log

/**
 * Gnosis Delight Native Bridge
 *
 * Safely loads and orchestrates the Gnosis consolidated native runtime:
 * - libintegrated_shared_object.so (Core neural monolith: Mozc, HMM, SODA, ScaNN)
 * - libgrammar-checker_jni.so (Sentence Explorer grammar checking bridge)
 * - libgnosis_pipeline_jni.so / libgboard_pipeline_jni.so (Gnosis neural pipeline engine)
 * - libgraphics-core.so & libandroidx.graphics.path.so (Low-latency rendering & glide paths)
 * - libscam_*.so (Vector scoring & nearest neighbor indexers)
 * - libogg_opus_encoder.so (Audio voice compression)
 */
object GnosisDelightBridge {

    private const val TAG = "GnosisDelightBridge"
    private val loadedLibraries = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val failedLibraries = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var isCoreInitialized = false

    val ALL_NATIVE_MODULES = listOf(
        "integrated_shared_object",
        "androidx.graphics.path",
        "graphics-core",
        "gnosis_pipeline_jni",
        "gboard_pipeline_jni",
        "grammar-checker_jni",
        "surface_util_jni",
        "ogg_opus_encoder",
        "scam_scorer",
        "scam_partition",
        "scam_partitioner"
    )

    fun loadAllNativeModules(context: Context? = null): Set<String> {
        for (lib in ALL_NATIVE_MODULES) {
            loadLibrarySafely(lib)
        }
        isCoreInitialized = isLibraryLoaded("integrated_shared_object")
        return loadedLibraries.toSet()
    }

    fun loadLibrarySafely(libName: String): Boolean {
        if (loadedLibraries.contains(libName)) return true
        if (failedLibraries.contains(libName)) return false
        return try {
            System.loadLibrary(libName)
            loadedLibraries.add(libName)
            Log.d(TAG, "Successfully loaded native module: $libName")
            true
        } catch (_: Throwable) {
            failedLibraries.add(libName)
            false
        }
    }

    fun isLibraryLoaded(libName: String): Boolean = loadedLibraries.contains(libName)

    fun isNativeEngineAvailable(): Boolean = isCoreInitialized
}
