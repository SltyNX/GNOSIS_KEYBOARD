package gnosis.keyboard.engine.spell

object WordCasing {

    private val MAP: Map<String, String> = buildMap {
        for (w in listOf("i", "i'm", "i'll", "i've", "i'd")) put(w, "I" + w.substring(1))
        val plain = """
            monday tuesday wednesday thursday friday saturday sunday
            january february april june july september october november december
            english hindi french spanish german chinese japanese korean arabic russian italian urdu punjabi tamil
            india indian america american europe european asia asian africa african australia canada london paris delhi mumbai
            google youtube whatsapp instagram facebook twitter tiktok snapchat linkedin netflix spotify amazon microsoft android
            christmas easter diwali eid ramadan holi
        """.trim().split(Regex("\\s+"))
        for (w in plain) put(w, w.replaceFirstChar { it.uppercaseChar() })
        for (w in listOf("uk", "usa", "tv", "ceo", "gps", "pdf", "sms", "usb", "atm", "faq", "fyi", "asap", "diy", "ai")) put(w, w.uppercase())
        put("iphone", "iPhone"); put("ipad", "iPad"); put("ios", "iOS"); put("macbook", "MacBook")
    }

    fun proper(lower: String): String? = MAP[lower]
}
