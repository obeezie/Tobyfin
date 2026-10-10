vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({ default: { currentApiClient: () => undefined } }));
import type { Api } from '@jellyfin/sdk/lib/api';
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';

vi.mock('hooks/useApi', () => ({ useApi: vi.fn() }));
vi.mock('components/homesections/sections/fetchMovieSuggestions', () => ({ fetchMovieSuggestions: vi.fn() }));

import { getMovieRecommendationsQuery } from './useMovieRecommendations';

describe('stable movie suggestions', () => {
    it('keeps loaded rows when websocket events invalidate active queries', async () => {
        const client = new QueryClient();
        const rows = [{ CategoryId: 'seed', BaselineItemName: '', Title: 'Action', RecommendationType: undefined,
            Items: [{ Id: 'movie' }], LoadMore: async () => ({ Items: [], HasMore: false }) }];
        const fetch = vi.fn(async () => rows);
        const options = getMovieRecommendationsQuery({ basePath: 'https://test.local' } as Api, { userId: 'user', parentId: 'library' });
        const observer = new QueryObserver(client, { ...options, queryFn: fetch });
        const unsubscribe = observer.subscribe(() => undefined);
        await vi.waitFor(() => expect(observer.getCurrentResult().data).toBe(rows));
        await client.invalidateQueries();
        await client.invalidateQueries({ queryKey: ['MovieRecommendations'], refetchType: 'active' });
        expect(fetch).toHaveBeenCalledTimes(1);
        expect(observer.getCurrentResult().data).toBe(rows);
        expect(options.refetchOnWindowFocus).toBe(false);
        expect(options.refetchOnReconnect).toBe(false);
        unsubscribe();
        client.clear();
    });
});
