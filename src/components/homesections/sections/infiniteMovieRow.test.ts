// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('lib/globalize', () => ({ default: { translate: (key: string) => key } }));
vi.mock('components/images/imageLoader', () => ({ default: { lazyChildren: vi.fn() } }));

import { attachInfiniteMovieRow } from './infiniteMovieRow';

afterEach(() => {
    document.body.innerHTML = '';
    vi.unstubAllGlobals();
});

describe('infinite movie row', () => {
    it('loads on reaching the row end, appends cards, and removes the control at exhaustion', async () => {
        let intersect: ((entries: { isIntersecting: boolean }[]) => void) | undefined;
        const disconnect = vi.fn();
        vi.stubGlobal('IntersectionObserver', class {
            constructor(callback: typeof intersect) { intersect = callback; }
            observe = vi.fn();
            disconnect = disconnect;
        });
        const elem = document.createElement('div');
        elem.innerHTML = '<span>Initial movie</span>';
        document.body.appendChild(elem);
        const more = vi.fn(async () => ({ Items: [{ Id: 'new' }], HasMore: false }));
        attachInfiniteMovieRow(elem, more, () => '<span>New movie</span>');
        intersect?.([{ isIntersecting: true }]);
        await vi.waitFor(() => expect(elem.textContent).toContain('New movie'));
        expect(elem.textContent).toContain('Initial movie');
        expect(elem.querySelector('button')).toBeNull();
        expect(disconnect).toHaveBeenCalled();
    });

    it('offers a retry after a failure and cleans up its button on navigation', async () => {
        vi.stubGlobal('IntersectionObserver', undefined);
        const elem = document.createElement('div');
        document.body.appendChild(elem);
        const more = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ Items: [], HasMore: false });
        const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
        attachInfiniteMovieRow(elem, more, () => '');
        elem.querySelector('button')?.click();
        await vi.waitFor(() => expect(elem.textContent).toContain('MovieDiscoveryRetry'));
        elem.querySelector('button')?.click();
        await vi.waitFor(() => expect(elem.querySelector('button')).toBeNull());
        warning.mockRestore();
    });
});
