# The three apps are git subtrees of their Jellyfin upstreams

`android-tv/`, `android/` and `web/` were added to this repository with `git subtree add`, and each keeps the full history of its upstream Jellyfin project (jellyfin-androidtv, jellyfin-android, jellyfin-web) plus Tobyfin's changes. We chose one repository with subtrees over three forks so that the apps and the web client the Phone app bundles change together, while new Jellyfin releases can still be merged into each folder with `git subtree pull`.

Do not move these folders, flatten or rewrite their history, or squash the subtree merges. That would break later upstream merges.
