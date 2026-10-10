# Movie discovery changes

Home movie discovery and both Movies → Suggestions layouts now show genre-only discovery rows: Action, Comedy, Horror, Suspense, Drama, Sci-Fi, Fantasy, Adventure, Crime & Mystery, Animation, Family, Romance, and Documentaries. Empty shelves are omitted. Suspense matches Suspense or Thriller; Sci-Fi matches Science Fiction or Sci-Fi; Crime & Mystery matches either Crime or Mystery; Documentaries matches Documentary or Documentaries.

“Because you watched,” favorites-based recommendations, director/cast recommendations, personalized “For You,” highly rated, and surprise shelves have been removed from discovery. Discovery does not fetch viewing history or favorites to select movies or genres. Other Home sections, Continue Watching, and Recently Added remain available. Genre discovery retains the existing unwatched filter; Recently Added includes watched movies.

The shared Home screen displays Movie discovery near the top automatically, including for profiles with saved `none` section slots. Profile → Home has a Show movie discovery on Home checkbox to disable it. Libraries excluded from latest media are also excluded from Home discovery.

Rows initially load 16 movies on Home and 20 on Suggestions, then load further matching movies as the row end becomes visible. A Load more movies button is available for keyboard use and browsers without IntersectionObserver; failed requests offer a retry. Genre pagination divides the matching library into four disjoint ranges and combines shuffled ten-title slices from each range. This mixes different parts of the alphabet within each batch. Each slice is visited once, partial failures retry the same batch, and already displayed titles are skipped. Movies are deduplicated within a shelf; a multi-genre movie can appear in more than one shelf.

Appending cards and resize notifications preserve native touch and desktop scroll positions. Native scrolling reads the actual touch offset instead of the desktop animation destination, avoiding a snap to item 1 or interruption of swipe momentum. Pending observers are cleaned up on navigation.

Modern Suggestions queries contain live pagination functions and are excluded from the persisted query cache. Their query key is versioned to prevent older cached recommendations from replacing the genre-only rows. The active query is static: focus, reconnect, and websocket invalidations do not reshuffle or remount the rows while browsing. Leaving the page releases the live paging controllers; reopening or deliberately reloading starts a fresh shuffled session. The legacy layout retains its rows after playback ends. Pagination detaches the initial request's abort signal so later pages continue working after initial-query cancellation.

Home's horizontal latest-movies rows and both Suggestions layouts query movies by DateCreated without a played filter. Watching a movie no longer removes it from Recently Added. These rows page through older additions in newest-first order. Other media types retain the existing Hide watched setting. Modern Recently Added keeps its displayed roster and pager stable when Continue Watching data updates elsewhere.

## Build and preview

Build using the project's required Node 24+ and npm 11+ with `npm ci` and `npm run build:production`. The resulting `dist` directory is the custom web client. This ZIP contains development version 13.0.0; check compatibility with the installed server before deployment. The remote installed frontend has not been deployed; the local preview uses the rebuilt `dist` files.

For the localhost preview, leave its static server running in `dist`, then reload the browser after rebuilding (Ctrl+Shift+R on desktop). The phone browser must load this custom frontend to receive the changes. Native clients or apps shipping their own frontend do not inherit source edits automatically.

## Validation

Twenty-five focused Vitest tests pass. Coverage includes all thirteen genre shelves and aliases, absence of history/favorites requests, omission of empty shelves, isolation of failed genre requests, duplicate removal, watched exclusions, library scoping, safe title rendering, navigation during loading, pagination beyond 20, mixed alphabetical ranges, concurrent requests, partial failure retries, exhaustion, persistent-cache compatibility, pagination after initial cancellation, native and RTL scroll offsets, desktop bounds, static-query invalidations, and Recently Added with watched titles. A 520-title fixture verifies mixed batches and full coverage; a Home fixture pages through 90 additions in order.

TypeScript passes. ESLint has no errors in changed files (three pre-existing FIXME warnings remain in the legacy controller). Production builds retain the project's bundle-size warnings and report no Git commit identifier because the ZIP has no Git metadata. Validation uses Node 24.19.0 and existing locked dependencies. Physical-phone verification has not been performed; touch behavior is covered by regression tests.

Earlier live checks verified mixed genre pagination reaching 130 unique movies and Recently Added reaching 40 movies while preserving the other rows' movie IDs and order. The Recently Added check used a 390 × 844 viewport with desktop pointer behavior; the temporary viewport override was reset afterward.

The genre-only rebuild was verified in the signed-in localhost preview: all thirteen genre headings appeared, each with twenty initial cards and a paging control, alongside Continue Watching and Recently Added. No personalized headings remained. The new Sci-Fi shelf grew to fifty-two unique movies while retaining its horizontal offset, and no browser warnings or errors were captured. Source and built ZIPs were refreshed after the rebuild.
