# Jellyfin Genres for Android phones

This build bundles the modified Jellyfin web frontend inside the official Android mobile client. It includes thirteen genre shelves, mixed movie batches with continued loading, Recently Added paging that includes watched movies, genre-only discovery, and the web version's append and refresh fixes.

The frontend is stored in `app/src/main/assets/web`. `BundledWebFrontend` maps only the selected server's `/web/` directory (including a reverse proxy base path) to these APK assets. The WebView keeps that server's origin. Authentication, API requests, images, media streams, and WebSocket connections continue to reach the Jellyfin server. Other origins and paths are not mapped to bundled frontend files. Native script injection and the mobile playback bridges remain in the existing WebView client. A GET request for the main bundle receives the native bootstrap; its deferred request receives the bundled web script.

The app opens the bundled `web/index.html` directly rather than requesting the server's root page. Users enter their normal Jellyfin server URL in the mobile connection screen. No development frontend at `localhost:8080` or running PC is required to serve the interface. The Jellyfin server itself must remain reachable.

The debug app is named **Jellyfin Genres** and uses `org.jellyfin.mobile.debug`, so it installs alongside the regular `org.jellyfin.mobile` app. The proprietary variant retains the standard app's Chromecast integration. Existing native and external player implementations are retained.

## Build

Use Java 21, the supplied Gradle 9.8.0 wrapper, and Android SDK platform 37. On this PC, the portable tools are under `F:\Jellyfin\android-build-tools`. The supplied project's dependency versions are retained.

Build and run the focused routing tests:

```text
gradlew.bat :app:testProprietaryDebugUnitTest :app:assembleProprietaryDebug
```

The Kotlin test task uses JUnit Platform for the project's Kotest/JUnit 5 dependencies. Debug resource generation is enabled for the separate app label.

To refresh the bundled interface, rebuild the modified `F:\Jellyfin\jellyfin-web-master` project and replace the contents of `app/src/main/assets/web` with its `dist` output, then rebuild the APK. Web source is separately available in `F:\Jellyfin\jellyfin-web-discovery-source.zip`. Preserve bundled third-party license files.

## Install

The current APK is `F:\Jellyfin\jellyfin-android-phone-genres-v5.apk` (also available at `F:\Jellyfin\jellyfin-android-phone-genres.apk`). Copy it to your Android phone's Downloads folder, open it, and grant the file manager permission to install unknown apps when prompted. Version code 5 has the same package and signing certificate as the previous versions, so it installs as an update and retains existing app data. Close and reopen Jellyfin Genres after installing. New installations use your existing Jellyfin server URL, rather than the PC frontend's `localhost:8080` address.

## Validation

The bundled main script matches the working web build by SHA-256. All 35 entry-point asset references resolve, and the native main-bundle interception pattern matches `main.jellyfin.bundle.js`. All 2,353 frontend files in the finished APK were checked against the source assets by SHA-256. Android's underscore-folder exclusion is disabled so the shared `themes/_base` stylesheet is packaged.

On October 4, 2026, the proprietary debug APK build and all five focused routing tests passed. Tests cover the selected server origin, reverse-proxy base paths, encoded filenames, default ports, API/media pass-through, and rejection of other origins and traversal paths. APK v1/v2 signature verification passed. Manifest inspection confirmed the separate package, Jellyfin Genres label, phone launcher activity, minimum API 23, and ARM 32-bit/64-bit support (plus emulator ABIs).

## Playback correction, October 6, 2026

The version 1 WebView request filter returned early for non-GET requests. The bundled web client reports capabilities using POST immediately after authentication, so native account setup never ran even though web login succeeded. The native player uses a separate API client and therefore lacked the web login's access token. The filter now observes the selected server's POST capability report before applying the GET-only asset rule, and lets the original request continue to the network unchanged. Native login parses WebView's JSON-encoded storage string, chooses the matching saved server, and handles missing/malformed credentials or setup failures without closing the working web session.

The upstream queue initializer also discarded the result of `startRemotePlayback` and returned success after a streaming failure. It now propagates the result to `PlayerFragment`'s existing playback-error handling.

