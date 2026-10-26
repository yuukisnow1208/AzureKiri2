# AzureKiri2

[简体中文](readme.md) | **English**

AzureKiri2 is a KiriKiri2 visual-novel game **emulator** for Android, rebuilt from the ground up with a modern Material 3 interface.

> This project originates from [zeas2/Kirikiroid2](https://github.com/zeas2/Kirikiroid2) and its [Yuri branch (YuriSizuku/Kirikiroid2Yuri)](https://github.com/YuriSizuku/Kirikiroid2Yuri) — full credit to the original authors. The legacy UI had gone untouched for years, so this project replaces it entirely with a modern Material 3 design while keeping solid compatibility with KiriKiri2 games.

## Features

### Game Library
- **Steam-style library home**: a cover grid where every card shows its cover art, game title, total playtime and a "last played" badge (Today / Yesterday / MMM d)
- **Sorting**: by last played (default) / alphabetically (Chinese titles by pinyin) / by total playtime
- **Playtime tracking**: fully automatic — paused when you leave the app or the screen turns off, resumed when you return, and settled correctly when switching between games
- **Per-game naming**: every KiriKiri game launches from a file called `data.xp3` — name your games on first launch (defaults to the game's folder name) and rename them any time
- **Custom covers**: pick a cover from the game's screenshot gallery or from your phone's photo library

### Screenshot System
- **In-game screenshots**: one tap in the floating sidebar menu captures the game view into that game's own gallery (the menu itself never appears in shots)
- **System screenshot link**: system screenshots taken while playing (e.g. a three-finger swipe) are automatically filed into the running game's gallery
- **Gallery page**: a dedicated page — filter by game, view full-size, delete

### Interface & Engine
- **All-new Material 3 UI**: dynamic color, dark mode, drawer navigation; wallpaper-based theming on Android 12+
- **In-game floating menu**: modern in-game menu (game menu, window manager, mouse/touch mode, IME, screenshot, exit); the classic menu style remains available in settings
- **Per-game settings**: rendering, fonts and more can be overridden for individual games
- **XP3 repack/decryption**: convert encrypted XP3 archives for faster launches and less heat
- **Rich engine settings**: renderer selection, FPS cap, memory limits, texture compression, OpenGL extension toggles, default font and more
- **Scoped-storage ready**: adapted to modern Android storage behavior

## Requirements

- Android 7.0 (API 24) or later
- ARM64 device

## Installation

Grab the latest APK from the [Releases](https://github.com/yuukisnow1208/AzureKiri2/releases) page and install it.

## Getting Started

1. **First launch**: grant "All files access" when prompted (the emulator needs to read game files from storage)
2. **Add a game**: browse to a game folder or a `.xp3` file in "Browse files" and tap to launch; opening a game file from a system file manager works too. A naming dialog appears on first launch (pre-filled with the game's folder name)
3. **Library**: the home page is your library — tap a card to play, long-press to rename, change the cover, open the gallery or per-game settings
4. **Screenshots**: in game, open the right-side floating menu and tap "Screenshot", or simply use your system screenshot gesture — shots are filed by game automatically
5. **Change a cover**: long-press a game card → "Change cover" → pick from the gallery or your photo library
6. **Repack/decrypt** (optional): run it from the drawer on a game directory for faster loads
7. **Personalize**: adjust appearance, rendering and fonts in "Settings"; per-game overrides live behind a long-press on the game card

## FAQ

**My game isn't recognized?**
A game must be a complete folder (containing `startup.tjs` or a `data.xp3` archive) or a single `.xp3` file. Some games need an `xp3filter.tjs` decryption script placed in the game folder.

**Can it run encrypted games?**
Yes. Common decryption schemes are built in; the "Repack/decrypt XP3" feature can also permanently decrypt an archive.

**The card shows the wrong name?**
Every game launches from a file named `data.xp3`, so the emulator shows the game's folder name by default — long-press the card → "Rename" to set whatever you like.

**How do I set a cover image?**
Take a few in-game screenshots first, then long-press the game card → "Change cover" → pick from the gallery; your phone's photo library works too.

**Fonts look wrong?**
Set a custom font file (`.ttf` / `.otf`) in settings, or enable "Force default font".

## Building from Source

```bash
git clone https://github.com/yuukisnow1208/AzureKiri2.git
cd AzureKiri2/project/android
./gradlew assembleRelease --offline
```

- JDK 17 and the Android SDK (NDK 25) are required; the CMake build directory is redirected to `build_android/` at the repo root
- Signing is configured via `project/android/app/sign.properties` (point it at your own keystore)
- The port layer (cocos2d-x) comes from [yuukisnow1208/port-cocos2d-x](https://github.com/yuukisnow1208/port-cocos2d-x) and is fetched automatically by CI

## License

This project inherits the upstream projects' open-source licenses. KiriKiri2 engine copyrights belong to their respective owners. Please only run games you legally own.
