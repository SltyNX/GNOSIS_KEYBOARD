package gnosis.keyboard.suggest

data class Gnosis3SlotLayout(
    val slot0Left: GnosisCandidate?,
    val slot1Center: GnosisCandidate,
    val slot2Right: GnosisCandidate?,
    val emojiCandidate: GnosisCandidate? = null
) {
    fun toList(): List<GnosisCandidate> {
        val list = mutableListOf<GnosisCandidate>()
        slot0Left?.let { if (it.text.isNotBlank() && list.none { x -> x.text.equals(it.text, ignoreCase = true) }) list.add(it) }
        if (slot1Center.text.isNotBlank() && list.none { x -> x.text.equals(slot1Center.text, ignoreCase = true) }) {
            list.add(slot1Center)
        }
        slot2Right?.let { if (it.text.isNotBlank() && list.none { x -> x.text.equals(it.text, ignoreCase = true) }) list.add(it) }
        return list
    }

    val autoCorrectCandidate: GnosisCandidate?
        get() = if (slot1Center.isAutoCorrect || slot1Center.score >= 0.70) slot1Center else null

    companion object {
        fun empty(placeholder: String = ""): Gnosis3SlotLayout {
            return Gnosis3SlotLayout(
                slot0Left = null,
                slot1Center = GnosisCandidate(placeholder, GnosisCandidateType.RAW, 0.0),
                slot2Right = null,
                emojiCandidate = null
            )
        }
    }
}

typealias Gboard3SlotLayout = Gnosis3SlotLayout
