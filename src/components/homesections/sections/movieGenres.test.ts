import { describe, expect, it, vi } from 'vitest';

import { loadMovieSuggestions } from './movieGenres';

const titles = ['Action', 'Comedy', 'Horror', 'Suspense', 'Drama', 'Sci-Fi', 'Fantasy', 'Adventure',
    'Crime & Mystery', 'Animation', 'Family', 'Romance', 'Documentaries'];

describe('genre-only movie suggestions', () => {
    it('loads all thirteen shelves without requesting history, favorites or recommendation candidates', async () => {
        const fetchMovies = vi.fn(async (query: Record<string, unknown>) => ({ Items: [{ Id: String(query.Genres) }] }));
        const rows = await loadMovieSuggestions(fetchMovies, 'library', 16);
        expect(rows.map(row => row.Title)).toEqual(titles);
        expect(rows.every(row => typeof row.LoadMore === 'function')).toBe(true);
        expect(fetchMovies).toHaveBeenCalledTimes(13);
        expect(fetchMovies.mock.calls.every(([query]) => query.ParentId === 'library' && query.Genres
            && query.IsPlayed === false && query.IsFavorite === undefined && query.SortBy === 'Random' && query.Limit === 16)).toBe(true);
        expect(fetchMovies.mock.calls.map(([query]) => query.Genres)).toContain('Crime|Mystery');
        expect(fetchMovies.mock.calls.map(([query]) => query.Genres)).toContain('Science Fiction|Sci-Fi');
    });

    it('omits empty shelves and keeps other genres when one request fails', async () => {
        const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        try {
            const rows = await loadMovieSuggestions(async (query: Record<string, unknown>) => {
                if (query.Genres === 'Horror') throw new Error('offline');
                return { Items: query.Genres === 'Fantasy' ? [{ Id: 'fantasy' }] : [] };
            }, 'library');
            expect(rows.map(row => row.Title)).toEqual(['Fantasy']);
        } finally {
            warning.mockRestore();
        }
    });

    it('deduplicates each shelf and preserves multi-genre movies across shelves', async () => {
        const rows = await loadMovieSuggestions(async () => ({ Items: [
            { Id: 'shared' }, { Id: 'shared' }, { Id: 'watched', UserData: { Played: true } }, { Name: 'Missing ID' }
        ] }), 'library');
        expect(rows).toHaveLength(13);
        expect(rows.every(row => row.Items.length === 1 && row.Items[0].Id === 'shared')).toBe(true);
    });
});
