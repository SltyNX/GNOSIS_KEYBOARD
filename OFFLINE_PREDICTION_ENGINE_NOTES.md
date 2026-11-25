# Offline prediction engine improvements

This patch changes the general algorithms rather than adding a special-case mapping for one example.

## What changed
- Fixed the trie-based Damerau-style adjacent-transposition condition.
- Increased the bounded fuzzy-trie search budget from 600 to 5,000 visited nodes and stopped terminating merely because a small number of early candidates were found.
- Uses a length-sensitive fuzzy search radius: stricter for short words and broader for longer words, including multiple independent edits in a longer token.
- Incorporates normalized edit similarity into fuzzy candidate ranking alongside keyboard spatial similarity, language-model context and word frequency.
- Prevents fuzzy alternatives from outranking an exact dictionary word or autocorrecting away from a correctly typed dictionary word.
- Adds generic fuzzy matching against up to 500 locally learned words, so personal names, slang and custom terms can be corrected after the user has learned them.
- Adds regression tests for multiple unrelated transposition cases and for a word containing two transpositions.

## Offline and vocabulary behavior
The prediction path uses the bundled local dictionary/ngram assets and the on-device user lexicon. No network requests were added.

No finite dictionary-based engine can reliably correct every arbitrary string into every possible word. The target must be in the bundled dictionary, or in the user's learned vocabulary; otherwise the engine has no reliable candidate to rank. Broader language coverage requires a larger licensed offline lexicon and better language-model data, not word-specific hardcoding.

## Limitations and testing
This remains a local dictionary + n-gram + fuzzy decoder, not Google's proprietary Gboard model. The ZIP could not be compiled in this environment because Gradle is not installed here. Run the unit tests and build in Android Studio/Gradle before installing, then benchmark per-keystroke latency on the target phone.


## Expanded offline vocabulary (2026-10)

- Added `app/src/main/assets/words_en_extended.txt`, a filtered 117,363-word vocabulary extracted from the locally available CMU pronunciation dictionary (`/usr/share/pocketsphinx/model/en-us/cmudict-en-us.dict`). The asset contains alphabetic words only (2–30 letters), lowercased and deduplicated. The original frequency-ordered `words_en.txt` is preserved and loaded first.
- `EngineFactory` loads this extended asset offline when initializing the trie. This broadens coverage for uncommon vocabulary and many names/technical terms, but it is not a literal list of every English word, username, product name, or newly coined term.
- The personal offline lexicon now makes a committed word eligible for personalization after its first use and retains up to 5,000 learned words instead of 500. This helps unknown names and terms become available without network access.
- The CMU pronunciation dictionary is a third-party vocabulary source; retain its upstream attribution/license notices when redistributing the derived word list.


## Frequency-prior correction (2026-10)

- Fixed `EngineFactory` so the alphabetically ordered extended vocabulary is no longer assigned artificial descending frequencies from insertion order.
- The existing primary word list retains its relative order as a frequency prior; words sourced only from supplemental lists receive a neutral prior until a licensed frequency list is supplied.
- Added unit tests for primary ordering, neutral supplemental priors, and duplicate handling.
- This fixes a ranking-data defect, but it does not replace a real frequency corpus. Equal-prior supplemental words may still need contextual reranking or an external licensed frequency pack to rank well among themselves.
