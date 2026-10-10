// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let layoutKey = 'server-user';
const loadSections = vi.fn();
const resume = vi.fn<(...args: unknown[]) => Promise<void>>(() => Promise.resolve());

vi.mock('components/discoverySettings/discoverySettings', () => ({
    getDiscoveryLayout: () => ({}),
    currentLayoutKey: () => layoutKey
}));
vi.mock('scripts/settings/userSettings', () => ({}));
vi.mock('components/focusManager', () => ({ default: { autoFocus: vi.fn() } }));
vi.mock('components/homesections/homesections', () => ({
    default: {
        loadSections: (...args: unknown[]) => loadSections(...args),
        resume: (...args: unknown[]) => resume(...args),
        pause: vi.fn(),
        destroySections: (elem: HTMLElement) => { elem.innerHTML = ''; }
    }
}));
vi.mock('lib/jellyfin-apiclient', () => ({
    ServerConnections: { currentApiClient: () => ({ getCurrentUser: async () => ({ Id: 'user' }) }) }
}));
vi.mock('elements/emby-itemscontainer/emby-itemscontainer', () => ({}));

import HomeTab, { clearParkedSections } from './hometab';

type Row = HTMLElement & { fetchData?: unknown; needsRefresh?: boolean };

function mountHome() {
    const view = document.createElement('div');
    view.innerHTML = '<div class="sections"></div>';
    document.body.appendChild(view);
    return view;
}

beforeEach(() => {
    layoutKey = 'server-user';
    loadSections.mockImplementation(async (elem: HTMLElement) => {
        elem.classList.add('homeSectionsContainer');
        Object.assign(elem, { homeSectionsComplete: true });
        elem.innerHTML = '<div class="section0"><div class="itemsContainer" data-monitor="videoplayback"></div></div>'
            + '<div class="section1"><div class="emby-scroller"><div class="itemsContainer genre"></div></div></div>';
        for (const row of elem.querySelectorAll<Row>('.itemsContainer')) {
            row.fetchData = () => 'cards';
        }
    });
    window.scrollTo = vi.fn();
});

afterEach(() => {
    clearParkedSections();
    document.body.innerHTML = '';
    vi.clearAllMocks();
});

async function visitThenLeave(beforeLeaving?: (view: HTMLElement) => void) {
    const firstView = mountHome();
    const first = new HomeTab(firstView, null);
    await first.onResume({ refresh: true });
    beforeLeaving?.(firstView);
    const genreRow = firstView.querySelector('.genre') as Row;
    // React removes Home's DOM before running its unmount cleanup, and a detached element
    // reports no scroll offset.
    firstView.remove();
    for (const scroller of firstView.querySelectorAll('.emby-scroller')) {
        scroller.scrollLeft = 0;
    }
    first.onPause();
    first.park();
    return genreRow;
}

describe('home tab returning from a details page', () => {
    it('restores the rendered rows instead of reloading every section', async () => {
        const genreRow = await visitThenLeave();
        // Detaching an emby-itemscontainer clears its fetch function.
        genreRow.fetchData = null;

        const secondView = mountHome();
        await new HomeTab(secondView, null).onResume({ refresh: true });

        expect(loadSections).toHaveBeenCalledTimes(1);
        expect(secondView.querySelector('.genre')).toBe(genreRow);
        expect(typeof genreRow.fetchData).toBe('function');
        expect(resume).toHaveBeenLastCalledWith(secondView.querySelector('.sections'), { refresh: false });
        // Continue Watching/Next Up refresh, because they missed playback events while parked.
        expect((secondView.querySelector('[data-monitor]') as Row).needsRefresh).toBe(true);
        expect(genreRow.needsRefresh).toBeUndefined();
    });

    it('returns each row to where it was scrolled sideways', async () => {
        await visitThenLeave(view => {
            const scroller = view.querySelector('.emby-scroller') as HTMLElement;
            scroller.scrollLeft = 640;
            scroller.dispatchEvent(new Event('scroll'));
        });
        const frames: FrameRequestCallback[] = [];
        window.requestAnimationFrame = callback => frames.push(callback);

        const secondView = mountHome();
        await new HomeTab(secondView, null).onResume({ refresh: true });
        for (const frame of frames) frame(0);

        expect((secondView.querySelector('.emby-scroller') as HTMLElement).scrollLeft).toBe(640);
    });

    it('keeps restoring Home across repeated trips to details pages', async () => {
        const genreRow = await visitThenLeave();

        // Back to Home, then open another movie and come back again.
        const secondView = mountHome();
        const second = new HomeTab(secondView, null);
        await second.onResume({ refresh: true });
        secondView.remove();
        second.onPause();
        second.park();

        const thirdView = mountHome();
        await new HomeTab(thirdView, null).onResume({ refresh: true });

        expect(loadSections).toHaveBeenCalledTimes(1);
        expect(thirdView.querySelector('.genre')).toBe(genreRow);
    });

    it('reloads when a different account returns to home', async () => {
        await visitThenLeave();
        layoutKey = 'server-other';

        const secondView = mountHome();
        await new HomeTab(secondView, null).onResume({ refresh: true });

        expect(loadSections).toHaveBeenCalledTimes(2);
    });
});
