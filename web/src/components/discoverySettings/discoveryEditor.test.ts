import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const account = vi.hoisted(() => ({ user: 'one' }));
const catalog = vi.hoisted(() => ({
    getGenres: vi.fn(),
    changed: undefined as undefined | (() => void)
}));
vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({
    default: { currentApiClient: () => ({ serverId: () => 'server', getCurrentUserId: () => account.user, getGenres: catalog.getGenres, subscribe: (_: unknown, callback: () => void) => {
        catalog.changed = callback;
        return () => {
            catalog.changed = undefined;
        };
    } }) }
}));
vi.mock('../dialogHelper/dialogHelper', () => ({
    default: {
        createDialog: () => document.createElement('div'),
        open: (element: HTMLElement) => document.body.append(element),
        close: (element: HTMLElement) => element.dispatchEvent(new Event('close'))
    }
}));
import { openDiscoveryEditor } from './discoveryEditor';
import { getDiscoveryLayout, saveDiscoveryLayout } from './discoverySettings';

function click(selector: string) {
    (document.querySelector(selector) as HTMLElement).click();
}
describe('Customize Discovery editor', () => {
    it('can remove, save and re-add a default even when server catalog is empty', () => {
        openDiscoveryEditor();
        click('button[aria-label="Remove Action"]');
        expect(document.querySelector('button[aria-label="Remove Action"]')).toBeNull();
        click('[data-action="save"]');
        expect(getDiscoveryLayout().order).not.toContain('genre:Action');
        openDiscoveryEditor();
        click('.discoveryCatalog button');
        click('[data-action="save"]');
        expect(getDiscoveryLayout().order).toContain('genre:Action');
    });
    beforeEach(() => {
        document.body.textContent = '';
        localStorage.clear();
        account.user = 'one';
        catalog.getGenres.mockReset().mockResolvedValue({ Items: [], TotalRecordCount: 0 });
    });
    afterEach(() => {
        document.querySelectorAll('.discoveryEditor').forEach(dialog => {
            dialog.dispatchEvent(new Event('close'));
        });
        vi.useRealTimers();
    });
    it('keeps the genre search visible when the keyboard shrinks the page, until the editor closes', () => {
        openDiscoveryEditor();
        const search = document.querySelector('.discoverySearch') as HTMLInputElement;
        search.scrollIntoView = vi.fn();
        search.focus();
        window.dispatchEvent(new Event('resize'));
        expect(search.scrollIntoView).toHaveBeenCalledWith({ block: 'center' });
        click('[data-action="cancel"]');
        vi.mocked(search.scrollIntoView).mockClear();
        window.dispatchEvent(new Event('resize'));
        expect(search.scrollIntoView).not.toHaveBeenCalled();
    });
    it('Save commits order visibility and size together; edits alone do not persist', () => {
        openDiscoveryEditor();
        click('.discoveryOrderRow:first-child button:last-child');
        click('.discoveryOrderRow:first-child input');
        const size = document.querySelector('.discoverySize') as HTMLSelectElement;
        size.value = 'large';
        size.dispatchEvent(new Event('change'));
        expect(getDiscoveryLayout().size).toBe('medium');
        click('[data-action="save"]');
        const saved = getDiscoveryLayout();
        expect(saved.order.slice(0, 2)).toEqual(['nextup', 'resume']);
        expect(saved.hidden).toEqual(['nextup']);
        expect(saved.size).toBe('large');
        expect(document.querySelector('.discoveryEditor')).toBeNull();
    });
    it('Cancel discards Restore defaults and reopening reads the saved layout', () => {
        saveDiscoveryLayout({ size: 'small', hidden: ['resume'] });
        openDiscoveryEditor();
        click('[data-action="defaults"]');
        click('[data-action="cancel"]');
        expect(getDiscoveryLayout().hidden).toEqual(['resume']);
        openDiscoveryEditor();
        expect((document.querySelector('.discoverySize') as HTMLSelectElement).value).toBe('small');
        click('[data-action="defaults"]');
        click('[data-action="save"]');
        expect(getDiscoveryLayout().hidden).toEqual([]);
        expect(getDiscoveryLayout().size).toBe('medium');
    });
    it('an account switch while editing cannot overwrite the new account', () => {
        openDiscoveryEditor();
        click('.discoveryOrderRow:first-child input');
        account.user = 'two';
        click('[data-action="save"]');
        expect(document.querySelector('.discoveryError')?.textContent).toContain('account changed');
        expect(getDiscoveryLayout().hidden).toEqual([]);
    });
    it('searches new server genres, adds them to the draft and saves their IDs', async () => {
        vi.useFakeTimers();
        openDiscoveryEditor();
        await vi.advanceTimersByTimeAsync(0);
        catalog.getGenres.mockResolvedValue({ Items: [
            { Id: '0123456789abcdef0123456789abcdef', Name: 'Western' }
        ], TotalRecordCount: 1 });
        catalog.changed?.();
        await vi.advanceTimersByTimeAsync(1000);
        const search = document.querySelector('.discoverySearch') as HTMLInputElement;
        search.value = 'west';
        search.dispatchEvent(new Event('input'));
        expect(document.querySelector('.discoveryCatalog')?.textContent).toContain('Western');
        click('.discoveryCatalog button');
        expect(getDiscoveryLayout().customGenres).toEqual([]);
        expect(document.querySelector('.discoveryOrder')?.textContent).toContain('Western');
        expect(document.querySelector('.discoveryBody [data-action="save"]')).toBeNull();
        click('[data-action="defaults"]');
        expect((document.querySelector('.discoveryCatalog button') as HTMLButtonElement).disabled).toBe(false);
        click('.discoveryCatalog button');
        click('[data-action="save"]');
        expect(getDiscoveryLayout().customGenres[0].genreId).toBe('0123456789abcdef0123456789abcdef');
        expect(catalog.changed).toBeUndefined();
    });
});
