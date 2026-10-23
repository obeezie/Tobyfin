# Tobyfin

Tobyfin is a customized build of the [Jellyfin](https://jellyfin.org) Android phone and Android TV apps. It connects to a normal Jellyfin server and installs alongside the stock Jellyfin apps.

## What's in this repository

| Folder | What it is | Upstream |
|---|---|---|
| `android-tv/` | The Android TV / NVIDIA Shield app | [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) |
| `android/` | The Android phone app: a native shell around the web client | [jellyfin-android](https://github.com/jellyfin/jellyfin-android) |
| `web/` | The web client the phone app displays (Home, Discovery, playback screens) | [jellyfin-web](https://github.com/jellyfin/jellyfin-web) |

The phone app bundles a built copy of `web/` in `android/app/src/main/assets/web`. After changing `web/`, rebuild it and copy `web/dist` there before building the phone app.

## Features

- Tobyfin branding throughout the apps
- Genre rows on the home screen
- Customize Discovery: reorder, hide or add home rows and choose poster size
- Recently Added TV grouped by show
- Sleep timer
- Live subtitle timing adjustment
- "Who's watching?" profile picker on Android TV
- Spotlight layout on Android TV: a per-profile Home layout that anchors the selected row to the bottom edge and shows the highlighted title above it

Release notes for every Phone and Shield version, with what was tested and what is still pending, are in [TOBYFIN.md](TOBYFIN.md).

## Building

- **Web:** `cd web && npm ci && npm run build:production`
- **Phone:** copy `web/dist` into `android/app/src/main/assets/web`, then `cd android && ./gradlew assembleProprietaryDebug`
- **Android TV:** `cd android-tv && ./gradlew assembleDebug`

The Tobyfin build type keeps the `.debug` application id suffix so it installs next to stock Jellyfin.

## License

Each folder keeps its upstream license: GPL-2.0 for all three Jellyfin projects. See the `LICENSE` file in each folder.
