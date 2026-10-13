# Tobyfin

Tobyfin is a customized family of Jellyfin apps: the Shield app, the Phone app and the web client they share. They all connect to ordinary Jellyfin servers.

## Apps and devices

**Shield app**:
The Tobyfin app for Android TV, built from `android-tv/`. It runs on the household's NVIDIA Shield.
_Avoid_: TV app, Android TV app

**Phone app**:
The Tobyfin Android phone app: a native shell that displays the web client.
_Avoid_: mobile app

**Web client**:
The Jellyfin web interface customized for Tobyfin, built from `web/`. The Phone app bundles a copy of it.
_Avoid_: web app, frontend

**Release**:
A numbered build of one app, named after where it runs, such as "Shield v21" or "Phone v18". The Shield app and the Phone app are numbered separately.

## Servers, accounts and profiles

**Server**:
A Jellyfin server the apps connect to, such as oasis or Navarro.

**Saved server**:
A server this device remembers. A server is forgotten once none of its profiles are left.

**Found server**:
A server the network scan discovered that this device has not saved yet.
_Avoid_: discovered server

**Account**:
A Jellyfin user on one server. A person with logins on two servers has two accounts.
_Avoid_: user (except when quoting Jellyfin), login

**Profile**:
An account saved on this device, shown as a card in Who's watching. Removing a profile forgets it on this device only; the account is unchanged.
_Avoid_: user, saved user

## Choosing a profile

**Who's watching**:
The Shield app's screen for choosing a profile. It appears on launch and after 30 minutes away when more than one profile is saved.
_Avoid_: profile picker, user select (the shorthand "picker" is fine)

**Add-profile panel**:
The card opened from Who's watching's Add profile tile. It is used to choose or add a server, then sign in with Quick Connect or a password.
_Avoid_: login screen, add user dialog

**Quick Connect**:
Jellyfin's code-based sign-in: the device shows a code and a signed-in phone or browser approves it.

## Home

**Home**:
The first screen after choosing a profile, made of rows.

**Discovery**:
The customizable set of rows on Home, chosen and ordered per account and device.

**On Home**:
The rows Discovery shows, in their chosen order. Every row is either On Home or in Add to Home.
_Avoid_: shown, visible, enabled

**Add to Home**:
The rows not on Home that can be added: built-in rows taken off Home and the server's genres not added yet. Adding one puts it at the end of On Home.
_Avoid_: more rows, hidden, removed, disabled

**Row**:
One horizontal shelf of items on Home.
_Avoid_: section

**Genre row**:
A row showing one genre, built in or added by the person.

## Subtitles

**Subtitle track**:
One set of subtitles for a video, listed in the player's CC menu. It is either embedded in the video file or an external file next to it on the server.
_Avoid_: captions, subs

**Subtitle provider**:
An online subtitle source, such as OpenSubtitles, that a server searches through a plugin. Without one, the server has nothing to search.

**Find other subtitles**:
Searching the server's subtitle providers from the player for a replacement subtitle track, then switching to it. The downloaded track is saved on the server for every account.
_Avoid_: subtitle search, download subtitles

**Subtitle offset**:
A shift of the current subtitle track earlier or later, in seconds, for this playback only.
_Avoid_: subtitle timing, subtitle delay, sync

**Release name**:
The name of the specific encode of a video, such as `Movie.2019.1080p.BluRay-GROUP`. Subtitles made for the same release name usually line up without any offset.

**SDH**:
Subtitles for the deaf and hard of hearing, which also describe sounds like "[door creaks]".
_Avoid_: hearing impaired, CC

**Forced subtitles**:
A subtitle track that only covers foreign-language dialogue and on-screen text, not the main audio.
