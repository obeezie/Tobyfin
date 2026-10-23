import { describe, expect, it, vi } from 'vitest';
import { loadGenreCatalog, searchGenres } from './genreCatalog';

describe('server genre catalog', () => {
    it('pages through server-limited results and searches all genres', async () => {
        const getGenres = vi.fn()
            .mockResolvedValueOnce({ Items: [{ Id: 'one', Name: 'Western' }], TotalRecordCount: 2 })
            .mockResolvedValueOnce({ Items: [{ Id: 'two', Name: 'History' }], TotalRecordCount: 2 });
        const result = await loadGenreCatalog({ getGenres, getCurrentUserId: () => 'user' });
        expect(getGenres.mock.calls[1][1].StartIndex).toBe(1);
        expect(getGenres.mock.calls[0][0]).toBe('user');
        expect(searchGenres(result, '  HIST ')).toEqual([expect.objectContaining({ title: 'History' })]);
    });
    it('does not publish results for a closed editor or switched account', async () => {
        let current = true;
        const getGenres = vi.fn(async () => {
            current = false;
            return { Items: [{ Id: 'one', Name: 'Western' }], TotalRecordCount: 1 };
        });
        expect(await loadGenreCatalog({ getGenres, getCurrentUserId: () => 'user' }, () => current)).toEqual([]);
    });
    it('refreshes additions and stops a server that repeats its first page', async () => {
        const api = { getCurrentUserId: () => 'user', getGenres: vi.fn().mockResolvedValue({
            Items: [{ Id: 'one', Name: 'Western' }], TotalRecordCount: 400
        }) };
        expect(await loadGenreCatalog(api)).toHaveLength(1);
        expect(api.getGenres).toHaveBeenCalledTimes(2);
        api.getGenres.mockResolvedValue({ Items: [{ Id: 'one', Name: 'Western' }, { Id: 'two', Name: 'Music' }], TotalRecordCount: 2 });
        expect(await loadGenreCatalog(api)).toHaveLength(2);
    });
    it('asks each movie library for its genres and merges them', async () => {
        const getUserViews = vi.fn().mockResolvedValue({ Items: [
            { Id: 'films', CollectionType: 'movies' }, { Id: 'shows', CollectionType: 'tvshows' }, { Id: 'kids', CollectionType: 'movies' }
        ] });
        const getGenres = vi.fn(async (_user: string, query: { ParentId?: string }) => (query.ParentId === 'films' ?
            { Items: [{ Id: 'one', Name: 'Western' }, { Id: 'two', Name: 'Drama' }], TotalRecordCount: 2 } :
            { Items: [{ Id: 'two', Name: 'Drama' }, { Id: 'three', Name: 'Animation' }], TotalRecordCount: 2 }));
        const result = await loadGenreCatalog({ getGenres, getUserViews, getCurrentUserId: () => 'user' });
        expect(getGenres.mock.calls.map(call => call[1].ParentId)).toEqual(['films', 'kids']);
        expect(result.map(genre => genre.title)).toEqual(['Animation', 'Drama', 'Western']);
    });
    it('falls back to a server-wide request when the movie libraries have no genres', async () => {
        const getUserViews = vi.fn().mockResolvedValue({ Items: [{ Id: 'films', CollectionType: 'movies' }] });
        const getGenres = vi.fn(async (_user: string, query: { ParentId?: string }) => (query.ParentId ?
            { Items: [], TotalRecordCount: 0 } : { Items: [{ Id: 'one', Name: 'Western' }], TotalRecordCount: 1 }));
        expect(await loadGenreCatalog({ getGenres, getUserViews, getCurrentUserId: () => 'user' })).toHaveLength(1);
    });
});
