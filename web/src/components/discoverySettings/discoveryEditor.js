import dialogHelper from '../dialogHelper/dialogHelper';
import ServerConnections from 'lib/jellyfin-apiclient/ServerConnections';
import { loadGenreCatalog, searchGenres } from './genreCatalog';
import { sections, normalizeLayout, layoutSections, moveSection, addGenre, removeGenre, selectedGenreId } from './discoveryLayout';
import { currentLayoutKey, getDiscoveryLayout, saveDiscoveryLayout } from './discoverySettings';

export function openDiscoveryEditor() {
    const key = currentLayoutKey();
    let draft = getDiscoveryLayout();
    const dialog = dialogHelper.createDialog({ removeOnClose: true, lockScroll: true });
    dialog.classList.add('discoveryEditor');
    dialog.innerHTML = '<div class="discoveryBody"><h2>Customize Discovery</h2><p>For your account on this device. Empty sections hide automatically.</p>'
        + '<label>Poster size <select class="discoverySize"><option value="small">Small</option><option value="medium">Medium</option><option value="large">Large</option></select></label>'
        + '<p>Use the arrows to reorder sections. Uncheck a section to hide it.</p><div class="discoveryOrder"></div>'
        + '<h3>Add genres from your server</h3><p>Search your movie genres. The list updates as your library changes.</p>'
        + '<label>Search genres<input class="discoverySearch" type="search" placeholder="Western, music, history…" /></label>'
        + '<button type="button" data-action="refresh">Refresh genres</button><p class="discoveryCatalogStatus" role="status"></p>'
        + '<div class="discoveryCatalog"></div></div><div class="discoveryFooter"><p class="discoveryError" role="alert"></p><div class="discoveryActions"><button type="button" data-action="defaults">Restore defaults</button>'
        + '<button type="button" data-action="cancel">Cancel</button><button type="button" data-action="save">Save</button></div></div>';
    const list = dialog.querySelector('.discoveryOrder');
    const size = dialog.querySelector('.discoverySize');
    function render(focusId, direction) {
        size.value = draft.size;
        list.textContent = '';
        draft.order.forEach((id, index) => {
            const row = document.createElement('div');
            row.className = 'discoveryOrderRow';
            const title = layoutSections(draft).find(section => section.id === id).title;
            const label = document.createElement('label');
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = !draft.hidden.includes(id);
            checkbox.addEventListener('change', () => {
                draft.hidden = checkbox.checked ? draft.hidden.filter(hidden => hidden !== id) : [...draft.hidden, id];
                renderCatalog();
            });
            label.append(checkbox, document.createTextNode(title));
            row.append(label);
            [-1, 1].forEach(delta => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = delta < 0 ? '↑' : '↓';
                button.setAttribute('aria-label', 'Move ' + title + (delta < 0 ? ' up' : ' down'));
                button.disabled = delta < 0 ? index === 0 : index === draft.order.length - 1;
                button.addEventListener('click', () => {
                    draft = moveSection(draft, id, delta);
                    render(id, delta);
                });
                row.append(button);
                if (id === focusId && delta === direction) {
                    queueMicrotask(() => (button.disabled ? checkbox : button).focus());
                }
            });
            if (id.startsWith('genre:')) {
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.textContent = '×';
                remove.setAttribute('aria-label', 'Remove ' + title);
                remove.addEventListener('click', () => {
                    draft = removeGenre(draft, id);
                    render();
                    renderCatalog();
                    search.focus();
                });
                row.append(remove);
            }
            list.append(row);
        });
    }
    size.addEventListener('change', () => {
        draft.size = size.value;
    });
    dialog.querySelector('[data-action="defaults"]').addEventListener('click', () => {
        draft = normalizeLayout(null);
        renderCatalog();
        render();
    });
    dialog.querySelector('[data-action="cancel"]').addEventListener('click', () => dialogHelper.close(dialog));
    dialog.querySelector('[data-action="save"]').addEventListener('click', () => {
        try {
            saveDiscoveryLayout(draft, key);
            dialogHelper.close(dialog);
        } catch (error) {
            dialog.querySelector('.discoveryError').textContent = error.message;
        }
    });
    let closed = false;
    let loading = false;
    let catalog = [];
    const api = ServerConnections.currentApiClient();
    const search = dialog.querySelector('.discoverySearch');
    const catalogList = dialog.querySelector('.discoveryCatalog');
    const status = dialog.querySelector('.discoveryCatalogStatus');
    const isCurrent = () => !closed && key === currentLayoutKey();
    function renderCatalog() {
        catalogList.textContent = '';
        const available = [...sections.filter(section => draft.removedGenres.includes(section.id)), ...catalog];
        const matches = searchGenres([...new Map(available.map(genre => [genre.title.toLocaleLowerCase(), genre])).values()], search.value);
        for (const genre of matches) {
            const button = document.createElement('button');
            button.type = 'button';
            const existing = selectedGenreId(draft, genre);
            const shown = existing && draft.order.includes(existing) && !draft.hidden.includes(existing);
            button.textContent = genre.title + (shown ? ' · Added' : ' +');
            button.disabled = !!shown;
            button.addEventListener('click', () => {
                if (!isCurrent()) return;
                draft = addGenre(draft, genre);
                render();
                renderCatalog();
                search.focus();
            });
            catalogList.append(button);
        }
        if (!matches.length && catalog.length) catalogList.textContent = 'No matching genres.';
    }
    async function refreshCatalog() {
        if (loading || !isCurrent() || !api?.getGenres) return;
        loading = true;
        status.textContent = 'Checking server genres…';
        try {
            const next = await loadGenreCatalog(api, isCurrent);
            if (!isCurrent()) return;
            if (JSON.stringify(next) !== JSON.stringify(catalog)) {
                catalog = next;
                renderCatalog();
            }
            status.textContent = catalog.length ? 'Choose a genre to add a section.' : 'No movie genres found on this server.';
        } catch {
            if (isCurrent()) status.textContent = 'Could not refresh genres. Your selections are safe. Try Refresh genres.';
        } finally {
            loading = false;
        }
    }
    search.addEventListener('input', renderCatalog);
    // The page shrinks when the on-screen keyboard opens; keep the field being typed in visible.
    const keepFocusVisible = () => {
        const active = document.activeElement;
        if (active?.matches?.('input, select') && dialog.contains(active)) {
            active.scrollIntoView({ block: 'center' });
        }
    };
    window.addEventListener('resize', keepFocusVisible);
    dialog.querySelector('[data-action="refresh"]').addEventListener('click', refreshCatalog);
    const interval = setInterval(refreshCatalog, 30000);
    let refreshTimer;
    const unsubscribe = api?.subscribe?.(['LibraryChanged'], () => {
        clearTimeout(refreshTimer);
        refreshTimer = setTimeout(refreshCatalog, 1000);
    });
    dialog.addEventListener('close', () => {
        closed = true;
        window.removeEventListener('resize', keepFocusVisible);
        clearInterval(interval);
        clearTimeout(refreshTimer);
        unsubscribe?.();
        dialog.remove();
    });
    refreshCatalog();

    render();
    renderCatalog();
    dialogHelper.open(dialog);
}
