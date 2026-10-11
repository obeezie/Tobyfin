import { afterEach, describe, expect, it, vi } from 'vitest';

const { resizeCallbacks } = vi.hoisted(() => ({ resizeCallbacks: [] as ((entries: { contentRect: { width: number; height: number } }[]) => void)[] }));
vi.mock('../../scripts/browser', () => ({ default: { firefox: false, animate: false } }));
vi.mock('../../components/layoutManager', () => ({ default: { desktop: false, tv: false } }));
vi.mock('../../components/focusManager', () => ({ default: { focusableParent: () => null } }));
vi.mock('../globalize', () => ({ default: { getIsRTL: vi.fn(() => false) } }));
vi.mock('../../utils/dom', () => ({ default: {
    addEventListener: (element: HTMLElement, event: string, callback: EventListener) => element.addEventListener(event, callback),
    removeEventListener: (element: HTMLElement, event: string, callback: EventListener) => element.removeEventListener(event, callback)
} }));
vi.mock('resize-observer-polyfill', () => ({ default: class {
    constructor(callback: typeof resizeCallbacks[number]) { resizeCallbacks.push(callback); }
    observe() { /* Geometry changes are triggered by the test. */ }
    disconnect() { /* No timers are needed. */ }
} }));

import ScrollerFactory from './index';
import globalize from '../globalize';

afterEach(() => {
    document.body.innerHTML = '';
    resizeCallbacks.length = 0;
});

function makeScroller(native = true) {
    const frame = document.createElement('div');
    const slider = document.createElement('div');
    frame.appendChild(slider);
    document.body.appendChild(frame);
    let width = 2400;
    Object.defineProperties(slider, {
        clientWidth: { value: 300 },
        offsetWidth: { value: 300 },
        scrollWidth: { get: () => width }
    });
    const scroll = vi.fn((options?: ScrollToOptions | number, y?: number) => {
        frame.scrollLeft = typeof options === 'number' ? options : options?.left || 0;
        frame.scrollTop = y || 0;
    });
    frame.scroll = scroll;
    const scroller = new ScrollerFactory(frame, { horizontal: true, slidee: slider, allowNativeScroll: native });
    scroller.init();
    return { frame, slider, scroller, scroll, setWidth: (next: number) => {
        width = next;
    } };
}

describe('scroller content growth', () => {
    it('keeps a native swipe offset when cards append and the frame reports a resize', () => {
        const { frame, scroller, scroll, setWidth } = makeScroller();
        frame.scrollLeft = 1800;
        setWidth(6400);
        scroller.reload();
        expect(frame.scrollLeft).toBe(1800);
        resizeCallbacks[0]([{ contentRect: { width: 300, height: 240 } }]);
        expect(frame.scrollLeft).toBe(1800);
        expect(scroll).not.toHaveBeenCalled();
        // New cards are reachable after extending the bounds.
        scroller.slideTo(5000, true, undefined);
        expect(frame.scrollLeft).toBe(5000);
        scroller.destroy();
    });

    it('preserves native RTL offsets and clamps only when content shrinks', () => {
        vi.mocked(globalize.getIsRTL).mockReturnValue(true);
        const { frame, scroller, setWidth } = makeScroller();
        frame.scrollLeft = -1800;
        setWidth(6400);
        scroller.reload();
        expect(frame.scrollLeft).toBe(-1800);
        setWidth(500);
        scroller.reload();
        expect(frame.scrollLeft).toBe(-200);
        scroller.destroy();
    });

    it('preserves desktop animation positions while extending their bounds', () => {
        const { slider, scroller, setWidth } = makeScroller(false);
        scroller.slideTo(1800, true, undefined);
        setWidth(6400);
        scroller.reload();
        expect(scroller.getScrollPosition()).toBe(1800);
        expect(slider.style.transform).toBe('translateX(-1800px)');
        scroller.slideTo(5000, true, undefined);
        expect(scroller.getScrollPosition()).toBe(5000);
        scroller.destroy();
    });
});
