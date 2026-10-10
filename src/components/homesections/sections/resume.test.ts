// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

const server = vi.hoisted(() => ({ respond: undefined as undefined | ((value: unknown) => void), calls: 0 }));

vi.mock('apps/legacy/features/libraries/api/useResumeItems', () => ({
    getResumeItemsQuery: () => ({
        queryKey: [ 'User', 'user', 'ResumeItems' ],
        queryFn: () => {
            server.calls++;
            return new Promise(resolve => {
                server.respond = resolve;
            });
        }
    })
}));
vi.mock('lib/jellyfin-apiclient/ServerConnections', () => ({ default: { getApi: () => ({}) } }));
vi.mock('lib/globalize', () => ({ default: { translate: (key: string) => key } }));
vi.mock('components/cardbuilder/cardBuilder', () => ({ default: { getCardsHtml: vi.fn() } }));
vi.mock('components/cardbuilder/utils/shape', () => ({ getBackdropShape: vi.fn(), getPortraitShape: vi.fn() }));

import { queryClient } from 'utils/query/queryClient';
import { loadResume } from './resume';
import type { SectionContainerElement } from './section';

const apiClient = { serverId: () => 'server', getCurrentUserId: () => 'user' };
const userSettings = { useEpisodeImagesInNextUpAndResume: () => false };
const KEY = [ 'User', 'user', 'ResumeItems' ];

function mountRow() {
    const elem = document.createElement('div');
    document.body.appendChild(elem);
    // @ts-expect-error minimal fakes for the API client and settings
    loadResume(elem, apiClient, 'HeaderContinueWatching', 'Video', userSettings, { enableOverflow: true });
    return elem.querySelector('.itemsContainer') as SectionContainerElement;
}

afterEach(() => {
    queryClient.clear();
    document.body.innerHTML = '';
    server.calls = 0;
});

describe('Continue Watching on Home', () => {
    it('shows the saved row at once and refreshes it in place when the server answers differently', async () => {
        const saved = { Items: [{ Id: 'bad-sisters' }] };
        queryClient.setQueryData(KEY, saved, { updatedAt: Date.now() - 60 * 60 * 1000 });
        const row = mountRow();
        row.refreshItems = vi.fn(() => Promise.resolve());

        await expect(row.fetchData()).resolves.toBe(saved);
        expect(server.calls).toBe(1);

        server.respond?.({ Items: [{ Id: 'south-park' }] });
        await vi.waitFor(() => expect(row.refreshItems).toHaveBeenCalledTimes(1));
    });

    it('does not redraw the row when the fresh answer matches the saved one', async () => {
        const saved = { Items: [{ Id: 'bad-sisters' }] };
        queryClient.setQueryData(KEY, saved, { updatedAt: Date.now() - 60 * 60 * 1000 });
        const row = mountRow();
        row.refreshItems = vi.fn(() => Promise.resolve());
        await row.fetchData();
        server.respond?.({ Items: [{ Id: 'bad-sisters' }] });
        await new Promise(resolve => setTimeout(resolve, 0));
        expect(row.refreshItems).not.toHaveBeenCalled();
    });

    it('waits for the server when nothing has been saved yet', async () => {
        const row = mountRow();
        const pending = row.fetchData();
        server.respond?.({ Items: [{ Id: 'scoob' }] });
        await expect(pending).resolves.toEqual({ Items: [{ Id: 'scoob' }] });
    });
});
