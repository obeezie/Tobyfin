import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SubtitleSync from './subtitlesync';

const manager = vi.hoisted(() => ({
    supportSubtitleOffset: vi.fn(() => true),
    canHandleOffsetOnCurrentSubtitle: vi.fn(() => true),
    isShowingSubtitleOffsetEnabled: vi.fn(() => true),
    getPlayerSubtitleOffset: vi.fn(() => 0),
    setSubtitleOffset: vi.fn(),
    disableShowingSubtitleOffset: vi.fn()
}));
vi.mock('../playback/playbackmanager', () => ({ playbackManager: manager }));
vi.mock('./subtitlesync.template.html', async () => {
    const { readFileSync } = await import('node:fs');
    return { default: readFileSync('src/components/subtitlesync/subtitlesync.template.html', 'utf8') };
});

describe('compact live subtitle controls', () => {
    let overlay: SubtitleSync;
    const click = (selector: string) => (overlay.element.querySelector(selector) as HTMLElement).click();
    beforeEach(() => {
        vi.clearAllMocks();
        manager.canHandleOffsetOnCurrentSubtitle.mockReturnValue(true);
        overlay = new SubtitleSync({});
        overlay.toggle();
    });
    afterEach(() => overlay.destroy());

    it('keeps every rapid tap even before the player reports its debounced value', () => {
        for (let i = 0; i < 5; i++) click('[data-subtitle-step="0.1"]');
        expect(manager.setSubtitleOffset).toHaveBeenLastCalledWith(-0.5, overlay.player);
        expect(overlay.value!.textContent).toBe('+0.5 s');
        click('[data-subtitle-step="-0.1"]');
        expect(manager.setSubtitleOffset).toHaveBeenLastCalledWith(-0.4, overlay.player);
    });
    it('stays small by default and remains open when transport controls auto-hide', () => {
        expect((overlay.advanced as HTMLElement).hidden).toBe(true);
        overlay.toggle('hide');
        expect(overlay.isOpen).toBe(true);
        expect(overlay.container!.classList.contains('hide')).toBe(false);
        click('.subtitleSync-more');
        expect((overlay.advanced as HTMLElement).hidden).toBe(false);
        click('.subtitleSync-closeButton');
        expect(overlay.isOpen).toBe(false);
        expect((overlay.advanced as HTMLElement).hidden).toBe(true);
        expect(manager.disableShowingSubtitleOffset).toHaveBeenCalledOnce();
    });
    it('supports precise signed entry, rejects invalid values and resets', () => {
        (overlay.input as HTMLInputElement).value = '-0.025';
        click('.subtitleSync-apply');
        expect(manager.setSubtitleOffset).toHaveBeenLastCalledWith(0.025, overlay.player);
        (overlay.input as HTMLInputElement).value = 'nonsense';
        click('.subtitleSync-apply');
        expect(manager.setSubtitleOffset).toHaveBeenCalledTimes(1);
        expect(overlay.input!.getAttribute('aria-invalid')).toBe('true');
        click('.subtitleSync-reset');
        expect(overlay.delay).toBe(0);
    });
    it('blocks unsupported caption timing and permits forced teardown', () => {
        manager.canHandleOffsetOnCurrentSubtitle.mockReturnValue(false);
        overlay.incrementOffset();
        expect(manager.setSubtitleOffset).not.toHaveBeenCalled();
        expect(overlay.isOpen).toBe(false);
        overlay.toggle('forceToHide');
        expect(overlay.container!.classList.contains('hide')).toBe(true);
    });
    it('does not bubble timing keyboard navigation into video seeking', () => {
        const spy = vi.fn();
        document.addEventListener('keydown', spy);
        overlay.more!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        expect(spy).not.toHaveBeenCalled();
        document.removeEventListener('keydown', spy);
    });
});
