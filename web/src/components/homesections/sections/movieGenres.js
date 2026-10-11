import { createMoviePager, getDiscoveryGenres, shuffleItems } from './moviePaging';

// Every discovery row is a genre, independent of watch history or favorites.
export async function loadMovieSuggestions(fetchMovies, parentId, itemLimit = 20, genres = getDiscoveryGenres()) {
    const shelves = await Promise.all(genres.map(async genre => {
        const query = { ParentId: parentId, IncludeItemTypes: 'Movie', Recursive: true,
            ...(genre.genreId ? { GenreIds: genre.genreId } : { Genres: genre.filter }), IsPlayed: false, EnableUserData: true,
            Fields: 'PrimaryImageAspectRatio,MediaSourceCount', EnableTotalRecordCount: false };
        try {
            const result = await fetchMovies({ ...query, SortBy: 'Random', Limit: itemLimit });
            const distinct = new Map((result.Items || []).filter(movie => movie.Id && !movie.UserData?.Played)
                .map(movie => [movie.Id, movie]));
            const items = shuffleItems([...distinct.values()]);
            return { CategoryId: genre.id || 'genre-' + genre.title, Title: genre.title, Items: items,
                LoadMore: createMoviePager(fetchMovies, query, items, movie => !movie.UserData?.Played) };
        } catch (error) {
            console.warn('[MovieDiscovery] Genre shelf unavailable: ' + genre.title, error);
            return undefined;
        }
    }));
    return shelves.filter(row => row !== undefined).filter(row => row.Items.length);
}
