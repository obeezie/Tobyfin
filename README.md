# Tobyfin

Tobyfin is a customized build of the [Jellyfin](https://jellyfin.org) Android phone and Android TV apps. It connects to a normal Jellyfin server and installs alongside the stock Jellyfin apps.

## What's in this repository

| Folder | What it is | Upstream |
|---|---|---|
| `android-tv/` | The Android TV / NVIDIA Shield app | [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) |
| `android/` | The Android phone app: a native shell around the web client | [jellyfin-android](https://github.com/jellyfin/jellyfin-android) |
| `web/` | The web client the phone app displays (Home, Discovery, playback screens) | [jellyfin-web](https://github.com/jellyfin/jellyfin-web) |

The phone app bundles a built copy of `web/` in `android/app/src/main/assets/web`. After changing `web/`, rebuild it and copy `web/dist` there before building the phone app.

## What Tobyfin adds

**Both apps**
- Tobyfin branding: name, dog logo, launcher artwork, and a buffering animation.
- Genre rows on Home (Action, Comedy, Horror and more), plus **Customize Discovery** to reorder, hide or add rows and choose poster size.
- Recently Added TV shows each show once, newest episode first.
- Home loads in screen order without rows jumping, and returns to the same place after you open a movie.
- Continue Watching opens instantly from a saved copy, then refreshes.
- A sleep timer, and live subtitle timing adjustments during playback.

**Android TV**
- A Netflix-style **"Who's watching?"** profile picker on launch and after 30 minutes away.
- The signed-in person's initial on Home's profile button.
- Posters scroll behind the Home header.
- Built as an optimized (non-debuggable, R8) app, so navigation is as smooth as stock Jellyfin.

**Phone**
- Text fields stay above the on-screen keyboard.

## Building

- **Web:** `cd web && npm ci && npm run build:production`
- **Phone:** copy `web/dist` into `android/app/src/main/assets/web`, then `cd android && ./gradlew assembleProprietaryDebug`
- **Android TV:** `cd android-tv && ./gradlew assembleDebug`

The Tobyfin build type keeps the `.debug` application id suffix so it installs next to stock Jellyfin.

## License

Each folder keeps its upstream license: GPL-2.0 for all three Jellyfin projects. See the `LICENSE` file in each folder.
