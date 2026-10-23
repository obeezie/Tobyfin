# Tobyfin — October 10, 2026

Approved dog illustration and heavier Plus Jakarta Sans wordmark applied to both custom applications.

- Android phone: tobyfin-phone-v12.apk (versionCode 12, org.jellyfin.mobile.debug)
- NVIDIA Shield: tobyfin-shield-v12.apk (versionCode 12, org.jellyfin.androidtv.debug)

Install the matching APK over the existing customized application. Do not uninstall first if you want to keep its settings. Canonical jellyfin-android-phone-genres.apk and jellyfin-androidtv-genres.apk downloads also contain these new builds. Earlier versioned APKs are retained.

Updated surfaces: phone launcher, connection screen, header, native and web startup screens, web icons; Shield launcher tile/icon, toolbar, startup screen, screensaver, and app-facing names/settings labels. This release adds sleep timers and Discovery changes described below; existing live subtitle timing and branding remain included. Jellyfin server references and upstream attribution remain intact.

Validation:
- Frontend production build passed (two standard size warnings).
- ESLint and Stylelint passed for the changed frontend source.
- Both Android APK builds passed.
- Both manifest labels are Tobyfin; version codes are higher than the previous releases.
- Both APK signatures verified and match the existing certificate: b314e7316d32dc2b6936b44a4e8adf5b3ae29248c3d1cacb6dcebf5c73a7f0b7.
- Checked native artwork and font license entries; phone embedded manifest is Tobyfin and banner bytes match production artwork.
- Physical phone/Shield installation and runtime visual checks were not performed.

SHA-256:
Phone: 075A5FC6B20C52068C660158AAF100FE6B2BCD75495165A4DB96454CE24DF9D6
Shield: 6659C9EADAF5150F45E0E307E4AA4172E666A428200A289A0B7212E83C609D8F

Source archives refreshed: jellyfin-android-phone-genres-source.zip, jellyfin-androidtv-discovery-source.zip, jellyfin-web-discovery-source.zip, jellyfin-web-discovery-built.zip. Generated production artwork, font license, rendering scripts, and image-generation prompt are in artwork/tobyfin/.
## Phone v12 / Shield v11: sleep timer, removable default genres, and recent TV paging

- A moon button in video playback opens the compact sleep timer. Presets: 15/30/60/90 minutes, custom 1–1440 minutes, or stop after the current episode/movie. Remaining time, +15 minutes and Cancel are available on reopening. Expiry pauses at the current position. End-of-item mode suppresses autoplay; timed countdowns continue across episode handoffs. The menu itself does not pause playback. Explicit Play clears an expired timer. Details and lifecycle notes: SLEEP-TIMER.md.
- Customize Discovery can remove built-in as well as custom genre sections. Removal survives saves/restarts. Add Genres can restore removed defaults and Restore defaults resets the layout. Core rows retain Show/Hide controls. Save/Cancel behavior and per-account/device scoping remain intact.
- Recently Added TV now pages unique shows using DateLastContentAdded descending, without hiding watched items. Home/Discovery and phone/Shield library suggestions use the stable incremental row renderer. Rows load until the accessible library is exhausted, retaining their position and avoiding duplicate series cards.
- Phone adaptive launcher logo is larger: foreground inset 18dp → 12dp in both main and debug resources. The installable APK's compiled inset was explicitly verified as 12dp. Splash artwork/padding is separate and unchanged.

Verification: 29 web tests, 5 phone timer tests and 23 Shield timer/Discovery/pager tests passed. ESLint had no errors (existing TODO/deprecation warnings); Stylelint passed. A headless Edge check exercised timer presets, custom entry, extension, cancellation and end-of-item at 320/393/852/960px without overflow. Production frontend and both APK builds passed. Package IDs, increasing versions and the existing signing certificate were verified. Native timer classes and moon icons, phone compiled launcher inset, exact embedded new feature bundles, prior buffering artwork, and Shield transparent banners were checked. Canonical APKs and all four source/built archives were refreshed. Physical phone/Shield playback and remote-control validation remain pending.

## Home header follow-up
The previous asset rebrand did not supply a logo to the modern phone toolbar's drawer layout or the Shield Home toolbar's custom start slot. Phone v9 explicitly renders the wordmark next to the menu on Home (including Favorites). Dark/light themes use contrasting assets. Small-screen styles reserve room for the existing controls. Shield v8 renders a 144dp × 40dp logo before the profile control on Home without adding a focus target.

Verification: the real phone toolbar initially failed three logo assertions. All four focused checks pass after the fix (mobile, wide layout, dark/light contrast, and no home toolbar during video playback). Native mounted-component fixtures with MUI-sized mock action controls passed headless Edge checks at 320, 360, 393, 412 and 600px in both themes: image decoded, no overlapping controls, no wrapping, and no horizontal overflow. Temporary fixture export instrumentation was removed; diagnostics live under artwork/tobyfin/debug. ESLint and Stylelint passed. Shield home visual validation on hardware remains pending.
Both updated APK builds and signatures passed. Verified that the phone APK's new header JavaScript and stylesheet exactly match the successful frontend build.

