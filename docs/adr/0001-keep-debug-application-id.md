# Keep Jellyfin's `.debug` application ID for Tobyfin builds

Tobyfin's Shield app and Phone app are built from each upstream project's debug build type, which adds a `.debug` suffix to the application ID (`org.jellyfin.androidtv.debug`, `org.jellyfin.mobile.debug`). We keep that suffix on purpose. It lets Tobyfin install next to the stock Jellyfin apps instead of replacing them, and every installed release already uses it. Changing the ID would make Android treat Tobyfin as a new app: people would lose their saved servers, profiles and settings, and would have to reinstall.

The debug build type is configured like a release: it is not debuggable and is minified with R8 (Shield v16 onwards). So "debug" in the name says nothing about how the build behaves.
