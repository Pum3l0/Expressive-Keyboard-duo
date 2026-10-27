# Expressive Keyboard Duo 📱✨

An advanced Android keyboard in the Material 3 Expressive style, specialized for **foldable devices (Google Pixel Fold)**, tablets, and wide screens, built with Kotlin and Jetpack Compose.

<!-- Expressive Keyboard Duo Banner / Screenshots -->
<p align="center">
  <img src="docs/banner.png" alt="Expressive Keyboard Duo" width="600">
</p>

> [!NOTE]
> **Foldable Optimization**: Tested and verified on **Google Pixel Fold** (Android 14 / 15 / 16).
> Features a two-pane split settings layout, adaptive posture profiles, and ergonomic split key layouts.

---

## 🚀 Key Duo Features (Foldable & Large Screens)

- 📱 **Adaptive Posture Profiles (`FoldPosture`)**: Automatically calculates and applies optimal key heights, side padding, and gap spacing for 4 distinct device states:
  - *Unfolded Landscape* (Inner wide screen)
  - *Unfolded Portrait* (Inner tall screen)
  - *Folded Portrait* (Outer screen)
  - *Folded Landscape* (Outer screen)
- 🔀 **Split Keyboard Modes**:
  - **Standard Split**: Cleanly divides key clusters with customizable central gap (`16dp..280dp`).
  - **Duplicate Center Keys (Gboard-style)**: Duplicates central letters (`г`/`н`, `р`/`о`, `и`/`т` in ЙЦУКЕН) so both thumbs easily reach them.
- 📐 **Ergonomic Staggered Layout**: Curved row offsets following the natural arc of human thumbs.
- 🦿 **Fold Crease Guide & Central Gap Widgets**:
  - *Crease Guide*: Customizable visual fold line with **Glow**, **Line**, **Dots**, or **Gradient** styles and opacity controls.
  - *NumPad*: Compact central numeric keypad (`1 2 3`, `4 5 6`, `7 8 9`, `0`).
  - *Symbols & Navigation*: Quick symbol grid or cursor arrow pads directly in the central gap.
- ⌨️ **Full-Width or Split Spacebar Concepts**: Option between dual split spacebars or a single continuous full-width spacebar across the central bottom row.
- 🎯 **Expanded Touch Targets (Zero Dead Touch Gaps)**: 100% active touch surface across grid cells so large thumbs never miss key taps.
- 📚 **Gboard Personal Dictionary Import & Export**: Import and export `.txt` / `.tsv` dictionary files compatible with Gboard.
- 🖼️ **Adaptive Two-Pane Settings Screen**: Master-detail sidebar layout on unfolded inner screens (`screenWidthDp >= 600`).

---

## 🌟 Core Keyboard Features

- **Typing**: Next-word prediction, auto-correction, glide (swipe) typing, local self-learning dictionary.
- **Languages**: 90+ layouts including Russian, English, Chinese (Pinyin), Japanese (Romaji → Kana), Indic scripts, and Telex.
- **Emoji**: Unicode 16 emoji, dual English/Russian search, skin tones, built-in styles (Twemoji, Noto, Blob, OpenMoji).
- **Designs**: Material You Expressive, Material 3, Liquid Glass, Classic, Neon, Neumorphism — one tap preset switching.
- **Panels**: Emoji, GIFs, clipboard manager, translator, voice input.
- **Customization**: Material You colors, custom fonts (`.ttf` / `.otf`), background images, adjustable vibration amplitude & duration.
- **Plugins & Mods**: Extensible JavaScript plugin system for custom buttons, shortcuts, layouts, and floating widgets.

---

## 📥 Installation

1. Download the latest APK from [Releases](https://github.com/kotlalipopus/Expressive-Keyboard/releases/latest).
2. Install the APK on your Android device (Android 8.0+).
3. Open **Expressive Keyboard Duo** and enable it in System Settings.
4. Select Expressive Keyboard Duo as your active input method.

---

## 🛠️ Building from Source

Requirements: Android Studio (or JDK 17 and Android SDK 35).

```bash
git clone https://github.com/kotlalipopus/Expressive-Keyboard.git
cd Expressive-Keyboard
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📜 License

Distributed under the [MIT License](LICENSE).