## Phone v10 splash safe area
The startup logo used a fixed 28dp inset that left a 232dp artwork box on Android's 288dp splash canvas. Its triangular corners exceeded the circular 192dp safe area. The updated drawable uses a transparent 288dp canvas and centered 144dp artwork. All nonzero-alpha pixels fit within the safe circle (maximum radius 93.73dp, limit 96dp). Phone build and signing checks passed, and the compiled splash drawable was inspected. Physical phone visual confirmation remains pending. Shield stays at v8.

## Shield v9 transparent launcher banner
The launcher banner now has a fully transparent background around the approved Tobyfin triangle and white wordmark. Artwork geometry and lettering are unchanged. Both banner generators retain transparency; all five packaged density variants were verified against the source images, including fully transparent corners. Build, versionCode 9, and existing signing certificate verified. Source archive refreshed. Physical Shield launcher rendering remains untested. The launcher may apply its own focus treatment.

The dog-only buffering animation was reviewed at artwork/tobyfin-preview/buffering-preview.html, then approved for implementation in the releases below.
## Phone v11 / Shield v10: buffering and browsing branding
Both native video players now display the approved transparent dog head inside a rotating purple/cyan ring. The 92dp indicator preserves the preview's 74dp dog and 2dp upward optical adjustment. Native animation starts only while attached and visible and cancels when hidden or detached. System-disabled animations are respected on API 26+. The view issues no playback, seeking, or timing commands. The Shield indicator follows Media3 buffering state and hides on errors. The phone retains its existing buffering state observer.

The embedded web loading overlay uses the same artwork and offset, including when the web video player buffers. It respects reduced-motion preferences and retains existing show/hide behavior.

Phone branding now appears on the shared non-video toolbar, including details, libraries, search and web settings. Native phone settings have a compact brand icon next to the title. Shield branding appears in the main/search toolbar, detail page, grid and shared library headers, and settings panel. No persistent branding overlay is added to playing video. Shield's transparent launcher banner and phone's splash safe-area fix remain included.

Validation: ten permanent toolbar tests passed; real mounted-toolbar fixtures passed 320/360/393/412/600px checks in dark/light themes with six navigation/action controls. Browser checks verified ring motion, stationary dog, optical offset, hiding, and reduced motion. ESLint and Stylelint passed. Frontend build passed with two existing size warnings. Both APK builds and signatures passed. Compiled native buffering layouts, exact native/web dog assets, embedded branded JS/CSS, and all five Shield banner variants were verified. Canonical APKs and all four source/built archives refreshed. Physical device visual/playback validation was not performed.

Production dog source: artwork/tobyfin/buffering-dog.png. Image-generation provenance: artwork/tobyfin-preview/BUFFERING-NOTES.md. Layout diagnostics: artwork/tobyfin/debug/verify-buffering-layout.cjs. Release verification: artwork/tobyfin/verify-buffering-release.ps1. Implementation scripts are one-time migration records; do not rerun them against already modified sources.
## TV header decision — October 10, 2026

Approved: keep the complete Home/Discovery header visible at every scroll position, including the Tobyfin logo/wordmark and Home/Search controls, on its existing transparent background. Do not implement the proposed fade-out or “Search at top” cue.

Source inspection: HomeFragment renders MainToolbar as a sibling above HomeRowsFragment in a Column. MainToolbar/Toolbar have no scroll-dependent visibility, opacity, or background panel. This decision retains the existing native Home layout; no application source, version, or APK change was needed. The isolated HTML prototype is a concept only, not a device screenshot. Physical Shield behavior was not checked.

### Correction to the TV header decision
The user clarified that the existing Shield header has an opaque backdrop. Desired behavior: keep all header content visible at all times; make only the backdrop fully transparent below the top, and restore the backdrop at the top. The earlier conclusion that no change was needed was premature: inspecting MainToolbar alone did not establish the actual on-device backdrop. The corrected isolated prototype demonstrates both states. Native implementation and APK rebuild are pending identification of the reported backdrop; no application source was changed.

## Shield v12: let posters scroll behind the persistent home header

The user's screenshot identified a clipped rows viewport, rather than an opaque toolbar fill. HomeFragment previously placed MainToolbar and HomeRowsFragment in a Column; the rows' top clipping boundary was consequently below the toolbar. The home screen now overlays the unchanged, fully visible toolbar over a full-screen rows fragment. Header height becomes grid content padding with clipToPadding disabled, allowing previous-row posters to draw behind the header when scrolling down. At the top, this content padding keeps the first row below the header and preserves the original clear header area. No new solid background, fade-out, or search cue is added.

