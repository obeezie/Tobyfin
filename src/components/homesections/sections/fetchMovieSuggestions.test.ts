vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({ default: { currentApiClient: () => undefined } }));
/* eslint-disable compat/compat -- This adapter test runs in Node.js, not a legacy browser. */
import { Api } from '@jellyfin/sdk/lib/api';
import axios, { type AxiosAdapter } from 'axios';
import { describe, expect, it, vi } from 'vitest';

import { fetchMovieSuggestions, fetchMovieItems } from './fetchMovieSuggestions';

describe('Suggestions SDK requests', () => {
    it('loads genre shelves and keeps pagination working after the initial query is aborted', async () => {
        const controller = new AbortController();
        const requests: { url: URL; hasSignal: boolean }[] = [];
        const movies = Array.from({ length: 80 }, (_, index) => ({ Id: String(index), Name: 'Movie ' + index, Genres: ['Action'] }));
        const adapter: AxiosAdapter = async config => {
            const url = new URL(config.url || '', 'https://test.local');
            requests.push({ url, hasSignal: !!config.signal });
            let items = movies;
            if (url.searchParams.get('isPlayed') === 'true') {
                items = [{ Id: 'seed', Name: 'Seed', Genres: ['Action'] }];
            } else if (url.searchParams.get('isFavorite') === 'true') {
                items = [];
            }
            const start = Number(url.searchParams.get('startIndex') || 0);
            const limit = Number(url.searchParams.get('limit') || 20);
            return { data: { Items: items.slice(start, start + limit), TotalRecordCount: items.length }, status: 200, statusText: 'OK', headers: {}, config };
        };
        const api = new Api('https://test.local', { name: 'Test', version: '1' }, { id: 'test', name: 'Test' }, undefined, axios.create({ adapter }));
        const rows = await fetchMovieSuggestions(api, { userId: 'user', parentId: 'library', itemLimit: 20 }, { signal: controller.signal });
        expect(rows.map(row => row.Title)).toEqual(['Action', 'Comedy', 'Horror', 'Suspense', 'Drama',
            'Sci-Fi', 'Fantasy', 'Adventure', 'Crime & Mystery', 'Animation', 'Family', 'Romance', 'Documentaries']);
        expect(requests.some(request => request.url.searchParams.getAll('genres').join('|') === 'Suspense|Thriller')).toBe(true);
        expect(requests.some(request => request.url.searchParams.getAll('genres').join('|') === 'Crime|Mystery')).toBe(true);
        expect(requests.every(request => request.url.searchParams.get('isPlayed') === 'false'
            && !request.url.searchParams.has('isFavorite'))).toBe(true);
        controller.abort();
        const recommendation = rows.find(row => row.Title === 'Action')!;
        const loadMore = recommendation.LoadMore;
        const first = await loadMore();
        const second = await loadMore();
        expect([...recommendation.Items, ...first.Items, ...second.Items]).toHaveLength(80);
        expect(second.HasMore).toBe(false);
        expect(requests.filter(request => request.url.searchParams.has('startIndex')).every(request => !request.hasSignal)).toBe(true);
    });
    it('preserves exact custom genre IDs through the SDK adapter', async () => {
        let request = '';
        const adapter: AxiosAdapter = async config => {
            request = config.url || '';
            return { data: { Items: [] }, status: 200, statusText: 'OK', headers: {}, config };
        };
        const api = new Api('https://test.local', { name: 'Test', version: '1' }, { id: 'test', name: 'Test' }, undefined, axios.create({ adapter }));
        await fetchMovieItems(api, 'user', { GenreIds: '0123456789abcdef0123456789abcdef', ParentId: 'library' });
        const params = new URL(request, 'https://test.local').searchParams;
        expect(params.get('genreIds')).toBe('0123456789abcdef0123456789abcdef');
        expect(params.has('genres')).toBe(false);
    });
});
/* eslint-enable compat/compat */
