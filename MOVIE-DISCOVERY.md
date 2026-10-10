# Custom Android TV movie discovery

This source ports the approved Jellyfin web behavior into Android TV's native Leanback interface. Home, movie library browsing, Suggested Movies, and the movie Genres screen show thirteen genre shelves: Action, Comedy, Horror, Suspense, Drama, Sci-Fi, Fantasy, Adventure, Crime & Mystery, Animation, Family, Romance, and Documentaries. Empty shelves are removed after loading. The previous watched-film recommendation requests have been removed from Suggested Movies. Genres do not use watch history or favorites to rank or choose categories; they retain the web version's unwatched filter.

Home discovery respects accessible movie libraries and their LatestItemsExcludes settings. Movies from each library stay scoped to that library. Continue Watching, library links, favorites, collections, and nonmovie sections retain their existing behavior.

Genres begin with twenty shuffled movies, then load mixed batches from four disjoint ranges of the matching library. Each batch combines shuffled ten-title slices from different parts of the alphabet. Failed batches retry the same slices, and displayed movies are deduplicated within a shelf. Multi-genre movies may appear in more than one shelf. Paging continues until the matching library is exhausted. Use the remote's left/right navigation to approach the row end and trigger more loading.

Recently Added Movies uses regular item queries sorted by DateCreated and SortName descending, with no played filter. It begins with twenty movies and continues through older additions in batches of forty. This applies to Home and movie library Latest rows. Watching a movie does not remove it from Recently Added.

The new movie rows append cards using insertion notifications, preserving existing card objects and remote selection. Server events and returning from playback do not regenerate genre rows or replace Recently Added. Continue Watching can still update. Leaving and reopening the screen starts a fresh session. Library additions become visible on a fresh visit. Other row types retain their usual refresh behavior.

## Build and install

The ready-to-install APK is `F:\Jellyfin\jellyfin-androidtv-genres-v4.apk` (about 49 MiB). Its app name is **Jellyfin Genres**, and its package is `org.jellyfin.androidtv.debug`, so it can coexist with the regular Jellyfin app on the Shield.

Transfer the APK to the Shield, open it with a file manager, and allow that file manager to install unknown apps when Android prompts you. Then open Jellyfin Genres and sign in with your existing Jellyfin server address and account. The address must point to the Jellyfin server, rather than the development web frontend at `localhost:8080`. No paid developer account is needed to sideload this APK. No changes have been installed on the Shield or deployed to the Jellyfin server.

The supplied master archive requires Java 21, Android SDK platform 37 (`platforms;android-37.0`), and its bundled Gradle 9.8.0 wrapper. Existing dependency versions are retained. Build with `gradlew.bat :app:assembleDebug`. The produced APK is under `app/build/outputs/apk/debug`.

Google's free Android SDK agreement applies to the build tools used on this PC; it is separate from sideloading the finished app. Java, Gradle, and the Android SDK are kept under `F:\Jellyfin\android-build-tools`, without changing system Java or globally installing Android Studio.

## Validation

On October 4, 2026, `:app:testDebugUnitTest --tests '*Movie*Tests' :app:assembleDebug` completed successfully. All eight regression tests passed, covering mixed alphabetical batches, complete coverage of a 520-movie fixture, boundary sizes, retries after failure, deduplication, empty libraries, Recently Added order and watched movies, all thirteen genre filters, library scoping, and preservation of card objects under append and background refresh. The adapter test captures insertion notifications at the Android framework boundary; it does not simulate a physical remote or rendered TV interface.

`apksigner verify` passed with APK v1 and v2 signatures. Manifest inspection confirmed the Jellyfin Genres label, separate debug package, Android TV launcher activity, minimum API 23, and both ARM ABIs (`arm64-v8a` and `armeabi-v7a`). Physical Shield testing and playback testing have not been performed.

APK SHA-256: `A7D779AC7ECB97E717CC710BEB252EC9C95C4EF554C9F525985E9BB64F31DAFA`.

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

## October 9 follow-up: simpler Shield editor and startup rows

Shield v6 replaces the three-pane Discovery editor with a single ordered list. Press OK to pick up a section; Up/Down previews a highlighted "Drop here" position while neighboring rows move around it; OK drops it, and Back cancels that move. Preview moves do not modify the draft until dropped. Save still commits the full layout. Scrolling only follows a moving row when it leaves the visible area, preserving neighboring context. Hide/Show and custom-genre removal remain inline.

Add Genres now occupies a separate full screen. Search, library-change refresh, and the manual refresh button are retained. Done or Back returns to the same draft, and Save is still required.

Home maintains an ordered source of rows and exposes only populated rows to Leanback. Item responses that finish out of order cannot reorder the saved layout. Visibility observers detach when the screen reloads or closes; clearing an adapter now updates its data before notifying observers. This avoids exposing empty genre headers while initial item requests are pending. Poster images still load through the normal image pipeline.

A regression test first reproduced publication of empty rows, then passed with the visibility filter. Additional tests cover out-of-order completion, dynamically added rows, observer cleanup, clear notification timing, and pickup/drop boundaries. Existing layout/account/paging tests remain included. Physical Shield remote interaction has not been verified on a device.

## Tobyfin v7 branding
Renamed the custom app to Tobyfin. Added the approved illustrated dog icon and heavier Plus Jakarta Sans wordmark to the launcher, toolbar, splash, and screensaver. Package identity and signing key are unchanged. See ../artwork/tobyfin/README.md for artwork provenance.

## Tobyfin v8 home header
The home MainToolbar previously replaced the default logo slot with profile controls. Home now includes the Tobyfin wordmark before the profile button, at 144dp by 40dp, preserving centered Home/Search navigation and the existing focus order. Build and signature verified; physical Shield layout testing remains pending.