HomeHeaderInset adjusts the focus keyline to preserve its screen position after the viewport expands. Repeated measurement is idempotent; the inset helper is recreated with each fragment view. Existing first-row navigation to Home/Search remains in place. Phone sources and APK are unchanged.

Validation: :app:testDebugUnitTest (HomeHeaderInsetTests and HomeRowVisibilityTests, 6 tests total) and :app:assembleDebug passed. Package/versionCode 12, existing signing certificate, native artwork and transparent launcher banner verified. Versioned and canonical Shield APKs and the Shield source archive were refreshed. Physical Shield visual and remote-navigation checks remain pending; no device was connected. This supersedes the earlier premature conclusion that no application change was needed.

## Phone v13: steady home load and restored home on Back

- Home sections stay hidden behind the loading spinner until every section has loaded its cards. Previously, sections appeared in network order: My Media, then empty genre headings, then Continue Watching/Next Up pushed everything down.
- Leaving Home for a details page parks the rendered rows rather than discarding them. Returning restores the same cards, including infinitely loaded ones, plus each row's horizontal and the page's vertical scroll. Only rows that track playback (Continue Watching, Next Up) refresh. A changed account or Discovery layout, a parked copy older than 30 minutes, or leaving before the first load finishes all rebuild Home normally.
- Files: homesections.js/.scss, apps/legacy/controllers/hometab.js, apps/modern/routes/home.tsx. Tests: homesections.test.ts (visible sections are always the top of the final layout) and hometab.test.ts (return restores rows without reloading; another account reloads).

Validation: all 231 web tests passed, along with tsc, ESLint and Stylelint. The production web build passed with its two existing size warnings. tobyfin-phone-v13.apk is versionCode 13, labeled Tobyfin, and signed with the existing certificate. Its packaged web assets contain the new home code. Shield is unchanged, because its home is native. Physical phone validation is pending.
SHA-256 phone v13: 5E954B1DA1FD0209ADAAE9BEBED5F205906EA225345DB7E170FA1831685483E7

## Phone v14: row scroll positions survive Back

v13 restored Home's vertical position but put every row back at its start. Parking runs after React removes Home, and a detached row reports scrollLeft 0. Each row's horizontal offset is now recorded live with a capture-phase scroll listener, like the page's vertical scroll. The hometab.test.ts regression test reproduces the reset. All 232 web tests pass. Installed on the phone over wireless adb and confirmed by the user: returning from a movie keeps both the section and the position within its row.

## Shield v13: top-down home loading and remembered row positions

- Home rows now appear top-down. A row is shown only once every row above it has answered its first request: with results, or empty, in which case it is skipped. A slow upper row can therefore never insert itself above rows already on screen. After 8 seconds Home stops waiting on a row that has not answered, and after the first load rows behave as before. ItemRowAdapter has a new first-retrieve signal for this.
- Each row remembers its selected card (HomeRowPositions). The card is reselected when Leanback rebinds the row, for example on returning from a details page or when a row scrolls back on screen. A position beyond a refreshed row's new size is ignored.
- Tests: HomeRowVisibilityTests (rewritten: top-down reveal, empty upper rows, stopWaiting) and HomeRowPositionsTests. All 129 Shield unit tests passed and assembleDebug passed. The APK is versionCode 13, labeled Tobyfin, and signed with the existing certificate. Physical Shield validation is pending.
SHA-256 Shield v13: D62D91739129DC4A9CE4BDA511EC16B342726FDF34C1F6F8CE94BB58974EBBFB

## Phone v15: keyboard no longer covers text fields; faster Home

- Keyboard: the app is edge-to-edge, and on recent Android that means the window is no longer resized for the on-screen keyboard. applyWindowInsetsAsMargins (web view, connect and settings screens) now reserves the larger of the system-bar and keyboard insets. The Discovery editor also scrolls the focused field into view when the page resizes. Test: discoveryEditor.test.ts.
- Faster Home: Home appears once the sections filling the first screen have their cards. Sections still loading are all below the first screen and fill in out of view. Home is remembered for Back only after every section has finished loading. Tests: homesections.test.ts (ordering invariant plus early reveal).
- Validation: all 234 web tests pass, along with tsc, ESLint, the production build and the APK build. versionCode 15 is signed with the existing certificate and was installed on the phone over adb. The user confirmed the keyboard fix on the phone. The faster Home made no noticeable difference in load time; the cause has not yet been measured.
SHA-256 phone v15: 6088E2FA66A889EF877F204E50537276DDCA9A66B5F476967004FD92BBF06944

## Phone v16: first-screen rows load first

I measured three cold launches over adb using tagged WebView console timings, since removed. About 1.2s passes from the tap until Home starts loading: process start, WebView and the web app. The first screen then waited on Recently Added Movies. Every section started at once, so 11 below-the-fold genre rows took the WebView's per-host connections, and Recently Added's request queued behind them. Sections now start in screen order, three at a time.

