# Gnosis Keyboard (Gnosis_keyboard)

A modern, high-performance, fully native Android Keyboard (IME) written in Kotlin. Features the sleek Omnitype keyboard layout and theme presets, a fully offline typing engine (suggestions, spell-check, grammar, glide typing), a floating keyboard, clean text-only emoji navigation, Lucide vector icons, rich image clipboard with `commitContent`, low-latency sound effects and haptic feedback, custom background photos with interactive crop/dimming, and an accordion-style Settings UI.

- **App & IME Name:** `GNOSIS KEYBOARD` (project name `Gnosis_keyboard`)
- **Package:** `gnosis.keyboard`
- **Min SDK:** 26 (Android 8.0+), **Target SDK:** 36, **Compile SDK:** 37
- **Version Configuration:** [`placeholder.txt`](placeholder.txt) (the single source of truth for version name, version code and the release flag)

---

## Key Features & Highlights

### 1. Omnitype Layouts & Key Aesthetics
- **Layout Styles:** *Revamped* (docking toolbar above the keys) or *Classics* (suggestion strip with a toolbox button). Switching restarts the keyboard automatically.
- **Layouts:** QWERTY, QWERTZ, AZERTY, and Devanagari (Hindi native).
- **Key Styling:** Rounded rectangular keys, smooth scale press states, optional key borders (off by default), adjustable keyboard height, and a Number Row (`1234567890`) that is on by default.
- **Pages:** Letters, Numbers (`?123`), Symbols (`=\<`), Emoji, Clipboard, and Text Editing.
- **Shift Engine:** Lowercase -> Uppercase (one shot) -> Caps Lock (double tap).
- **Dynamic Enter Key:** Automatically adapts icon and action based on target editor (Search, Send, Go, Done, Return).
- **One-Handed Mode:** Cycles Off -> Right -> Left from the toolbar, with a side bar on the free edge.

### 2. Bug Fixes (Stuck Popups & Emoji Crashes Resolved)
- **Zero Stuck Popups:** Replaced shared singleton runnable with per-key touch cancellation and synchronized dismissals on touch up/cancel/outside. Popups are cleanly dismissed on layer switches, keyboard hiding, and window detachment.
- **Crash-Free Emoji Grid:** Replaced the legacy 1,000-view delayed layout and unmeasured `anchor.top` scrolling with an instant, on-demand categorized grid.
- **Text-Only Emoji Selector Bar:** Category tabs display clean text pills (**Recent**, **Smileys**, **Animals**, **Food**, **Activities**, **Travel**, **Objects**, **Symbols**) with zero emoji icons in the tab bar.

### 3. Typing, Prediction & Grammar (100% offline)
All typing intelligence runs on the device (`engine/`). Nothing you type is sent anywhere.
- **Suggestions:** Prefix completions from a built-in English dictionary, plus next-word prediction from a compact n-gram model.
- **Spell-check & auto-correction:** Edit-distance and keyboard-proximity (QWERTY distance) matching with a typo dictionary. Auto-correction on space is **off by default**.
- **Grammar & apostrophes:** Offline grammar and contraction fixes (`arent` -> `aren't`). Uses the optional native Harper library if present, otherwise the built-in `OfflineGrammarEngine`.
- **Hinglish:** Built-in Hinglish word list, next-word pairs and common chat-typo expansions.
- **Personalized dictionary:** Learns the words you type locally. *Clear Learned Words* wipes it.
- **Glide typing:** Swipe across letters to form words.
- **Gesture delete:** Swipe left on backspace to delete word by word.
- **Spacebar cursor control:** Slide your finger on space to move the cursor.
- **Double-space period:** Double tap on space inserts `. `.

### 4. Toolbox, Quick Keys & Floating Keyboard
- **Toolbox:** Voice dictation, Clipboard, Text Editing, Floating keyboard and Settings buttons, all one size.
- **Quick Keys:** Up to 3 keys beside the suggestions (defaults `.` `,` `?`), each up to 3 characters (an emoji counts as one). Edit them in Settings or hold one on the keyboard to replace it with anything typed or pasted.
- **Floating Keyboard:** Drag it anywhere on screen with no extra permission. Resize it with the handles on the card's edges (width and height 60-100%).
- **Voice Dictation:** Uses Android's speech recognizer; asks for the microphone permission the first time.
- **Text Editing Panel:** Select All, Copy, Cut and Paste actions plus cursor controls.

