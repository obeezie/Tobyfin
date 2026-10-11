import type { BaseItemDto } from '@jellyfin/sdk/lib/generated-client/models/base-item-dto';
import type { ApiClient } from 'jellyfin-apiclient';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('apps/legacy/features/libraries/api/useLatestMedia', () => ({ getLatestMediaQuery: vi.fn() }));
vi.mock('components/cardbuilder/cardBuilder', () => ({ default: {
    getCardsHtml: ({ items }: { items: BaseItemDto[] }) => items.map(movie => '<span class="card" data-id="' + movie.Id + '"></span>').join('')
} }));
vi.mock('components/cardbuilder/utils/shape', () => ({ getPortraitShape: vi.fn(), getSquareShape: vi.fn(), getBackdropShape: vi.fn() }));
vi.mock('components/layoutManager', () => ({ default: { tv: false } }));
vi.mock('components/router/appRouter', () => ({ appRouter: { getRouteUrl: () => '#/list' } }));
vi.mock('lib/globalize', () => ({ default: { translate: (key: string) => key } }));
vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({ default: { getApi: vi.fn() } }));
vi.mock('utils/query/queryClient', () => ({ queryClient: { fetchQuery: vi.fn() } }));
vi.mock('components/images/imageLoader', () => ({ default: { lazyChildren: vi.fn() } }));

import { loadRecentlyAdded } from './recentlyAdded';
import type { SectionContainerElement } from './section';

afterEach(() => {
    document.body.innerHTML = '';
    vi.unstubAllGlobals();
});

describe('Recently Added on Home', () => {
    it.each(['movies', 'tvshows'] as const)('pages %s past the initial limit without hiding watched additions', async (collectionType) => {
        vi.stubGlobal('IntersectionObserver', undefined);
        const movies = Array.from({ length: 90 }, (_, index) => ({ Id: String(index), UserData: { Played: index % 2 === 0 } }));
        const getItems = vi.fn(async (_user: string, query: Record<string, unknown>) => ({
            Items: movies.slice(Number(query.StartIndex || 0), Number(query.StartIndex || 0) + Number(query.Limit)), TotalRecordCount: movies.length
        }));
        const api = { getItems, serverId: () => 'server', getCurrentUserId: () => 'user' } as unknown as ApiClient;
        const elem = document.createElement('div');
        document.body.appendChild(elem);
        loadRecentlyAdded(elem, api, { Id: 'user', Configuration: { LatestItemsExcludes: ['excluded'] } }, [
            { Id: 'excluded', CollectionType: 'movies' },
            { Id: 'movies', Name: 'Library', CollectionType: collectionType }
        ], { enableOverflow: true });
        const container = elem.querySelector('.itemsContainer') as SectionContainerElement;
        const initial = await container.fetchData() as BaseItemDto[];
        expect(initial).toHaveLength(16);
        expect(initial[0].UserData?.Played).toBe(true);
        container.innerHTML = container.getItemsHtml(initial);
        container.afterRefresh?.();
        for (const expected of [40, 80, 90]) {
            container.querySelector('button')?.click();
            await vi.waitFor(() => expect(container.querySelectorAll('.card')).toHaveLength(expected));
        }
        expect(Array.from(container.querySelectorAll('.card')).map(card => card.getAttribute('data-id'))).toEqual(movies.map(movie => movie.Id));
        expect(container.querySelector('button')).toBeNull();
        expect(getItems.mock.calls.every(([, query]) => query.ParentId === 'movies' && query.IncludeItemTypes === (collectionType === 'tvshows' ? 'Series' : 'Movie') && query.SortBy === (collectionType === 'tvshows' ? 'DateLastContentAdded,SortName' : 'DateCreated,SortName') && query.SortOrder === 'Descending' && query.IsPlayed === undefined)).toBe(true);
    });
});