Home load to reveal, measured on the phone: cold 1.95s → 0.89s, warm 1.34s/1.30s → 0.38s/0.70s. Test: homesections.test.ts ("starts sections in screen order"; fails without the queue). All 235 web tests pass, along with tsc, ESLint and the production and APK builds. versionCode 16 is signed with the existing certificate and installed on the phone.

Remaining time: about 1.2s for app and WebView startup, plus Recently Added's own server response (0.4–0.7s). Showing the previous Home from a cache would hide both.
SHA-256 phone v16: 8A4A52E70494221BF590D6CD79E9941369CDE243A510D179CCE4A2ACE5F2EB90

## Shield v14: first-screen rows load first

Every Home row started its request at once. Rows now start in screen order, three at a time (HomeRowLoadQueue). A row's slot frees when its first request finishes. After the 8-second safety timeout, any rows still waiting all start. ItemRowAdapter accepts several first-retrieve listeners.

Measured on the Television_4K emulator against the user's server, from Home building its rows to the first rows on screen. Earlier temporary builds were all-at-once vs three-at-a-time, with timing logs that have since been removed. Results: 2.8s/2.6s → 2.1s/2.3s/2.3s. The first run after sign-in took 9.9s before the change.

Remaining delay: Continue Watching takes ~1.8–2.0s on the server, even when it starts first and asks for 12 items instead of 50. That was tested and ruled out, and the limit stays at 50. The phone's first cold launch showed the same ~2s. Only showing a cached Home first, or speeding up the server's resume query, would remove it.

Tests: HomeRowLoadQueueTests. All 131 Shield unit tests pass and assembleDebug passed. versionCode 14 is signed with the existing certificate and installed on the emulator. Physical Shield validation is pending.
SHA-256 Shield v14: F1E28B4771BC06C16A38DEB75226C4F9A8F547630EF73DCB930A8F760C5A12B2

## Shield v15 / Phone v17: slow rows open from a saved copy

Only the slowest Home requests are cached. The server takes about 2 seconds for Continue Watching on both apps, and about 2–2.7 seconds for Recently Added TV Shows on the Shield. Every other row answers in about 1 second.

- Shield: HomeRowCache saves the last successful results of Continue Watching (`resume-video`) and Recently Added TV Shows (`latest-series-<library>`) per user in the app's cache directory. On the next launch the saved cards show at once. When fresh results arrive, they replace the saved cards in place, matched by item id so focus stays put. A failed request keeps the saved copy. Tests: HomeRowCacheTests. All 133 Shield unit tests pass.
- Phone: Continue Watching uses the persisted query cache. A saved result older than 1 minute is shown at once and refreshed in the background, and the row is redrawn only if the fresh result differs. With nothing saved, Home waits for the server as before. Tests: resume.test.ts. All 238 web tests pass.
- Emulator, measured from Home building rows to the first row shown: 2.4s with nothing saved; 0.48s, 0.46s and 0.29s on the following launches. Screenshots confirmed that Continue Watching shows straight away with placeholder images, followed by the images and Next Up.
- Shield v15 is installed on the emulator. Phone v17 has not been installed yet: the adb connection to the phone was stopped while setting up the emulator.
SHA-256 phone v17: 72DD9EDC96D6B1C6E658F39D199E0DEAE76C4DEFA2CBC012F87444D3E58FC8AD
SHA-256 Shield v15: 3B648932A46E568BBFA4C4FB2548A63CB8B6590ED2BCFD7DAE3CFB31081D7E10

## Phone v18: Home keeps its place after repeated Back

The user reported that Back restored Home's position once, but a second movie followed by Back rebuilt Home at the top. The cause was v15's rule that only a fully loaded Home is parked: the container a parked Home was restored into never received the homeSectionsComplete flag, so the next trip skipped parking. restoreSections now sets the flag. The regression test in hometab.test.ts ("keeps restoring Home across repeated trips") failed before the fix and passes after it. All 239 web tests pass. The Shield is unaffected, since its Home is native. Phone v18 includes the v17 saved Continue Watching. It is signed with the existing certificate and was installed on the phone over wireless adb, replacing v16.
SHA-256 phone v18: D8E722CADDFF0B6552B6BB60E8F6D93D0ECCA9718DBA899B6D5D12E869E77E54

## Shield v16: optimized build fixes sluggish Home navigation

Measured on the user's SHIELD Android TV (Android 11, 1080p UI) over network adb. Each run is 40 remote presses on Home, read from gfxinfo:
- Tobyfin v15 (debuggable): p90 32–40ms, p99 125–150ms, 48–66 janky frames, 27–30 slow UI-thread frames.
- Stock Jellyfin 0.19.10: p90 13ms, 13 janky frames, 4 slow UI-thread frames.
- v16 non-debuggable: p90 20–21ms, 15–18 janky, 9–12 slow UI.
- v16 non-debuggable + R8: p90 18ms, 9–11 janky, 4–7 slow UI.
On a freshly booted emulator v12 and v15 measured the same, so the Tobyfin features were not the cause; the debuggable build (fewer ART optimizations plus Timber DebugTree logging) was. Turning off row-position memory or backdrops changed little.

