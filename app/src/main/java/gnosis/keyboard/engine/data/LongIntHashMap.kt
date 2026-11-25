package gnosis.keyboard.engine.data

class LongIntHashMap(val initialCapacity: Int = 4096) {

    private var keys: LongArray
    private var values: IntArray
    private var allocated: BooleanArray
    private var capacityMask: Int
    var size: Int = 0
        private set

    init {
        var cap = 16
        while (cap < initialCapacity) cap = cap shl 1
        keys = LongArray(cap)
        values = IntArray(cap)
        allocated = BooleanArray(cap)
        capacityMask = cap - 1
    }

    operator fun get(key: Long): Int {
        var idx = hash(key) and capacityMask
        while (allocated[idx]) {
            if (keys[idx] == key) {
                return values[idx]
            }
            idx = (idx + 1) and capacityMask
        }
        return 0
    }

    operator fun set(key: Long, value: Int) {
        put(key, value)
    }

    fun put(key: Long, value: Int) {
        if (size >= keys.size * 0.70) {
            rehash()
        }
        var idx = hash(key) and capacityMask
        while (allocated[idx]) {
            if (keys[idx] == key) {
                values[idx] = value
                return
            }
            idx = (idx + 1) and capacityMask
        }
        allocated[idx] = true
        keys[idx] = key
        values[idx] = value
        size++
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        val oldAllocated = allocated

        val newCap = keys.size shl 1
        keys = LongArray(newCap)
        values = IntArray(newCap)
        allocated = BooleanArray(newCap)
        capacityMask = newCap - 1
        size = 0

        for (i in oldKeys.indices) {
            if (oldAllocated[i]) {
                put(oldKeys[i], oldValues[i])
            }
        }
    }

    private fun hash(key: Long): Int {
        var h = key xor (key ushr 32)
        h = h xor (h ushr 16)
        return (h xor (h ushr 8)).toInt()
    }
}
