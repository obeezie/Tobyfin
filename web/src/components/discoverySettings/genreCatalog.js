/**
 * Fetch the complete accessible movie-genre catalog; search is instant and local. Genres are asked for per movie
 * library, as Jellyfin's own clients do: Jellyfin 10.11 can answer a server-wide request with no genres. The
 * server-wide request remains the fallback for servers without a movie library.
 */
export async function loadGenreCatalog(api, isCurrent = () => true) {
    const genres = new Map();
    const userId = api.getCurrentUserId();
    const views = api.getUserViews ? await api.getUserViews({}, userId) : null;
    if (!isCurrent()) return [];
    const libraries = (views?.Items || []).filter(view => view.CollectionType === 'movies').map(view => view.Id);
    for (const library of libraries) {
        await loadGenres(api, userId, library, genres, isCurrent);
    }
    if (!genres.size) await loadGenres(api, userId, undefined, genres, isCurrent);
    if (!isCurrent()) return [];
    return [...genres.values()].sort((a, b) => a.title.localeCompare(b.title));
}
async function loadGenres(api, userId, parentId, genres, isCurrent) {
    // Genres seen in this library: a page with none new means the server is repeating itself.
    const seen = new Set();
    let start = 0;
    while (isCurrent()) {
        const result = await api.getGenres(userId, {
            ...(parentId ? { ParentId: parentId } : {}),
            IncludeItemTypes: 'Movie', SortBy: 'SortName', SortOrder: 'Ascending',
            StartIndex: start, Limit: 200, EnableImages: false, EnableTotalRecordCount: true
        });
        if (!isCurrent()) return;
        const items = result.Items || [];
        const previous = seen.size;
        for (const item of items) {
            if (item.Id && item.Name) {
                const id = item.Id.replace(/-/g, '').toLowerCase();
                seen.add(id);
                genres.set(id, { id: 'genre:server:' + id, genreId: id, title: item.Name });
            }
        }
        start += items.length;
        if (!items.length || seen.size === previous || (typeof result.TotalRecordCount === 'number' ?
            start >= result.TotalRecordCount : items.length < 200)) break;
    }
}
export function searchGenres(genres, search) {
    const query = search.trim().toLocaleLowerCase();
    return genres.filter(genre => genre.title.toLocaleLowerCase().includes(query));
}