The Tobyfin (debug) build type is now `isDebuggable = false` and `isMinifyEnabled = true` with the upstream ProGuard rules. Because of this, adb run-as and profiling no longer work on installed builds. The APK shrank from 56MB to 34.5MB. Smoke-tested on the Shield: Home with the saved rows, a details page, Back with the position kept, and Settings → Customize Discovery, with no crashes. Playback and the sleep timer were not exercised. All 133 unit tests pass.

Caution: simpleperf `record -g -f 2000` crashed the Shield's system_server (DeadSystemException). Use gfxinfo instead.
SHA-256 Shield v16: 046BEB6AC353306ABBA1B5C45C9A399923A30803CF32184F8A70FE3F901942F3

## Shield v17: open-triangle, larger launcher banner

The user asked for a slightly larger logo on Projectivy and a transparent area inside the triangle. In a Shield screenshot, both triangles measured about 122px at 4K; Jellyfin's looked larger because its triangle is an open outline. artwork/tobyfin/generate-tv-banner-open.cjs flood-fills the navy interior (RGB 0,13,36) from a seed below the chin and removes 11,836 px. It un-blends a 3px edge band so there is no dark fringe, and leaves the nose and eyes untouched. It writes banner-light-open.png, scales the mark from 880 to 986px wide, centers it, and regenerates every TV banner density plus artwork/tobyfin-launcher-banner-transparent(-cropped).png. The previous files are in artwork/tobyfin/previous-banner.

v17 (resource change only) is installed on the Shield. Projectivy kept showing the old banner: it does not reload banners after an app update, and the user had also set a custom image. Force-stopping Projectivy previously disabled its accessibility service and needed a Shield restart, so it was not restarted again. The new PNGs were pushed to /sdcard/Download/tobyfin-banner-v2(-cropped).png for the user to choose in Projectivy.
SHA-256 Shield v17: CB98635E62CF79143B407D1B240975D3B38EBFE5AC557681D17E286DF210485E

## Shield v18: "Who's watching?" profile picker

Designed with the user in grilling rounds and a phone-viewable prototype (prototype/whos-watching-prototype.html, published as an artifact). Approved choices:
- Premium carousel; the glow background drifts.
- Gradient ring and glow on the selected profile; circular avatars; name only.
- Flat slide, ring moving with the profile, springy settle over 600ms.
- Wood click on each move and Two-note chime on choosing; the chosen avatar grows into Home.
- A centered title.
- Profiles in the order added, with the highlight on the last user.
- No lock. Hold OK removes a profile from the TV only.
- Back leaves the app.
- Server names show only when more than one server is saved.
- One profile skips the picker.

The picker asks on launch and after 30+ minutes in the background or asleep; time with the TV on doesn't count. Opening from outside goes through the picker, then on to the requested item.

Implementation: UserSelectBehavior.ASK is the new default; WhosWatching holds the rules (unit-tested); SessionRepository restores no session when it should ask; StartupActivity shows WhosWatchingFragment (Compose); WhosWatchingReturnWatcher, registered in SessionInitializer, handles returning after 30 minutes. Sounds are synthesized WAVs in res/raw, and Plus Jakarta Sans (variable) is in res/font. There is no runtime blur, because the Shield is API 30.

Verified on the Television_4K emulator at 1080p, with one saved profile ("martin"):
- Launch skips the picker.
- Home's profile button opens it.
- Moving, the hold-OK remove menu, and Back closing it all work.
- Choosing grows the avatar into Home with no splash screen.

Two bugs were found in the recording and fixed:
- OK did nothing after the menu closed (the held-OK state was never reset).
- Fading clipped the glow and the growing avatar to a square (fixed with CompositingStrategy.ModulateAlpha).

Demo: whos-watching-demo.mp4. All 136 unit tests pass.

