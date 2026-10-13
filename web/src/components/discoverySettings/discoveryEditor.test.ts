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
import { onHome } from './discoveryLayout';

const western = { Id: '0123456789abcdef0123456789abcdef', Name: 'Western' };
const click = (selector: string) => (document.querySelector(selector) as HTMLElement).click();
const titles = () => [...document.querySelectorAll('.dRow .dTitle')].map(element => element.textContent);
const tab = (name: 'on' | 'add') => click(`[data-tab="${name}"]`);
const flush = () => new Promise(resolve => setTimeout(resolve, 0));

describe('Customize Discovery editor', () => {
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

    it('opens on the On Home tab with the row count, labelling only built-in rows', () => {
        openDiscoveryEditor();
        const count = onHome(getDiscoveryLayout()).length;
        expect(document.querySelector('[data-tab="on"]')?.textContent).toBe(`On Home · ${count}`);
        expect(titles()).toHaveLength(count);
        const continueWatching = document.querySelector('.dRow[data-id="resume"]');
        const action = document.querySelector('.dRow[data-id="genre:Action"]');
        expect(continueWatching?.querySelector('.dTag')?.textContent).toBe('Built-in');
        expect(action?.querySelector('.dTag')).toBeNull();
    });

    it('taking a row off saves straight away, and Undo puts it back', () => {
        openDiscoveryEditor();
        click('.dRow[data-id="nextup"] .dSwitch');
        expect(onHome(getDiscoveryLayout())).not.toContain('nextup');
        expect(document.querySelector('.dSnack')?.textContent).toContain('Up Next taken off Home');
        click('.dSnack [data-action="undo"]');
        expect(onHome(getDiscoveryLayout())).toContain('nextup');
        expect(titles()).toContain('Up Next');
    });

    it('Add to Home lists rows taken off first, then server genres, and + adds to the end of On Home', async () => {
        catalog.getGenres.mockResolvedValue({ Items: [western], TotalRecordCount: 1 });
        openDiscoveryEditor();
        await flush();
        click('.dRow[data-id="genre:Horror"] .dSwitch');
        tab('add');
        expect(titles()).toEqual(['Horror', 'Recordings', 'Western']);
        click('.dRow.off:last-child [data-action="add"]');
        const shown = onHome(getDiscoveryLayout());
        expect(shown[shown.length - 1]).toBe('genre:server:' + western.Id);
        expect(document.querySelector('[data-tab="on"]')?.textContent).toBe(`On Home · ${shown.length}`);
    });

    it('searches Add to Home and keeps typing in the search field', () => {
        openDiscoveryEditor();
        click('.dRow[data-id="genre:Comedy"] .dSwitch');
        click('.dRow[data-id="genre:Drama"] .dSwitch');
        tab('add');
        const search = document.querySelector('.dSearchInput') as HTMLInputElement;
        search.value = 'dra';
        search.dispatchEvent(new Event('input', { bubbles: true }));
        expect(titles()).toEqual(['Drama']);
        expect(document.activeElement).toBe(document.querySelector('.dSearchInput'));
    });

    it('a long press offers Move to top', () => {
        vi.useFakeTimers();
        openDiscoveryEditor();
        const row = document.querySelector('.dRow[data-id="genre:Horror"] .dBody') as HTMLElement;
        row.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true, clientY: 100 }));
        vi.advanceTimersByTime(600);
        row.dispatchEvent(new MouseEvent('pointerup', { bubbles: true }));
        vi.advanceTimersByTime(500);
        click('.dSheet [data-action="top"]');
        expect(onHome(getDiscoveryLayout())[0]).toBe('genre:Horror');
    });

    it('changes poster size straight away', () => {
        openDiscoveryEditor();
        click('[data-size="large"]');
        expect(getDiscoveryLayout().size).toBe('large');
        expect(document.querySelector('[data-size="large"]')?.classList.contains('selected')).toBe(true);
    });

    it('Reset to default asks first', () => {
        saveDiscoveryLayout({ size: 'small', hidden: ['resume'] });
        openDiscoveryEditor();
        click('[data-action="reset"]');
        click('.dConfirm [data-action="dismiss"]');
        expect(getDiscoveryLayout().size).toBe('small');
        click('[data-action="reset"]');
        click('[data-action="confirm-reset"]');
        expect(getDiscoveryLayout().size).toBe('medium');
        expect(getDiscoveryLayout().hidden).toEqual(['activerecordings']);
    });

    it('does not save into another account if the signed-in account changed', () => {
        openDiscoveryEditor();
        account.user = 'two';
        click('.dRow[data-id="resume"] .dSwitch');
        expect(document.querySelector('.dSnack')?.textContent).toContain('account changed');
        expect(getDiscoveryLayout().hidden).toEqual(['activerecordings']);
        account.user = 'one';
        expect(getDiscoveryLayout().hidden).toEqual(['activerecordings']);
    });

    it('marks built-in genres the server has nothing for once its genres load', async () => {
        catalog.getGenres.mockResolvedValue({ Items: [{ Id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', Name: 'Action' }], TotalRecordCount: 1 });
        openDiscoveryEditor();
        expect(document.querySelectorAll('.dEmpty')).toHaveLength(0);
        await flush();
        expect(document.querySelector('.dRow[data-id="genre:Action"] .dEmpty')).toBeNull();
        expect(document.querySelector('.dRow[data-id="genre:Horror"] .dEmpty')?.textContent).toBe('Nothing to show yet');
    });

    it('stops refreshing genres once closed', () => {
        openDiscoveryEditor();
        click('[data-action="close"]');
        expect(catalog.changed).toBeUndefined();
        expect(document.querySelector('.discoveryEditor')).toBeNull();
    });
});