A regression harness using frozen runtime classes from version 1 reproduced both failures: `MainViewModel.setupUser` was never called for the POST handoff, and a `NetworkFailure` from the stream resolver became a success (`null`) at the UI boundary. The same two production-path tests pass with the correction. The complete 13-test suite and APK build passed. All 2,353 bundled frontend files matched their source assets by SHA-256; updated APK v1/v2 signature verification passed, and its certificate matched version 1.

The frozen runtime and comparison init script are retained outside the project in the clearly marked `F:\Jellyfin\phone-playback-repro` debug directory. The regression tests are in the project's normal test suite. No temporary instrumentation was added to the app. An Android playback smoke test at this boundary would have caught the POST filter regression before the first APK was delivered.

Physical phone video/audio decoding and Chromecast playback have not been verified by the agent. The user reported that web login works and the same movie plays in the regular Jellyfin app; the corrected APK still needs a playback check on the phone.

Version 2 APK SHA-256: `B324E868526B294EF8695F23745335577FCDA0CBD89D6039EFBCE4FACC855E6A`.

## October 8 update: home order and subtitle timing

Recently Added movie and TV shelves appear before genre discovery on Home, just below library links when those links are present. Saved placements lower down are consolidated into this position. The phone keeps the normal saved order if genre discovery is disabled.

During native playback, open Subtitles > Subtitle timing. Use +/-0.1-second or +/-1-second buttons, type seconds with millisecond precision, Apply, or Reset. Positive values delay subtitles; negative values show them earlier. The accepted range is -600 to +600 seconds. Applying a change rebuilds the media source at the current position and retains pause state. This can briefly buffer. Captions burned into the video cannot be shifted independently. Nonzero offsets use Media3's standard ASS parser rather than native libass styling. Timing adjustments are temporary and are not saved per title.

The phone's web player also has fine buttons, exact entry, Apply and Reset, with the same positive/later convention.

Subtitle provider search/download controls have not been added by this update. The server already supports those operations for configured providers and authorized users.

Validation: 18 phone unit tests, 13 Shield unit tests, and 23 frontend discovery/timing tests passed. Both APK builds succeeded; final exports are checked against the prior signing certificates. Physical phone and Shield subtitle playback still requires device testing.

## October 9 update: live subtitle timing

The current timing control is a compact, undimmed bar at the top of the picture. Minus and plus adjust by 0.1 seconds; More exposes a 1-second step, Reset, and exact entry. Positive delays captions; negative shows them earlier. Close or Back dismisses the bar. The normal transport controls hide while it is open.

Native timing now adjusts the subtitle renderer's clock only. It does not pause, seek, rebuild the media source, or restart playback. It preserves the user's existing play/pause state, and retains native libass styling. The previous October 8 reload/parser-fallback behavior above is historical and has been removed. The bundled phone web player uses the same compact layout and preserves rapid consecutive taps.

The subtitle module owns offset state, bounded caption history, output scheduling, and renderer integration. Caption history allows small adjustments in both directions across already-rendered cues. It retains up to ten minutes subject to a 32 MiB/10,000-packet budget; very large shifts, recently sought positions, and future captions not yet delivered by the source may lack available caption data. Burned-in captions and legacy broadcast caption decoders do not support this live adjustment. Timing is temporary for the playback session.

Validation on October 9: 25 phone unit tests, 12 Shield subtitle renderer tests, and 8 web timing tests passed. Renderer tests exercise actual Media3 renderer lifecycle and a scripted subtitle stream, including backward/forward nudges, paused updates, EOS, timestamp conversion, seeks, delayed output, and teardown. They do not replace physical-device tests of decoding, native ASS rendering, or remote/touch interaction. No device installation or server deployment was performed.

## October 9 update: Customize Discovery

Phone v5 and Shield v4 add Customize Discovery in Settings. On the phone, open Settings > Home > Customize Discovery. On Shield, open Settings > Customize Discovery.

The editor supports section order, visibility, and one Small/Medium/Large poster size for Discovery. Save commits the draft; Cancel/Back discards it. Restore defaults changes the draft and requires Save. There is no row-count control.

Default order: Continue Watching, Up Next, Recently Added Movies, Recently Added TV, then Action, Comedy, Horror, Suspense, Drama, Sci-Fi, Fantasy, Adventure, Crime & Mystery, Animation, Family, Romance, and Documentaries. All these sections can be moved or hidden. Empty rows retain the existing automatic hiding behavior. Library navigation, audio, live TV, and other unrelated Home sections remain available after Discovery.

