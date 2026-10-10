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
});
