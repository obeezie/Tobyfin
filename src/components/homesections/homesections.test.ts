// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

type Deferred = { promise: Promise<void>; resolve: () => void };
function deferred(): Deferred {
    const result = {} as Deferred;
    result.promise = new Promise<void>(r => {
        result.resolve = r;
    });
    return result;
}

const pending: Record<string, Deferred> = {};
const fetches: Record<string, Deferred> = {};

vi.mock('../discoverySettings/discoverySettings', () => ({ getDiscoveryLayout: () => ({}), applyDiscoverySize: vi.fn() }));
vi.mock('../discoverySettings/discoveryLayout', () => ({ orderedGenres: () => [], genreId: (g: string) => g }));
vi.mock('components/layoutManager', () => ({ default: { tv: false } }));
vi.mock('hooks/api/useUserViews', () => ({ getUserViewsQuery: () => ({}) }));
vi.mock('lib/globalize', () => ({ default: { translate: (key: string) => key } }));
vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({ default: { getApi: () => ({}) } }));
vi.mock('utils/dashboard', () => ({ default: { navigate: vi.fn() } }));
vi.mock('utils/query/queryClient', () => ({ queryClient: { fetchQuery: async () => ({ Items: [{ Id: 'movies' }] }) } }));
vi.mock('components/loading/loading', () => ({ default: { show: vi.fn(), hide: vi.fn() } }));
vi.mock('elements/emby-button/paper-icon-button-light', () => ({}));
vi.mock('elements/emby-itemscontainer/emby-itemscontainer', () => ({}));
vi.mock('elements/emby-scroller/emby-scroller', () => ({}));
vi.mock('elements/emby-button/emby-button', () => ({}));
vi.mock('./homesections.scss', () => ({}));
vi.mock('./homeSectionSelection', () => ({
    selectHomeSections: () => ['resume', 'nextup', 'genre:action', 'smalllibrarytiles']
}));

// Section fakes mirror the real loaders: resume/next up start hidden and are revealed by their
// itemsContainer refresh; genre rows render a visible heading once their request completes; library
// tiles render synchronously.
function hiddenUntilRefreshed(elem: HTMLElement, title: string) {
    elem.classList.add('hide');
    elem.innerHTML = `<h2>${title}</h2><div class="itemsContainer"></div>`;
    const container = elem.querySelector('.itemsContainer') as HTMLElement & { resume?: () => Promise<void> };
    fetches[title] = deferred();
    container.resume = () => fetches[title].promise.then(() => elem.classList.remove('hide'));
}
vi.mock('./sections/resume', () => ({ loadResume: (elem: HTMLElement) => hiddenUntilRefreshed(elem, 'Continue Watching') }));
vi.mock('./sections/nextUp', () => ({ loadNextUp: (elem: HTMLElement) => hiddenUntilRefreshed(elem, 'Next Up') }));
vi.mock('./sections/movieDiscovery', () => ({
    loadMovieDiscovery: (elem: HTMLElement) => {
        pending.genre = deferred();
        return pending.genre.promise.then(() => {
            elem.innerHTML = '<h2>Action</h2><div class="itemsContainer"></div>';
        });
    }
}));
vi.mock('./sections/libraryTiles', () => ({
    loadLibraryTiles: (elem: HTMLElement) => {
        elem.innerHTML = '<h2>My Media</h2>';
    }
}));
vi.mock('./sections/activeRecordings', () => ({ loadRecordings: vi.fn() }));
vi.mock('./sections/libraryButtons', () => ({ loadLibraryButtons: vi.fn() }));
vi.mock('./sections/liveTv', () => ({ loadLiveTV: () => Promise.resolve() }));
vi.mock('./sections/recentlyAdded', () => ({ loadRecentlyAdded: vi.fn() }));

import { loadSections } from './homesections';

const FINAL_ORDER = ['Continue Watching', 'Next Up', 'Action', 'My Media'];

function isShown(node: Element | null, root: Element): boolean {
    for (let el = node; el && el !== root.parentNode; el = el.parentElement) {
        if (el.classList.contains('hide') || el.classList.contains('homeSectionsLoading')) return false;
    }
    return true;
}

function visibleHeadings(root: HTMLElement) {
    return Array.from(root.querySelectorAll('h2')).filter(h => isShown(h, root)).map(h => h.textContent);
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0));

afterEach(() => {
    document.body.innerHTML = '';
});

describe('home sections initial load', () => {
    it('never shows a later section while an earlier section is still loading', async () => {
        const root = document.createElement('div');
        document.body.appendChild(root);
        const apiClient = { serverId: () => 'server', getCurrentUserId: () => 'user' };
        const snapshots: string[][] = [];
        const done = loadSections(root, apiClient, { Id: 'user' }, {});

        // Responses arrive in the order observed on the phone: genres first, then resume/next up.
        await flush();
        snapshots.push(visibleHeadings(root));
        pending.genre.resolve();
        await flush();
        snapshots.push(visibleHeadings(root));
        fetches['Next Up'].resolve();
        await flush();
        snapshots.push(visibleHeadings(root));
        fetches['Continue Watching'].resolve();
        await done;
        snapshots.push(visibleHeadings(root));

        for (const shown of snapshots) {
            // Whatever is visible must be the top of the final layout, never a later section first.
            expect(shown).toEqual(FINAL_ORDER.slice(0, shown.length));
        }
        expect(snapshots.at(-1)).toEqual(FINAL_ORDER);
    });

    it('starts sections in screen order, a few at a time, so lower rows cannot crowd out the first screen', async () => {
        const root = document.createElement('div');
        document.body.appendChild(root);
        const done = loadSections(root, { serverId: () => 'server', getCurrentUserId: () => 'user' }, { Id: 'user' }, {});
        await flush();
        // Continue Watching, Next Up and the genre row hold all three slots; My Media waits.
        expect(root.querySelector('.section3')?.innerHTML).toBe('');
        fetches['Continue Watching'].resolve();
        await flush();
        await flush();
        expect(root.querySelector('.section3')?.innerHTML).toContain('My Media');
        pending.genre.resolve();
        fetches['Next Up'].resolve();
        await done;
    });

    it('shows Home once the sections filling the first screen are ready', async () => {
        // Continue Watching alone is taller than the screen once it has cards.
        const offsetHeight = vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
            return this.classList.contains('section0') && !this.classList.contains('hide') ? window.innerHeight + 100 : 0;
        });
        const root = document.createElement('div');
        document.body.appendChild(root);
        const done = loadSections(root, { serverId: () => 'server', getCurrentUserId: () => 'user' }, { Id: 'user' }, {});

        await flush();
        fetches['Continue Watching'].resolve();
        await flush();
        await flush();
        // Next Up and the genre row are still loading, but everything after Continue Watching is
        // below the first screen, so Home is already shown with Continue Watching at the top.
        expect(root.classList.contains('homeSectionsLoading')).toBe(false);
        expect(visibleHeadings(root)[0]).toBe('Continue Watching');

        pending.genre.resolve();
        fetches['Next Up'].resolve();
        await done;
        expect(visibleHeadings(root)).toEqual(FINAL_ORDER);
        offsetHeight.mockRestore();
    });
});