### 5. Clipboard with Text and Screenshots
The clipboard has two sections, each with its own tick box (in the clipboard panel and in Settings) and its own **Pinned** and **Recent** lists (up to 30 unpinned items per section).
- **Text** (on by default): persistent history of copied text with pin/unpin and delete.
- **Screenshots** (off by default): screenshots you take are saved here automatically. Turning it on asks for photo access; only screenshots taken after you turn it on are imported.
- **Direct App Insertion:** tap any image or screenshot to insert it into compatible apps (WhatsApp, Telegram, Discord, Messages) using `InputConnectionCompat.commitContent` and Android `FileProvider`.
- **Safe clearing:** *Clear All* in the clipboard (current section), *Clear Clipboard History*, *Clear Learned Words* and *Reset All Settings* all ask for confirmation first. Pinned clips are never removed by clearing.

### 6. Sound & Haptics
- **Key sounds:** Default click, Piano (every key plays its own note, rising left to right) or Drum, with adjustable volume. On by default.
- **Haptic feedback:** Adjustable vibration intensity. Off by default; turning it on warns you if the device has vibration disabled or unsupported.

### 7. Lucide Vector Icons
- All keyboard and toolbar icons are crisp Android VectorDrawables converted from Lucide SVG icons (`Delete`, `ArrowUp`, `ChevronsUp`, `CornerDownLeft`, `Mic`, `Smile`, `Clipboard`, `Type`, `Settings`, `Globe`, `Pin`, `Trash2`, etc.).

### 8. Themes & Appearance Engine
- **Presets:** Onyx, Graphite, Midnight, Ocean, Ember, Rose, Daylight, and High Contrast.
- **Accent Palette:** Electric Blue (`#7C9CFF`), Cyan, Emerald Green, Sunset Coral, Neon Purple, Hot Pink, Amber Gold, Crimson Red, Pure White, and Midnight Black.
- **Contrast Guard:** Automatically prevents dark-on-dark and light-on-light illegibility.
- **Custom Photo Background:** Choose any image from your gallery, adjust pinch-zoom and crop center with live preview, and configure dimming (0% to 80%, default 35%).
- **Emoji Font:** System default or an iOS-style emoji font bundled with the app.

### 9. Revamped Settings UI
- Accordion cards matching Omnitype UI with real-time live preview, modern sliding toggle switches, custom sliders, and theme swatches. Sections: **Keyboard Layout Engine**, **Languages & Layouts**, **Quick Keys**, **Preferences**, **Theme & Appearance**, **Text Correction**, **Glide Typing**, **Clipboard**, **Emojis**, and **Privacy & Reset**.
- Changes apply to the keyboard live, without closing and reopening it.

---

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Voice dictation |
| `INTERNET` | Needed by Android's speech recognizer; the typing engine itself is offline |
| `VIBRATE` | Haptic feedback |
| `READ_MEDIA_IMAGES` (and `READ_EXTERNAL_STORAGE` up to Android 12L) | Optional Clipboard > Screenshots import |

---

#### Signing keys (GitHub Secrets)

The CI build is signed with your own keystore, stored as repository secrets, on every branch. If `KEY_STORE` is missing the build still succeeds but the APK is unsigned.

| Secret | Value |
|---|---|
| `KEY_STORE` | Your keystore (`.jks`), base64-encoded |
| `KEY_STORE_PASSWORD` | The keystore password |
| `ALIAS` | The key alias inside the keystore |
| `KEY_PASSWORD` | The key password (falls back to the store password if empty) |
| `TELEGRAM_BOT_TOKEN` | *Optional.* Bot token used to post each finished build to Telegram |
| `TELEGRAM_CHAT_ID` | *Optional.* Chat or channel ID the bot posts to. Keep the leading minus sign: groups, supergroups and channels use a negative ID such as `-1001234567890` |

If either Telegram secret is missing, the Telegram step is skipped and the build still succeeds.

## Installation & Setup

1. Build and install the APK (or run via Android Studio / Gradle).
2. Open the **GNOSIS KEYBOARD** app.
3. Tap **1. Enable in Settings** and toggle **GNOSIS KEYBOARD** on.
4. Tap **2. Select Keyboard** and choose **GNOSIS KEYBOARD** as your active IME.

---