Preferences are local to this installation and scoped by server and signed-in account. No server preference map is modified. Phone/browser data uses account-namespaced local storage; Shield uses account-namespaced SharedPreferences. Reinstalling/clearing app data can reset layouts. Genre choices apply across movie libraries and genre recommendation screens. Continue Watching, Up Next, and Recently Added placement applies on Home.

Open screens refresh their layout after an explicit save. Ordinary playback return and background library events preserve the existing paging/session stability rules. The legacy server-wide phone genre toggle no longer overrides the device layout.

Focused tests cover default/interleaved order, hidden sections, defaults, draft save/cancel, malformed preference recovery, account/server separation, genre filtering and library scoping, plus existing mixed paging and append behavior. Physical phone and Shield interaction still requires device testing.

## October 9 follow-up: server genres and TV editor

Phone v6 keeps Save, Cancel, and Restore defaults outside the scrolling editor body, with bottom safe-area spacing.

Shield v5 replaces the narrow Discovery settings list with a full-width three-pane editor: ordered sections on the left, section controls and a poster-size preview in the middle, and searchable server genres on the right. Save/Cancel/defaults stay in a fixed footer. Other settings retain their existing design.

Both editors fetch the complete movie genre catalog available to the signed-in user. Search filters immediately. Add reveals an existing matching section or appends a new server-genre section; custom additions can be moved, hidden, and removed. Save persists exact server genre IDs with the existing account/device layout. Earlier layouts remain compatible. Genre rows keep the existing mixed infinite pager and unwatched filtering.

While the editor is open, library-change notifications refresh the available choices after a short delay, with a 30-second refresh fallback and a manual Refresh button. Catalog refresh never saves, reorders, or inserts sections into the draft automatically. Catalog errors retain previous choices; account changes cannot save a draft into another account. Close cancels subscriptions and refresh timers. New server genres become available after Jellyfin has indexed their metadata.

Validation: web editor/catalog/storage/query/paging regression tests and native layout/query/paging tests; production frontend and both debug APK builds. Device interaction has not been visually verified on a physical phone or Shield.

## October 9 phone v7: approved illustrated dog icon
The user approved an illustrated portrait of their dog inside the Jellyfin gradient triangle. The approved PNG is bundled directly, with matching navy adaptive-icon background and safe-area padding. Both main and debug launcher resources use the approved design. Pre-adaptive launchers use an any-density bitmap resource. The launcher monochrome layer is omitted to avoid tinting an opaque square. Existing phone functionality and saved settings are unchanged. Full-resolution approved artwork and the exact built-in image-generation prompt are in F:\Jellyfin\artwork.

## October 9 phone v8: Tobyfin branding
Renamed the custom app to Tobyfin and applied the approved heavier Plus Jakarta Sans wordmark and illustrated dog logo to the phone header, connection screen, splash screens, launcher icon, and web app icons. The built frontend is bundled in the APK. App package and signing key remain unchanged for in-place updates. Font license is included. No physical device install or playback verification was performed for this branding-only release.

## Tobyfin v9 home header
The modern frontend omitted its header content when the mobile drawer was enabled, so replacing the legacy banner was insufficient. The modern Home toolbar now explicitly renders the Tobyfin wordmark beside the menu, using the appropriate dark/light asset. Responsive sizing reserves space for the existing toolbar controls. Regression tests cover mobile and desktop home, theme contrast, and exclusion from the video screen. Mounted-component fixtures were checked at 320, 360, 393, 412 and 600px in light/dark themes; no overlapping or wrapped controls. Physical phone testing remains pending.

## Tobyfin v10: uncut Android splash logo
Replaced the fixed 28dp splash inset with an explicit 288dp transparent canvas and centered 144dp artwork. Android's no-background splash mask is a 192dp circle. The former 232dp artwork box extended beyond that mask, clipping the triangle's corners. The alpha-pixel safe-area check now reports no clipped pixels. Artwork is unchanged; only its startup size and spacing changed. Package/signing identity retained. Physical-device visual confirmation remains pending.
