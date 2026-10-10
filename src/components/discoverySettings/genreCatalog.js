// Fetch the complete accessible movie-genre catalog; search is instant and local.
export async function loadGenreCatalog(api, isCurrent = () => true) {
    const genres = new Map();
    let start = 0;
    while (isCurrent()) {
        const result = await api.getGenres(api.getCurrentUserId(), {
            IncludeItemTypes: 'Movie', SortBy: 'SortName', SortOrder: 'Ascending',
            StartIndex: start, Limit: 200, EnableImages: false, EnableTotalRecordCount: true
        });
        if (!isCurrent()) return [];
        const items = result.Items || [];
        const previous = genres.size;
        for (const item of items) {
            if (item.Id && item.Name) {
                const id = item.Id.replace(/-/g, '').toLowerCase();
                genres.set(id, { id: 'genre:server:' + id, genreId: id, title: item.Name });
            }
        }
        start += items.length;
        if (!items.length || genres.size === previous || (typeof result.TotalRecordCount === 'number' ?
            start >= result.TotalRecordCount : items.length < 200)) break;
    }
    return [...genres.values()].sort((a, b) => a.title.localeCompare(b.title));
}
export function searchGenres(genres, search) {
    const query = search.trim().toLocaleLowerCase();
    return genres.filter(genre => genre.title.toLocaleLowerCase().includes(query));
}