Not yet verified:
- Two or more profiles (needs a second account signed in on the emulator).
- Asking again after 30 minutes away (the rule is unit-tested).
- Removing a profile in practice.
- Opening an item from outside.
- Sounds on real hardware (the screen recorder doesn't capture audio).
- Frame timing on the Shield.
Follow-up: the Add profile "+" was off-center (glyph metrics); it is now drawn as two lines and measured within 1px of center.
Follow-up: names in the picker show with a capital first letter (display only; sign-in keeps the account name).
SHA-256 Shield v18: 0B25352444565A7D55769CFE310901C86EC4C16FA7F1834CC442C833F7F3773B

## Shield v19: silent picker, your initial on Home

- The user disliked the sounds, so the Wood click, Two-note chime and res/raw files were removed.
- Home's profile button shows the signed-in person's initial (ui/base/ProfileInitial.kt) in the same color as their "Who's watching?" avatar, via the shared ProfileColors. This is option A, chosen from a mock-up comparing it with an initial plus gradient ring. A Jellyfin profile picture still wins when one loads.

Verified on the emulator, in normal and highlighted states. All 136 unit tests pass. Installed on the Shield (versionCode 19).
SHA-256 Shield v19: 6A1D8D8B8456073FA8B53C592BD0F664D140B32E969D22F24405196D6FB086A8

## Shield v20: holding OK no longer removes a profile

Bug reported on the Shield: holding OK on a profile opened its menu, and releasing OK removed the account. The remote repeats OK while it is held. The menu opened on the first repeat with "Remove from this TV" focused, so the remaining repeats and the release clicked it. The earlier emulator test used `input keyevent --longpress`, which sends a single repeat that never reached the button, so it did not catch this.

Fix: an onPreviewKeyEvent on the screen swallows the remaining OK repeats and release of the hold that opened the menu, and the menu now starts on Cancel. Verified on the emulator (API 36) with `input keyevent --duration 1500 23`:
- After the hold, the menu stayed open with Cancel focused and the profile was not removed.
- OK on Cancel closed the menu.
- OK on the profile opened Home.

Installed on the Shield (versionCode 20).

All 136 unit tests pass.
SHA-256 Shield v20: F7FC193E4D4A672917F8979FE2D284B477FFB42CD7C545F945152E797E084996

## 2026-10-11: two servers in "Who's watching?" confirmed

The user added a second server on the Shield using the workaround (Automatic sign in → Disable → the old account screen → Add server, then back to Ask who's watching). They confirmed that the picker shows profiles from both servers on one page.

Known gap: with only one saved server, the picker's Add profile goes straight to sign-in, so it cannot add a new server. Planned fix: offer "Sign in on <server>" or "Add a new server". Also pending: the phone's buffering spinner can stay over a restored Home (the restore path never calls loading.hide()).

## Shield v21: add profiles and servers from "Who's watching?"

Closes the known gap from 2026-10-11. Add profile now opens a panel over the picker instead of the old server and sign-in screens. The design was agreed with the user and approved as prototype/add-profile-prototype.html.
- The first card lists the servers saved on this TV, servers found on the network, and "Enter a server address". The address card uses the TV keyboard and has https://, .com, .net and :8096 shortcuts.
- Sign-in uses Quick Connect first, with a QR code that opens the server's Quick Connect page with the code filled in. "Use password instead" shows the server's user tiles, or a username field.
- Problems show inside the card with Try again / Back.
- A new profile slides into the carousel and opens Home. An account that is already on the TV only has its sign-in refreshed.
- A fresh install opens the picker with the panel up. Removing the last profile on a server also forgets that server.

Fix found during testing: Back took two presses in the panel and in the remove menu, because Compose spent the first press clearing focus. One press now goes back.

Tested on the Shield: the picker, the server list (oasis and Navarro, no other servers on the network), a real Quick Connect code for oasis, and single-press Back in the panel and the remove menu. Nothing was signed in, added or removed during testing. The user tried it with the remote and said it is good. All 138 unit tests pass, including 2 new ones in AddProfileTests. Installed on the Shield (versionCode 21). Committed as 4c1c8d4 on branch shield-v21-add-profile.
SHA-256 Shield v21: 6AAD08179E88D0C9831B76CBDBAB231017188B3DC62F54F7D1171400FB848CC1

## Shield v22: find other subtitles from the player

The CC menu now starts with "Find other subtitles…" and "Subtitle offset" (renamed from "Subtitle timing"), then a divider and the tracks. The design was agreed in a grilling session and approved as prototype/subtitle-search-prototype.html.
- Find other subtitles pauses the video and opens a side panel. The panel shows the file's release name and starts in the last language searched (else the account's preferred subtitle language). Results show SDH, Forced and Machine translated labels.
- Choosing a result downloads it on the server, then restarts playback at the same position with the new track and the subtitle offset reset to 0.
- If the server takes over 15 seconds, playback resumes and the track is offered in the CC menu when it arrives.
- The entry only shows for accounts allowed to manage subtitles, on videos with a file on the server.

Depends on the server: the account needs "Allow subtitle management", and the server needs a subtitle provider plugin (for example OpenSubtitles). The user is arranging both with the server host, so the search itself has not been tried on a real server yet. All 143 unit tests pass, including 5 new ones in SubtitleSearchRulesTests. Installed on the Shield (versionCode 22). Committed as 9757c5b on branch shield-v21-add-profile, together with v21 and the new glossary (CONTEXT.md) and ADRs. Follow-up 194c242 (same v22 build, reinstalled): the CC button now also shows on videos with no subtitle tracks when a search is possible, since otherwise the feature was unreachable exactly where it is needed most.
SHA-256 Shield v22: 1A00E3BBE66C46AF22CC4A805F1FF848EA357777AF8BFA234EEC05C452102779

## Phone v19: find other subtitles from the player

Brings Shield v22's "Find other subtitles" to the Phone app's native player. The subtitles menu now starts with "Find other subtitles…" and "Subtitle offset" (renamed from "Subtitle timing"), then a divider and the tracks.
- The search opens over the paused video: a side panel in landscape, a bottom sheet in portrait. Tapping the video or Back closes it.
- It has the same release name, language order, labels, auto-switch with offset reset, and slow-server behaviour as the Shield app.
- It only shows for streamed videos (subtitles saved on the server don't reach downloads), and for accounts allowed to manage subtitles. The subtitles button now also shows on videos with no tracks when a search is possible.

The web client is unchanged, so the bundled copy was not rebuilt. All 35 phone unit tests pass, including 5 new ones in SubtitleSearchRulesTests. Like Shield v22, it is untested against a server with a subtitle provider. Committed as dcc3201 on branch shield-v21-add-profile.
SHA-256 Phone v19: 828A920D5BD4117F85648A0A55106C2DF3F6D1551C6E05F58E8E433209840724

Published 2026-10-13 as GitHub pre-release "Tobyfin 2026-10-13 (Android TV v22, Phone v19), testing" (tag 2026-10-13, target branch shield-v21-add-profile, pushed): https://github.com/obeezie/Tobyfin/releases/tag/2026-10-13. The 2026-10-11 release stays Latest.

## Phone v20 and Shield v23: redesigned Customize Discovery

Designed in a grilling session and approved as prototype/customize-discovery-prototype.html. Both apps share one design in Tobyfin's style:
- Poster size is a Small / Medium / Large choice with poster shapes.
- Every row is either **On Home** or in **Add to Home**. Separate hiding and removing are gone.
- **On Home:** reorder rows, take a row off with its switch, or use Move to top. Only built-in rows are labelled, and built-in genres the server has nothing for say "Nothing to show yet".
- **Add to Home:** search, then rows taken off (newest first), then the server's genres. + adds a row to the end of On Home, and the genre list refreshes on its own.
- **Saving:** changes save as you go, with Undo. Reset to default asks first.

Phone: Customize Discovery has its own Settings entry (removed from Settings > Home). It uses two tabs, "On Home · N" and "Add to Home"; drag a row's handle to reorder and long-press for Move to top. The web client was rebuilt and bundled.
Shield: one screen with two columns. OK picks a row up to move it, Back puts it back, and ▶ reaches the row's switch and its menu. The separate Add genres screen is gone.

The stored layout format is unchanged, so existing layouts carry over. Tests: 251 web (including 18 new), 152 Shield (9 new) and 35 phone, all passing; web lint is clean. Not yet tried on a device. Committed as cf128c5 and b102817 on branch customize-discovery, which starts from shield-v21-add-profile.
SHA-256 Phone v20: 39C054F5956167F85B994931FE2E34A14C9A2396B1D624B9078F4E305B9423A0
SHA-256 Shield v23: 1BAA251206BCD1B390879F263D5C55C6A8BF7FC500444AA20653D8B204F356C0

Published as GitHub pre-release "Tobyfin 2026-10-13b (Android TV v23, Phone v20), testing" (tag 2026-10-13b, branch customize-discovery pushed): https://github.com/obeezie/Tobyfin/releases/tag/2026-10-13b

## Phone v21 and Shield v24: Tobyfin theme for the menus

Designed in a grilling session and approved as prototype/tobyfin-menus-prototype.html. Menus, and the pages they open, now use the Tobyfin look: dark navy, Plus Jakarta Sans headings, purple-to-cyan accents and rounded cards.

Phone:
- A new **Tobyfin** web theme is the default (Settings > Display > Theme still offers the others). It restyles every page, menu and dialog.
- **Settings** is grouped into cards (Your account, Home, Watching, This phone, Administration) with icon tiles and a short line under each item. Customize Discovery uses the same plain tile as the rest, and the missing divider is fixed.
- **Account menu**: your name and avatar at the top, items grouped, Sign Out in red.
- **Side menu**: navy drawer with section labels; Downloads, Administration and You (Settings, Sign Out) added; the current page has the gradient.
- **Settings pages** (Home, Playback, Subtitles, Display, Profile, Quick Connect): cards with small section labels, switches instead of checkboxes, choices with the value in cyan on the right, gradient Save buttons. Choices open a bottom sheet where the current one is outlined in cyan.
- Sign Out asks first, from Settings, the account menu and the side menu.
- **Downloads** and **Client Settings** (native screens) are restyled to match: navy, rounded rows and card groups, gradient switches, rounded dialogs.

Shield:
- The app's color scheme is navy, so buttons, inputs and panels match Who's watching. Focused buttons are white.
- **Settings panel**: navy all the way down (no gray strip above Customize Discovery), icon tiles, a short line under each main item, small uppercase section labels, Plus Jakarta page titles.
- Focus is a white outline on lighter navy; the current choice in a list has a cyan outline and a gradient dot. Values show in cyan on the right of their row. On/off settings are gradient switches.

Checked: Shield on the emulator (Settings, Customization, a choice list). Phone: web lint, types and 253 web tests pass (2 new), and the Settings page, settings rows and bottom sheet were checked in a browser with test markup. The account menu, side menu and Display page were not seen signed in.
Tests: 253 web (2 new), 152 Shield and 35 phone, all passing; web lint and types are clean. Committed as 674090e on branch tobyfin-theme (from customize-discovery), pushed.
SHA-256 Phone v21: DA59F26E751B1BE634E42B2833BBCFC491D216B7A42D2A0788E060EA728313F1
SHA-256 Shield v24: 19F54A321B277373FCCA38D461BEEEEC0023192E94F7BA88FB1D1A08C0FED292

Published as GitHub pre-release "Tobyfin 2026-10-13c (Android TV v24, Phone v21), testing" (tag 2026-10-13c, branch tobyfin-theme): https://github.com/obeezie/Tobyfin/releases/tag/2026-10-13c

## Phone v22 and Shield v25: Home settings folded into Customize Discovery

The Home section slots (Settings > Home on the phone, Settings > Customization > Home on the Shield) mostly did nothing once Discovery took over Home: picking Continue Watching, Next Up or Recently Added in a slot was ignored, and the rest were always added below Discovery's rows.
- The slots are gone. Their rows are built-in Customize Discovery rows: **My Media, Continue Listening, Live TV, Recordings, Recently Added Music & More**, and Continue Reading on the phone (the Shield has no books). Each can be turned on or off and ordered like any other row. "Recently Added Music & More" could not be turned off before.
- Home looks the same after the update: each new row starts On Home only if the old slots showed it (Recordings starts in Add to Home by default).
- Phone: Settings > Home is now **Libraries** (library order, Show in My Media / Recently Added, hide watched in Recently Added, folder groups).
- Shield: the Home entry is gone; "Up Next shows" (the Next Up date cutoff) moved into Customize Discovery, next to Poster size.
- Phone fix: the "Connect to Server" heading was almost black on navy (v21).

Tests: 257 web, 156 Shield (4 new) and 35 phone, all passing; web lint and types are clean. Not tried on a device. Committed as f7e9e7c on branch tobyfin-theme, pushed.
SHA-256 Phone v22: 42888F07165C736513408F5597FB2EE3C8E01E156ED0E213857ADFEAC054D15A
SHA-256 Shield v25: D0B3859C9D9C71A0672BDD1020F32190B35A2631668FC21C5742C749A139F0AB

Published as GitHub pre-release "Tobyfin 2026-10-13d (Android TV v25, Phone v22), testing" (tag 2026-10-13d, branch tobyfin-theme): https://github.com/obeezie/Tobyfin/releases/tag/2026-10-13d

## Phone v23 and Shield v26: server genres show in Add to Home again

Reported by the user: searching Add to Home in Customize Discovery found none of the server's genres. The apps asked the server for all movie genres at once, which Jellyfin 10.11 (the user's server runs 10.11.11) answers with nothing. They now ask each movie library for its genres and merge them, as Jellyfin's own clients do, with the server-wide request as a fallback. Verified on the Shield emulator against the user's server: genres such as Crime now appear. Tests: 259 web (2 new), Shield and phone suites passing.
SHA-256 Phone v23: 4FA3B098F848E5EE604752A8B5B8C82EA1A5288633597629B1F74EB7C1D0DE30
SHA-256 Shield v26: 62BD681EC4A949EA5CF75A4275C45F22E44B08A1460EF968AF09944802352858

## Shield v27: Spotlight Home layout, merged into main (2026-10-22)

- Brings in the Spotlight row layout from main (built in a Claude cloud session): Customize Discovery > Layout: Standard / Spotlight, per profile. The selected row sits at the bottom with the highlighted title's name, details and overview above it.
- Spotlight changes made here, all checked on the emulator: the details line no longer draws over the cards; a sharp backdrop on the right fades into the text area (the full-screen blur is off in Spotlight); backdrops load at 1280x720 and the neighbours are preloaded; rows above the selected one stay hidden, with no flash when scrolling up; a row lines its scroll up with its selection when you arrive (fixes a backdrop showing a film whose card was off-screen).
- Customize Discovery: "Up Next shows" moved beside Undo to make room for Layout.
- Includes Phone v23 / Shield v26 (server genres in Add to Home).

Pushed: branch tobyfin-theme fast-forwarded main to 5cca85c, so main now has everything from Shield v21 onwards. Not yet installed on the Shield (it needs the network-debugging prompt accepted again).
