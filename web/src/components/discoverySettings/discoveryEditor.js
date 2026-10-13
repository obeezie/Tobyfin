import dialogHelper from '../dialogHelper/dialogHelper';
import ServerConnections from 'lib/jellyfin-apiclient/ServerConnections';
import { loadGenreCatalog } from './genreCatalog';
import { addToHomeRows, isBuiltIn, isEmptyRow, layoutSections, moveOnHome, normalizeLayout, onHome, putOn, takeOff } from './discoveryLayout';
import { currentLayoutKey, getDiscoveryLayout, saveDiscoveryLayout } from './discoverySettings';

const SIZES = [
    { value: 'small', label: 'Small', count: 3 },
    { value: 'medium', label: 'Medium', count: 2 },
    { value: 'large', label: 'Large', count: 1 }
];
const LONG_PRESS_MS = 500;
const ENTITIES = new Map([['&', '&amp;'], ['<', '&lt;'], ['>', '&gt;'], ['"', '&quot;'], ['\'', '&#39;']]);
const escapeHtml = text => String(text).replace(/[&<>"']/g, character => ENTITIES.get(character));

/**
 * Customize Discovery: the rows On Home (drag to reorder, switch to take off) and the rows to Add to Home.
 * Changes save as they are made, with Undo. Design approved in prototype/customize-discovery-prototype.html.
 */
export function openDiscoveryEditor() {
    const key = currentLayoutKey();
    let layout = getDiscoveryLayout();
    let tab = 'on';
    let query = '';
    let catalog = [];
    let addRows = [];
    const history = [];
    const dialog = dialogHelper.createDialog({ removeOnClose: true, size: 'fullscreen', scrollY: false });
    dialog.classList.add('discoveryEditor');
    dialog.innerHTML = '<div class="dHeader"><button type="button" class="dBack" data-action="close" aria-label="Back">←</button><h2>Customize Discovery</h2></div>'
        + '<div class="dScroll"><div class="dContent"></div></div><div class="dOverlay"></div>';
    const content = dialog.querySelector('.dContent');
    const scroller = dialog.querySelector('.dScroll');
    const overlay = dialog.querySelector('.dOverlay');
    let snackTimer;
    let dragging = null;
    let press = null;
    let suppressClick = false;

    const titleOf = id => layoutSections(layout).find(section => section.id === id)?.title ?? id;

    function rowMeta(id) {
        let tags = '';
        if (isBuiltIn(id)) tags += '<span class="dTag">Built-in</span>';
        if (isEmptyRow(id, catalog)) tags += '<span class="dEmpty">Nothing to show yet</span>';
        return tags ? `<div class="dMeta">${tags}</div>` : '';
    }

    function onHomeList(shown) {
        if (!shown.length) return '<p class="dNote">Nothing is On Home. Add rows from Add to Home.</p>';
        return shown.map(id => {
            const title = escapeHtml(titleOf(id));
            return `<div class="dRow" data-id="${escapeHtml(id)}"><span class="dHandle" aria-hidden="true">⋮⋮</span>`
                + `<div class="dBody"><div class="dTitle">${title}</div>${rowMeta(id)}</div>`
                + `<button type="button" class="dSwitch" role="switch" aria-checked="true" aria-label="Take ${title} off Home" data-action="off"></button></div>`;
        }).join('');
    }

    function addToHomeList() {
        addRows = addToHomeRows(layout, catalog, query);
        const search = `<label class="dSearch"><span aria-hidden="true">⌕</span><input type="search" class="dSearchInput" placeholder="Search genres…" aria-label="Search genres" value="${escapeHtml(query)}"></label>`;
        if (!addRows.length) {
            const note = query ? 'No matching rows.' : 'Every row is On Home.';
            return `${search}<p class="dNote">${note}</p>`;
        }
        return search + addRows.map((row, index) => {
            const title = escapeHtml(row.title);
            return `<div class="dRow off" data-index="${index}"><div class="dBody"><div class="dTitle">${title}</div>${rowMeta(row.id)}</div>`
                + `<button type="button" class="dAdd" aria-label="Add ${title} to Home" data-action="add">+</button></div>`;
        }).join('');
    }

    function render() {
        const shown = onHome(layout);
        const sizes = SIZES.map(size => {
            const selected = layout.size === size.value;
            return `<button type="button" class="dSize${selected ? ' selected' : ''}" data-size="${size.value}" aria-pressed="${selected}">`
                + `<span class="dShape dShape-${size.value}">${'<i></i>'.repeat(size.count)}</span>${size.label}</button>`;
        }).join('');
        const list = tab === 'on' ? onHomeList(shown) : addToHomeList();
        content.innerHTML = `<section class="dCard"><h3>Poster size</h3><div class="dSizes">${sizes}</div></section>`
            + `<div class="dTabs" role="tablist"><button type="button" role="tab" data-tab="on" aria-selected="${tab === 'on'}">On Home · ${shown.length}</button>`
            + `<button type="button" role="tab" data-tab="add" aria-selected="${tab === 'add'}">Add to Home</button></div>`
            + `<div class="dList">${list}</div>`
            + '<button type="button" class="dReset" data-action="reset">Reset to default</button><p class="dFoot">For your account on this device. Empty rows are skipped on Home.</p>';
    }

    function hideSnack() {
        clearTimeout(snackTimer);
        overlay.querySelector('.dSnack')?.remove();
    }

    function showSnack(message, canUndo = false) {
        hideSnack();
        const undoButton = canUndo ? '<button type="button" data-action="undo">Undo</button>' : '';
        overlay.insertAdjacentHTML('beforeend', `<div class="dSnack" role="status"><span>${escapeHtml(message)}</span>${undoButton}</div>`);
        snackTimer = setTimeout(hideSnack, 4000);
    }

    /** Apply a change, save it straight away, and offer Undo. */
    function change(next, message) {
        try {
            saveDiscoveryLayout(next, key);
        } catch (error) {
            showSnack(error.message);
            return;
        }
        history.push(layout);
        layout = next;
        render();
        showSnack(message, true);
    }

    function undo() {
        const previous = history.pop();
        if (!previous) return;
        try {
            saveDiscoveryLayout(previous, key);
        } catch (error) {
            showSnack(error.message);
            return;
        }
        layout = previous;
        render();
        hideSnack();
    }

    const dismiss = () => overlay.querySelector('.dSheetWrap')?.remove();

    function showSheet(id) {
        dismiss();
        const safeId = escapeHtml(id);
        overlay.insertAdjacentHTML('beforeend', '<div class="dSheetWrap"><div class="dDim" data-action="dismiss"></div><div class="dSheet" role="menu">'
            + `<h4>${escapeHtml(titleOf(id))}</h4><button type="button" role="menuitem" data-action="top" data-id="${safeId}">Move to top</button>`
            + `<button type="button" role="menuitem" data-action="sheet-off" data-id="${safeId}">Take off Home</button>`
            + '<button type="button" class="dCancel" data-action="dismiss">Cancel</button></div></div>');
        overlay.querySelector('.dSheet button').focus();
    }

    function confirmReset() {
        dismiss();
        overlay.insertAdjacentHTML('beforeend', '<div class="dSheetWrap"><div class="dDim" data-action="dismiss"></div><div class="dConfirm" role="alertdialog" aria-label="Reset Discovery">'
            + '<h4>Reset Discovery?</h4><p>Home goes back to the default rows and poster size. Your added genres move to Add to Home.</p>'
            + '<div class="dConfirmButtons"><button type="button" data-action="dismiss">Cancel</button><button type="button" class="dDanger" data-action="confirm-reset">Reset</button></div></div></div>');
        overlay.querySelector('.dConfirm button').focus();
    }

    function runAction(action, id, target) {
        switch (action) {
            case 'close':
                dialogHelper.close(dialog);
                break;
            case 'off':
                change(takeOff(layout, id), `${titleOf(id)} taken off Home`);
                break;
            case 'add': {
                const row = addRows[Number(target.closest('[data-index]').dataset.index)];
                change(putOn(layout, row.genre ?? row.id), `${row.title} added to Home`);
                break;
            }
            case 'undo':
                undo();
                break;
            case 'top':
                dismiss();
                change(moveOnHome(layout, id, 0), `${titleOf(id)} moved to the top`);
                break;
            case 'sheet-off':
                dismiss();
                change(takeOff(layout, id), `${titleOf(id)} taken off Home`);
                break;
            case 'reset':
                confirmReset();
                break;
            case 'confirm-reset':
                dismiss();
                change(normalizeLayout(null), 'Reset to the default rows');
                break;
            case 'dismiss':
                dismiss();
                break;
        }
    }

    dialog.addEventListener('click', event => {
        if (dragging || suppressClick) return;
        const target = event.target.closest('[data-action], [data-size], [data-tab]');
        if (!target) return;
        const id = target.dataset.id ?? target.closest('[data-id]')?.dataset.id;
        if (target.dataset.size) {
            if (target.dataset.size !== layout.size) change({ ...layout, size: target.dataset.size }, `Poster size: ${target.textContent.trim()}`);
        } else if (target.dataset.tab) {
            tab = target.dataset.tab;
            render();
        } else {
            runAction(target.dataset.action, id, target);
        }
    });

    dialog.addEventListener('input', event => {
        if (!event.target.matches('.dSearchInput')) return;
        query = event.target.value;
        const caret = event.target.selectionStart;
        render();
        const search = dialog.querySelector('.dSearchInput');
        search.focus();
        search.setSelectionRange(caret, caret);
    });

    function suppressNextClick(ms) {
        suppressClick = true;
        setTimeout(() => {
            suppressClick = false;
        }, ms);
    }

    // Drag a row by its ⋮⋮ handle; long-press anywhere else on it for Move to top and Take off Home.
    dialog.addEventListener('pointerdown', event => {
        const row = event.target.closest('.dRow:not(.off)');
        if (!row || event.target.closest('.dSwitch')) return;
        if (event.target.closest('.dHandle')) {
            event.preventDefault();
            const rows = [...content.querySelectorAll('.dRow')];
            const step = rows[1] ? rows[1].getBoundingClientRect().top - rows[0].getBoundingClientRect().top : row.getBoundingClientRect().height;
            const from = rows.indexOf(row);
            dragging = { row, rows, id: row.dataset.id, start: event.clientY, from, to: from, step, scroll: scroller.scrollTop };
            row.classList.add('dragging');
            row.setPointerCapture?.(event.pointerId);
            return;
        }
        const startY = event.clientY;
        const timer = setTimeout(() => {
            press = null;
            suppressNextClick(400);
            showSheet(row.dataset.id);
        }, LONG_PRESS_MS);
        press = { startY, timer };
    });

    dialog.addEventListener('pointermove', event => {
        if (press && Math.abs(event.clientY - press.startY) > 8) {
            clearTimeout(press.timer);
            press = null;
        }
        if (!dragging) return;
        // Scroll near the edges while dragging.
        const bounds = scroller.getBoundingClientRect();
        if (event.clientY < bounds.top + 60) {
            scroller.scrollTop -= 10;
        } else if (event.clientY > bounds.bottom - 60) {
            scroller.scrollTop += 10;
        }
        const delta = event.clientY - dragging.start + (scroller.scrollTop - dragging.scroll);
        dragging.row.style.transform = `translateY(${delta}px)`;
        const to = Math.max(0, Math.min(dragging.rows.length - 1, dragging.from + Math.round(delta / dragging.step)));
        dragging.to = to;
        dragging.rows.forEach((other, index) => {
            if (other === dragging.row) return;
            let shift = 0;
            if (dragging.from < to && index > dragging.from && index <= to) shift = -dragging.step;
            if (dragging.from > to && index < dragging.from && index >= to) shift = dragging.step;
            other.style.transform = shift ? `translateY(${shift}px)` : '';
        });
    });

    const endPointer = () => {
        if (press) {
            clearTimeout(press.timer);
            press = null;
        }
        if (!dragging) return;
        const { id, from, to } = dragging;
        dragging = null;
        suppressNextClick(0);
        if (to === from) {
            render();
        } else {
            change(moveOnHome(layout, id, to), `Moved ${titleOf(id)} to position ${to + 1}`);
        }
    };
    dialog.addEventListener('pointerup', endPointer);
    dialog.addEventListener('pointercancel', endPointer);

    // The server's genres load in the background and refresh when the library changes.
    let closed = false;
    let loading = false;
    const api = ServerConnections.currentApiClient();
    const isCurrent = () => !closed && key === currentLayoutKey();
    async function refreshCatalog() {
        if (loading || !isCurrent() || !api?.getGenres) return;
        loading = true;
        try {
            const next = await loadGenreCatalog(api, isCurrent);
            if (isCurrent() && JSON.stringify(next) !== JSON.stringify(catalog)) {
                catalog = next;
                if (!dragging) render();
            }
        } catch {
            if (isCurrent() && tab === 'add') showSnack('Couldn’t load your server’s genres. Trying again shortly.');
        } finally {
            loading = false;
        }
    }
    const interval = setInterval(refreshCatalog, 30000);
    let refreshTimer;
    const unsubscribe = api?.subscribe?.(['LibraryChanged'], () => {
        clearTimeout(refreshTimer);
        refreshTimer = setTimeout(refreshCatalog, 1000);
    });
    // The page shrinks when the on-screen keyboard opens; keep the search field visible.
    const keepFocusVisible = () => {
        const active = document.activeElement;
        if (active?.matches?.('input') && dialog.contains(active)) active.scrollIntoView({ block: 'center' });
    };
    window.addEventListener('resize', keepFocusVisible);
    dialog.addEventListener('close', () => {
        closed = true;
        window.removeEventListener('resize', keepFocusVisible);
        clearInterval(interval);
        clearTimeout(refreshTimer);
        clearTimeout(snackTimer);
        unsubscribe?.();
        dialog.remove();
    });

    render();
    refreshCatalog();
    dialogHelper.open(dialog);
}
