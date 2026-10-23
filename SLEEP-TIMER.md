# Tobyfin sleep timer

Phone v12 and Shield v11 add a moon button to video playback controls. On the phone native player it sits at the right of the title bar; on Shield it is in the secondary playback actions. The embedded web player also has the moon button.

Choose 15, 30, 60 or 90 minutes, enter a whole number from 1 to 1440 minutes, or stop after the current episode/movie. Reopen the menu to see time remaining, add 15 minutes, or cancel. Opening the menu does not pause the player. Countdown is based on elapsed time and includes pauses and buffering. Expiration pauses at the current position; it does not mark an unfinished title watched. End-of-video mode prevents automatic next-episode playback. Normal episode handoffs retain timed countdowns; closing a playback session clears the timer. An explicit Play after expiry resumes normally. Timers are local and are not saved across app restarts or synchronized to other devices. Casting and external players have their own controls.

The app pauses playback; the phone/Shield controls when its screen sleeps. No new permissions or device power commands are used.

## Implementation

The native SleepTimerState is an Android-independent deadline and end-of-item state machine. SleepTimerController owns the monotonic clock tick and playback callback. Phone ownership is the PlayerViewModel. Shield ownership is the existing PlaybackControllerContainer so timed sessions survive Next Up and Still Watching screens. Owner identity prevents outgoing screens from clearing a timer already adopted by the next screen. Late stream preparation checks the expired latch, and only explicit resume clears it. Dialogs own only their status-refresh callbacks.

The browser timer belongs to PlaybackManager and gates automatic queue advancement. Its compact dialog uses the existing keyboard/back/focus handling. Native timers are used for native playback; the web timer applies to the local embedded web player.

## Included Discovery and icon changes

Default and added genre rows both have Remove. Removed default IDs are persisted separately from hidden IDs so normalization does not insert them again. Add Genres offers restoration, even without a matching live server result. Restore defaults resets all removals. Continue Watching, Up Next and recently-added rows still use Show/Hide.

Recently Added TV loads unique series via the regular Items API, newest episode/content additions first, without excluding watched shows. It reuses the stable incremental pager on Home/Discovery and library suggestions. Appended cards preserve existing row position; paging ends when all accessible shows have been loaded. A show appears once, rather than repeating a card for each episode.

Phone launcher foreground inset is reduced from 18dp to 12dp. The separate splash drawable and its padding are unchanged.

## Verification

Native timer state tests cover elapsed expiry, pause/buffering gaps, end-of-item autoplay blocking, cancellation, replacement, extension, invalid input and post-expiry reuse. Discovery tests cover persistence/restoration of defaults and TV query scope. Browser tests exercise the real Recently Added row through 90 cards for both movies and TV; headless Edge checks menu actions and layout at 320, 393, 852 and 960px widths. Build/signature/package checks and actual packaged web-asset checks are recorded in TOBYFIN.md. Physical phone/Shield playback and remote-control checks remain pending; no devices were connected for this change.
