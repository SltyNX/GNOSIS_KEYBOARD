package gnosis.keyboard.engine.spell

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object ContractionEngine {

    private val BASE_CONTRACTION_MAP: Map<String, List<String>> = mapOf(
        "arent" to listOf("aren't"),
        "isnt" to listOf("isn't"),
        "wasnt" to listOf("wasn't"),
        "werent" to listOf("weren't"),
        "dont" to listOf("don't"),
        "doesnt" to listOf("doesn't"),
        "didnt" to listOf("didn't"),
        "cant" to listOf("can't", "cannot"),
        "cannot" to listOf("can't"),
        "couldnt" to listOf("couldn't"),
        "wont" to listOf("won't"),
        "wouldnt" to listOf("wouldn't"),
        "shouldnt" to listOf("shouldn't"),
        "havent" to listOf("haven't"),
        "hasnt" to listOf("hasn't"),
        "hadnt" to listOf("hadn't"),
        "mustnt" to listOf("mustn't"),
        "neednt" to listOf("needn't"),
        "mightnt" to listOf("mightn't"),
        "shant" to listOf("shan't"),
        "oughtnt" to listOf("oughtn't"),
        "maynt" to listOf("mayn't"),
        "darent" to listOf("daren't"),
        "aint" to listOf("ain't"),
        "amnt" to listOf("amn't"),

        "im" to listOf("I'm"),
        "ive" to listOf("I've"),
        "ill" to listOf("I'll", "ill"),
        "id" to listOf("I'd"),
        "youre" to listOf("you're", "your"),
        "youve" to listOf("you've"),
        "youll" to listOf("you'll"),
        "youd" to listOf("you'd"),
        "hes" to listOf("he's", "he"),
        "heve" to listOf("he've"),
        "hell" to listOf("he'll", "hell"),
        "hed" to listOf("he'd"),
        "shes" to listOf("she's", "she"),
        "sheve" to listOf("she've"),
        "shell" to listOf("she'll", "shell"),
        "shed" to listOf("she'd", "shed"),
        "its" to listOf("it's", "its"),
        "itll" to listOf("it'll"),
        "itd" to listOf("it'd"),
        "weve" to listOf("we've"),
        "well" to listOf("we'll", "well"),
        "were" to listOf("we're", "were"),
        "wed" to listOf("we'd", "wed"),
        "theyre" to listOf("they're", "there", "their"),
        "theyd" to listOf("they'd"),
        "theyll" to listOf("they'll"),
        "theyve" to listOf("they've"),

        "thats" to listOf("that's", "that"),
        "thatll" to listOf("that'll"),
        "thatd" to listOf("that'd"),
        "theres" to listOf("there's", "there"),
        "therell" to listOf("there'll"),
        "thered" to listOf("there'd"),
        "heres" to listOf("here's", "here"),
        "herell" to listOf("here'll"),
        "hered" to listOf("here'd"),
        "wheres" to listOf("where's"),
        "wherell" to listOf("where'll"),
        "whered" to listOf("where'd"),
        "whereve" to listOf("where've"),
        "hows" to listOf("how's"),
        "howll" to listOf("how'll"),
        "howd" to listOf("how'd"),
        "howre" to listOf("how're"),
        "howve" to listOf("how've"),
        "whos" to listOf("who's", "whose"),
        "wholl" to listOf("who'll"),
        "whod" to listOf("who'd"),
        "whore" to listOf("who're"),
        "whove" to listOf("who've"),
        "whats" to listOf("what's", "what"),
        "whatll" to listOf("what'll"),
        "whatd" to listOf("what'd"),
        "whatre" to listOf("what're"),
        "whatve" to listOf("what've"),
        "whens" to listOf("when's"),
        "whenll" to listOf("when'll"),
        "whend" to listOf("when'd"),
        "whenve" to listOf("when've"),
        "whys" to listOf("why's"),
        "whyll" to listOf("why'll"),
        "whyd" to listOf("why'd"),
        "whyre" to listOf("why're"),
        "whichs" to listOf("which's"),
        "whomll" to listOf("whom'll"),
        "whomd" to listOf("whom'd"),

        "wouldve" to listOf("would've"),
        "couldve" to listOf("could've"),
        "shouldve" to listOf("should've"),
        "mustve" to listOf("must've"),
        "mightve" to listOf("might've"),
        "mayve" to listOf("may've"),
        "coulda" to listOf("could've"),
        "shoulda" to listOf("should've"),
        "woulda" to listOf("would've"),
        "mighta" to listOf("might've"),
        "musta" to listOf("must've"),
        "couldntve" to listOf("couldn't've"),
        "shouldntve" to listOf("shouldn't've"),
        "wouldntve" to listOf("wouldn't've"),
        "mightntve" to listOf("mightn't've"),

        "yall" to listOf("y'all"),
        "yalld" to listOf("y'all'd"),
        "yallll" to listOf("y'all'll"),
        "yallre" to listOf("y'all're"),
        "yallve" to listOf("y'all've"),
        "yalls" to listOf("y'all's"),
        "maam" to listOf("ma'am"),
        "oclock" to listOf("o'clock"),
        "cmon" to listOf("c'mon"),
        "lets" to listOf("let's", "lets"),
        "tis" to listOf("'tis"),
        "twas" to listOf("'twas"),
        "twould" to listOf("'twould"),
        "neer" to listOf("ne'er"),
        "oer" to listOf("o'er"),
        "ol" to listOf("ol'"),
        "cause" to listOf("'cause", "cause"),
        "cos" to listOf("'cause"),
        "coz" to listOf("'cause"),
        "em" to listOf("'em"),

        "someones" to listOf("someone's"),
        "someonell" to listOf("someone'll"),
        "someoned" to listOf("someone'd"),
        "somebodys" to listOf("somebody's"),
        "somebodyll" to listOf("somebody'll"),
        "somebodyd" to listOf("somebody'd"),
        "somethings" to listOf("something's"),
        "somethingll" to listOf("something'll"),
        "somethingd" to listOf("something'd"),
        "everyones" to listOf("everyone's"),
        "everyonell" to listOf("everyone'll"),
        "everyoned" to listOf("everyone'd"),
        "everybodys" to listOf("everybody's"),
        "everybodyll" to listOf("everybody'll"),
        "everybodyd" to listOf("everybody'd"),
        "everythings" to listOf("everything's"),
        "everythingll" to listOf("everything'll"),
        "everythingd" to listOf("everything'd"),
        "anyones" to listOf("anyone's"),
        "anyonell" to listOf("anyone'll"),
        "anyoned" to listOf("anyone'd"),
        "anybodys" to listOf("anybody's"),
        "anybodyll" to listOf("anybody'll"),
        "anybodyd" to listOf("anybody'd"),
        "anythings" to listOf("anything's"),
        "anythingll" to listOf("anything'll"),
        "anythingd" to listOf("anything'd"),
        "nobodys" to listOf("nobody's"),
        "nobodyll" to listOf("nobody'll"),
        "nobodyd" to listOf("nobody'd"),
        "nothings" to listOf("nothing's"),
        "nothingll" to listOf("nothing'll"),
        "nothingd" to listOf("nothing'd"),
        "ones" to listOf("one's"),
        "others" to listOf("other's"),

        "johns" to listOf("John's"),
        "marys" to listOf("Mary's"),
        "childrens" to listOf("children's"),
        "mens" to listOf("men's"),
        "womens" to listOf("women's"),
        "peoples" to listOf("people's"),
        "todays" to listOf("today's"),
        "tomorrows" to listOf("tomorrow's"),
        "yesterdays" to listOf("yesterday's"),
        "weeks" to listOf("week's"),
        "months" to listOf("month's"),
        "years" to listOf("year's"),
        "mondays" to listOf("Monday's"),
        "tuesdays" to listOf("Tuesday's"),
        "wednesdays" to listOf("Wednesday's"),
        "thursdays" to listOf("Thursday's"),
        "fridays" to listOf("Friday's"),
        "saturdays" to listOf("Saturday's"),
        "sundays" to listOf("Sunday's"),
        "worlds" to listOf("world's"),
        "lifes" to listOf("life's"),
        "moms" to listOf("mom's"),
        "dads" to listOf("dad's"),
        "friends" to listOf("friend's"),

        "dontt" to listOf("don't"),
        "cantt" to listOf("can't"),
        "woudnt" to listOf("wouldn't"),
        "isntt" to listOf("isn't"),
        "didntt" to listOf("didn't")
    )

    val AMBIGUOUS_CONTRACTIONS: Set<String> = setOf(
        "were", "well", "hell", "shed", "wed", "shell", "ill", "its"
    )

    fun isAmbiguous(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT).replace("'", "")
        return AMBIGUOUS_CONTRACTIONS.contains(lower)
    }

    private val dynamicContractions: MutableMap<String, List<String>> = HashMap(BASE_CONTRACTION_MAP)

    val CONTRACTION_MAP: Map<String, List<String>>
        get() = dynamicContractions

    @Synchronized
    fun init(context: Context) {
        val assetName = try {
            context.assets.open("gnosis_apostrophe_contractions.txt").close()
            "gnosis_apostrophe_contractions.txt"
        } catch (_: Exception) {
            "gboard_style_apostrophe_contractions.txt"
        }
        try {
            context.assets.open(assetName).use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val parts = trimmed.split("\t")
                        if (parts.size >= 2) {
                            val typed = parts[0].trim().lowercase(Locale.ROOT)
                            val suggested = parts[1].trim()
                            if (typed.isNotEmpty() && suggested.isNotEmpty()) {
                                val current = dynamicContractions[typed] ?: emptyList()
                                if (!current.contains(suggested)) {
                                    dynamicContractions[typed] = listOf(suggested) + current
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getContractionCandidates(input: String): List<String>? {
        if (input.isEmpty()) return null
        val lower = input.lowercase(Locale.ROOT)
        val clean = lower.replace("'", "")
        return dynamicContractions[clean] ?: dynamicContractions[lower]
    }

    fun getContraction(input: String): String? {
        return getContractionCandidates(input)?.firstOrNull()
    }

    fun isKnownContraction(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT)
        val clean = lower.replace("'", "")
        return dynamicContractions.containsKey(clean) || dynamicContractions.containsKey(lower)
    }

    fun matchCasing(original: String, target: String): String {
        if (original.isEmpty() || target.isEmpty()) return target
        val isFirstUpper = original[0].isUpperCase()
        val isAllUpper = original.length > 1 && original.all { it.isUpperCase() || !it.isLetter() }

        return when {
            isAllUpper -> target.uppercase(Locale.ROOT)
            isFirstUpper -> target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            target.startsWith("i'") || target == "i" -> target.replaceFirstChar { it.uppercaseChar() }
            else -> target
        }
    }
}
