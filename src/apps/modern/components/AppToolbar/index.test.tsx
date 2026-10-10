import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { afterEach, beforeAll, afterAll, describe, expect, it, vi } from 'vitest';
import AppToolbar from './index';

// The mocked export must match the production module's name.
// eslint-disable-next-line @typescript-eslint/naming-convention
vi.mock('components/router/appRouter', () => ({ appRouter: { canGoBack: () => false }, PUBLIC_PATHS: ['/login'] }));
vi.mock('hooks/useApi', () => ({ useApi: () => ({ user: { Id: 'test-user' } }) }));
vi.mock('lib/globalize', () => ({ default: { translate: (key: string) => key } }));
vi.mock('components/toolbar/UserMenuButton', () => ({ default: () => <button>User</button> }));
vi.mock('components/toolbar/ServerButton', () => ({ default: () => <button>Server</button> }));
vi.mock('./RemotePlayButton', () => ({ default: () => <button>Cast</button> }));
vi.mock('./SyncPlayButton', () => ({ default: () => <button>Sync</button> }));
vi.mock('./SearchButton', () => ({ default: () => <button>Search</button> }));
vi.mock('./userViews/UserViewNav', () => ({ default: () => <nav>Libraries</nav> }));

const roots: Root[] = [];
beforeAll(() => vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true));
afterAll(() => vi.unstubAllGlobals());
afterEach(() => {
    act(() => {
        roots.splice(0).forEach(root => {
            root.unmount();
        });
    });
    document.body.textContent = '';
});

function render(path: string, drawer: boolean, mode: 'light' | 'dark' = 'dark') {
    const container = document.createElement('div');
    document.body.append(container);
    const root = createRoot(container);
    roots.push(root);
    act(() => root.render(
        <MemoryRouter initialEntries={[path]}>
            <ThemeProvider theme={createTheme({ palette: { mode } })}>
                <AppToolbar isDrawerAvailable={drawer} isDrawerOpen={false} onDrawerButtonClick={() => undefined} />
            </ThemeProvider>
        </MemoryRouter>
    ));
    return container;
}
describe('browsing toolbar branding', () => {
    it('keeps the wordmark next to the menu when the phone drawer is available', () => {
        const toolbar = render('/home', true);
        expect(toolbar.querySelector('img[alt="Tobyfin"]')).not.toBeNull();
        expect(toolbar.querySelector('button[aria-label="MenuOpen"]')).not.toBeNull();
        expect(toolbar.textContent).toContain('Search');
        expect(toolbar.textContent).toContain('User');
    });
    it('also shows the wordmark on wide home screens', () => {
        expect(render('/home', false).querySelector('img[alt="Tobyfin"]')).not.toBeNull();
    });
    it('uses contrasting wordmarks in dark and light themes', () => {
        expect(render('/home', true, 'dark').querySelector('img')?.getAttribute('src')).toContain('banner-light');
        expect(render('/home', true, 'light').querySelector('img')?.getAttribute('src')).toContain('banner-dark');
    });
    it.each(['/details?id=movie', '/movies', '/shows', '/search', '/mypreferencesmenu', '/login'])(
        'keeps branding visible on %s', (path) => {
            expect(render(path, true).querySelector('img[alt="Tobyfin"]')).not.toBeNull();
        }
    );
    it('keeps the video player free of the home toolbar', () => {
        expect(render('/video', true).querySelector('img[alt="Tobyfin"]')).toBeNull();
    });
});
