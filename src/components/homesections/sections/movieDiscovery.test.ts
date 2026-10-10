// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('components/cardbuilder/cardBuilder', () => ({ default: { getCardsHtml: vi.fn() } }));
vi.mock('components/cardbuilder/utils/shape', () => ({ getPortraitShape: () => 'portrait' }));
vi.mock('components/images/imageLoader', () => ({ default: { lazyChildren: vi.fn() } }));
vi.mock('lib/globalize', () => ({ default: { translate: (key: string, ...args: string[]) => [key, ...args].join(' ') } }));

import { loadMovieDiscovery } from './movieDiscovery';

afterEach(() => {
    document.body.innerHTML = '';
});

describe('movie discovery home section', () => {
    it('respects excluded libraries and renders only genres without consulting viewing history', async () => {
        const elem = document.createElement('div');
        document.body.appendChild(elem);
        const api = {
            getCurrentUserId: () => 'user',
            getItems: vi.fn(async (_user: string, query: Record<string, unknown>) => ({ Items: query.Genres === 'Crime|Mystery' ?
                [{ Id: 'crime' }] : [] })),
            getGenres: vi.fn(async () => ({ Items: [] })),
            getUrl: () => 'recommendations',
            getJSON: vi.fn(async () => { throw new Error('unavailable'); })
        };
        await loadMovieDiscovery(elem, api, {
            Id: 'user', Configuration: { LatestItemsExcludes: ['excluded'] }
        }, [
            { Id: 'excluded', CollectionType: 'movies' },
            { Id: 'music', CollectionType: 'music' },
            { Id: 'allowed', Name: '<Movies>', CollectionType: 'movies' }
        ], { enableOverflow: true });
        expect(api.getItems.mock.calls.every(([, query]) => query.ParentId === 'allowed')).toBe(true);
        expect(api.getItems.mock.calls.every(([, query]) => query.Genres && query.IsPlayed === false && query.IsFavorite === undefined)).toBe(true);
        expect(api.getGenres).not.toHaveBeenCalled();
        expect(api.getJSON).not.toHaveBeenCalled();
        expect(elem.querySelectorAll('.verticalSection')).toHaveLength(1);
        expect(elem.querySelector('h2')?.textContent).toContain('Crime & Mystery');
        expect(elem.querySelector('h2')?.textContent).toContain('<Movies>');
        expect(elem.querySelector('h2')?.innerHTML).toContain('&lt;Movies&gt;');
    });

    it('does not render into a home section destroyed during navigation', async () => {
        const elem = document.createElement('div');
        document.body.appendChild(elem);
        const api = {
            getCurrentUserId: () => 'user',
            getItems: async () => {
                elem.remove();
                return { Items: [{ Id: 'movie', CommunityRating: 9 }] };
            },
            getGenres: async () => ({ Items: [] }),
            getUrl: () => 'recommendations',
            getJSON: async () => []
        };
        await loadMovieDiscovery(elem, api, { Id: 'user' }, [
            { Id: 'movies', Name: 'Movies', CollectionType: 'movies' }
        ], { enableOverflow: true });
        expect(elem.children).toHaveLength(0);
    });
});
