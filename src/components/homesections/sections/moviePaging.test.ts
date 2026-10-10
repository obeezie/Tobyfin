import { describe, expect, it, vi } from 'vitest';

import { createMoviePager, getDiscoveryGenres } from './moviePaging';

describe('movie row pagination', () => {
    const movies = Array.from({ length: 105 }, (_, index) => ({ Id: String(index) }));

    it('continues beyond 20, uses stable offsets and stops at library exhaustion', async () => {
        const fetch = vi.fn(async (query: Record<string, unknown>) => ({
            Items: movies.slice(Number(query.StartIndex), Number(query.StartIndex) + Number(query.Limit)), TotalRecordCount: movies.length
        }));
        const more = createMoviePager(fetch, { ParentId: 'library', SortBy: 'SortName' }, movies.slice(0, 16));
        const first = await more();
        const second = await more();
        const third = await more();
        expect([...first.Items, ...second.Items, ...third.Items]).toHaveLength(89);
        expect(first.HasMore).toBe(true);
        expect(third.HasMore).toBe(false);
        expect((await more()).Items).toEqual([]);
        expect(fetch.mock.calls.map(([query]) => query.StartIndex)).toEqual([0, 40, 80]);
        expect(fetch.mock.calls.every(([query]) => query.SortBy === 'SortName')).toBe(true);
    });

    it('shares concurrent requests and retries failed pages without skipping them', async () => {
        const fetch = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ Items: movies.slice(0, 3), TotalRecordCount: 3 });
        const more = createMoviePager(fetch, { SortBy: 'SortName' });
        await expect(more()).rejects.toThrow('offline');
        const first = more();
        expect(more()).toBe(first);
        expect((await first).Items).toHaveLength(3);
        expect(fetch.mock.calls.map(([query]) => query.StartIndex)).toEqual([0, 0]);
    });

    it('skips sparse pages and duplicate entries while retaining related movies', async () => {
        const fetch = vi.fn(async (query: Record<string, number>) => ({
            Items: query.StartIndex === 0 ? movies.slice(0, 40) : [{ Id: 'related' }, { Id: 'related' }], TotalRecordCount: 42
        }));
        const more = createMoviePager(fetch, { SortBy: 'SortName' }, [], movie => movie.Id === 'related');
        expect((await more()).Items).toEqual([{ Id: 'related' }]);
        expect(fetch).toHaveBeenCalledTimes(2);
    });

    it('includes all requested genres and maps metadata aliases', () => {
        const genres = getDiscoveryGenres();
        expect(genres.map(genre => genre.title)).toEqual(['Action', 'Comedy', 'Horror', 'Suspense', 'Drama',
            'Sci-Fi', 'Fantasy', 'Adventure', 'Crime & Mystery', 'Animation', 'Family', 'Romance', 'Documentaries']);
        expect(genres[3].filter).toBe('Suspense|Thriller');
        expect(genres[5].filter).toBe('Science Fiction|Sci-Fi');
        expect(genres[8].filter).toBe('Crime|Mystery');
        expect(genres[12].filter).toBe('Documentary|Documentaries');
    });

    it('extends Recently Added in newest-first order while retaining watched movies', async () => {
        const newestFirst = movies.map((movie, index) => ({ ...movie, DateCreated: 105 - index, UserData: { Played: index % 2 === 0 } }));
        const fetch = vi.fn(async (query: Record<string, unknown>) => ({
            Items: newestFirst.slice(Number(query.StartIndex), Number(query.StartIndex) + Number(query.Limit)), TotalRecordCount: newestFirst.length
        }));
        const more = createMoviePager(fetch, { SortBy: 'DateCreated,SortName', SortOrder: 'Descending', ParentId: 'library' }, newestFirst.slice(0, 20));
        const first = await more();
        const second = await more();
        const third = await more();
        const all = [...newestFirst.slice(0, 20), ...first.Items, ...second.Items, ...third.Items];
        expect(all.map(movie => movie.Id)).toEqual(newestFirst.map(movie => movie.Id));
        expect(all.filter(movie => movie.UserData.Played)).toHaveLength(53);
        expect(fetch.mock.calls.every(([query]) => query.SortBy === 'DateCreated,SortName' && query.SortOrder === 'Descending' && query.ParentId === 'library' && query.IsPlayed === undefined)).toBe(true);
        expect(third.HasMore).toBe(false);
    });

    it('mixes disjoint library ranges without losing matches or repeating initial cards', async () => {
        const fetch = vi.fn(async (query: Record<string, number>) => ({
            Items: movies.slice(query.StartIndex, query.StartIndex + query.Limit), TotalRecordCount: movies.length
        }));
        const more = createMoviePager(fetch, {}, movies.slice(0, 20), undefined, undefined, { random: () => 0 });
        const first = await more();
        const second = await more();
        const third = await more();
        const all = [...movies.slice(0, 20), ...first.Items, ...second.Items, ...third.Items];
        expect(all).toHaveLength(105);
        expect(new Set(all.map(movie => movie.Id)).size).toBe(105);
        expect(fetch.mock.calls.filter(([query]) => query.Limit > 1).map(([query]) => query.StartIndex)).toEqual([10, 36, 62, 88, 20, 46, 72, 98, 0, 26, 52, 78]);
        expect(fetch.mock.calls.every(([query]) => query.Limit <= 10)).toBe(true);
        expect(third.HasMore).toBe(false);
    });

    it('retries the same mixed batch after a partial request failure', async () => {
        let fail = true;
        const fetch = vi.fn(async (query: Record<string, number>) => {
            if (query.StartIndex === 30 && fail) {
                fail = false;
                throw new Error('offline');
            }
            return { Items: movies.slice(query.StartIndex, query.StartIndex + query.Limit), TotalRecordCount: 80 };
        });
        const more = createMoviePager(fetch, {}, [], undefined, undefined, { random: () => 0 });
        await expect(more()).rejects.toThrow('offline');
        const first = await more();
        const second = await more();
        expect([...first.Items, ...second.Items]).toHaveLength(80);
        expect(new Set([...first.Items, ...second.Items].map(movie => movie.Id)).size).toBe(80);
        expect(fetch.mock.calls.slice(1, 9).map(([query]) => query.StartIndex)).toEqual([10, 30, 50, 70, 10, 30, 50, 70]);
        expect(second.HasMore).toBe(false);
    });

    it('includes widely separated starting letters in every full discovery batch', async () => {
        const alphabetical = Array.from({ length: 520 }, (_, index) => ({
            Id: String(index), Name: String.fromCharCode(65 + Math.floor(index / 20)) + ' movie ' + index
        }));
        const fetch = vi.fn(async (query: Record<string, number>) => ({
            Items: alphabetical.slice(query.StartIndex, query.StartIndex + query.Limit), TotalRecordCount: alphabetical.length
        }));
        const more = createMoviePager(fetch, {}, [], undefined, undefined, { random: () => 0 });
        const seen = new Set();
        let hasMore = true;
        while (hasMore) {
            const batch = await more();
            expect(batch.Items).toHaveLength(40);
            expect(new Set(batch.Items.map((movie: { Name: string }) => movie.Name[0])).size).toBeGreaterThanOrEqual(4);
            batch.Items.forEach((movie: { Id: string }) => {
                seen.add(movie.Id);
            });
            hasMore = batch.HasMore;
        }
        expect(seen.size).toBe(520);
    });
});
