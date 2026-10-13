import { getDiscoveryGenres } from '../homesections/sections/moviePaging';

export const genreId = genre => genre.id || 'genre:' + genre.title;
/**
 * Rows that used to come from the Home section slots in Settings > Home. They are built-in Discovery rows now; each
 * maps to the Home section type that draws it.
 */
export const slotRows = [
    { id: 'mymedia', title: 'My Media', types: [ 'smalllibrarytiles', 'librarybuttons' ] },
    { id: 'resumeaudio', title: 'Continue Listening', types: [ 'resumeaudio' ] },
    { id: 'resumebook', title: 'Continue Reading', types: [ 'resumebook' ] },
    { id: 'livetv', title: 'Live TV', types: [ 'livetv' ] },
    { id: 'activerecordings', title: 'Recordings', types: [ 'activerecordings' ] },
    { id: 'latestother', title: 'Recently Added Music & More', types: [] }
];
/** The Home section slots' defaults, used when an account never set them. */
export const DEFAULT_SLOTS = [ 'smalllibrarytiles', 'resume', 'resumeaudio', 'resumebook', 'livetv', 'nextup', 'latestmedia', 'moviediscovery', 'none', 'none' ];
export const sections = [
    { id: 'resume', title: 'Continue Watching' },
    { id: 'nextup', title: 'Up Next' },
    { id: 'latestmovies', title: 'Recently Added Movies' },
    { id: 'latesttv', title: 'Recently Added TV' },
    ...getDiscoveryGenres().map(genre => ({ id: genreId(genre), title: genre.title })),
    ...slotRows.map(({ id, title }) => ({ id, title }))
];
/** Whether the old Home section slots showed [row]. "Recently Added Music & More" was always shown. */
function shownBySlots(row, slots) {
    return !row.types.length || row.types.some(type => slots.includes(type));
}
/**
 * [slots] are the account's Home section slots. A layout saved before the slot rows joined Discovery has no `known`
 * list: each slot row starts On Home only if the slots showed it, so Home looks the same after the update.
 */
export function normalizeLayout(value, slots = DEFAULT_SLOTS) {
    const customGenres = [...new Map((Array.isArray(value?.customGenres) ? value.customGenres : [])
        .filter(genre => typeof genre?.title === 'string' && genre.title.trim()
            && typeof genre.genreId === 'string' && /^[a-f0-9]{32}$|^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(genre.genreId))
        .map(genre => [genre.genreId.replace(/-/g, '').toLowerCase(), {
            id: 'genre:server:' + genre.genreId.replace(/-/g, '').toLowerCase(),
            genreId: genre.genreId.replace(/-/g, '').toLowerCase(), title: genre.title.trim()
        }])).values()];
    const removedGenres = [...new Set((Array.isArray(value?.removedGenres) ? value.removedGenres : [])
        .filter(id => sections.some(section => section.id === id && id.startsWith('genre:'))))];
    const ids = [...sections, ...customGenres].map(section => section.id).filter(id => !removedGenres.includes(id));
    const order = Array.isArray(value?.order) ? value.order.filter(id => ids.includes(id)) : [];
    const known = Array.isArray(value?.known) ? value.known : [];
    const hidden = Array.isArray(value?.hidden) ? value.hidden.filter(id => ids.includes(id)) : [];
    const newSlotRows = slotRows.filter(row => !known.includes(row.id) && !shownBySlots(row, slots)).map(row => row.id);
    return {
        customGenres,
        removedGenres,
        order: [...new Set([...order, ...ids])],
        // Placed rows go first, so Add to Home (newest first) lists them after rows the person took off.
        hidden: [...new Set([...newSlotRows, ...hidden])],
        size: ['small', 'medium', 'large'].includes(value?.size) ? value.size : 'medium',
        known: slotRows.map(row => row.id)
    };
}
export function visibleSections(layout) {
    return layout.order.filter(id => !layout.hidden.includes(id));
}
export function orderedGenres(layout) {
    const genres = [...getDiscoveryGenres(), ...(layout.customGenres || [])];
    return visibleSections(layout).flatMap(id => genres.filter(genre => genreId(genre) === id));
}
export function moveSection(layout, id, delta) {
    const order = [...layout.order];
    const from = order.indexOf(id);
    const to = Math.max(0, Math.min(order.length - 1, from + delta));
    if (from >= 0) order.splice(to, 0, ...order.splice(from, 1));
    return { ...layout, order };
}
export function layoutKey(serverId, userId) {
    return serverId && userId ? 'discovery-layout-v1:' + JSON.stringify([serverId, userId]) : null;
}
export function readLayout(storage, key, slots = DEFAULT_SLOTS) {
    try {
        return normalizeLayout(key ? JSON.parse(storage.getItem(key)) : null, slots);
    } catch {
        return normalizeLayout(null, slots);
    }
}
export function writeLayout(storage, key, value) {
    if (!key) throw new Error('Sign in before customizing Discovery.');
    storage.setItem(key, JSON.stringify(normalizeLayout(value)));
}

export function layoutSections(layout) {
    return [...sections, ...(layout.customGenres || [])];
}
export function selectedGenreId(layout, genre) {
    return layoutSections(layout).find(section => section.title.toLocaleLowerCase() === genre.title.toLocaleLowerCase()
        || section.id === genre.id)?.id;
}
export function addGenre(layout, genre) {
    const existing = selectedGenreId(layout, genre);
    if (existing) {
        return normalizeLayout({ ...layout, hidden: layout.hidden.filter(id => id !== existing),
            removedGenres: (layout.removedGenres || []).filter(id => id !== existing) });
    }
    return normalizeLayout({ ...layout, customGenres: [...(layout.customGenres || []), genre] });
}
export function removeGenre(layout, id) {
    if (!id.startsWith('genre:')) return layout;
    return normalizeLayout({ ...layout,
        removedGenres: [...(layout.removedGenres || []), id],
        customGenres: layout.customGenres.filter(genre => genre.id !== id) });
}

// Customize Discovery (2026-10-13 redesign): every row is either On Home or in Add to Home.
// These keep the stored format above, so Home and saved layouts work unchanged.

/** The rows On Home, in order. */
export function onHome(layout) {
    return visibleSections(layout);
}
/** Take a row off Home. It goes to the top of Add to Home. */
export function takeOff(layout, id) {
    if (!layout.order.includes(id) || layout.hidden.includes(id)) return layout;
    return { ...layout, hidden: [...layout.hidden, id] };
}
/** Put a row from Add to Home at the end of On Home: a row taken off, a removed built-in genre, or a server genre. */
export function putOn(layout, idOrGenre) {
    let next = typeof idOrGenre === 'string' ? layout : addGenre(layout, idOrGenre);
    const id = typeof idOrGenre === 'string' ? idOrGenre : selectedGenreId(next, idOrGenre);
    if (!id) return layout;
    if ((next.removedGenres || []).includes(id)) {
        next = normalizeLayout({ ...next, removedGenres: next.removedGenres.filter(other => other !== id) });
    }
    const hidden = next.hidden.filter(other => other !== id);
    const order = [...next.order.filter(other => other !== id), id];
    return { ...next, hidden, order };
}
/** Move an On Home row to [index] among the rows On Home. Rows that are off keep their place behind them. */
export function moveOnHome(layout, id, index) {
    const shown = onHome(layout).filter(other => other !== id);
    if (shown.length === onHome(layout).length) return layout;
    shown.splice(Math.max(0, Math.min(index, shown.length)), 0, id);
    return { ...layout, order: [...shown, ...layout.order.filter(other => !shown.includes(other))] };
}
/**
 * Add to Home, filtered by [query]: rows taken off (most recent first), removed built-in genres, then the server's
 * genres that are not rows yet. Each entry is { id, title, kind, genre? }.
 */
export function addToHomeRows(layout, catalog, query = '') {
    const all = layoutSections(layout);
    const titleOf = id => all.find(section => section.id === id)?.title ?? id;
    const off = [...layout.hidden].reverse().map(id => ({ id, title: titleOf(id) }));
    const removed = (layout.removedGenres || []).map(id => ({ id, title: titleOf(id) }));
    const server = catalog.filter(genre => !selectedGenreId(layout, genre))
        .map(genre => ({ id: genre.id, title: genre.title, genre }));
    const q = query.trim().toLocaleLowerCase();
    return [...off, ...removed, ...server].filter(row => row.title.toLocaleLowerCase().includes(q));
}
/** Continue Watching, Up Next, the Recently Added rows and the former Home section rows are built in; everything else is a genre. */
export function isBuiltIn(id) {
    return !id.startsWith('genre:');
}
/**
 * A built-in genre row matches none of the server's genres, so Home would skip it. Unknown until the server's genres
 * have loaded ([catalog] empty), when nothing is marked.
 */
export function isEmptyRow(id, catalog) {
    if (!catalog.length || !id.startsWith('genre:') || id.startsWith('genre:server:')) return false;
    const builtIn = getDiscoveryGenres().find(genre => genreId(genre) === id);
    if (!builtIn) return false;
    const names = new Set(catalog.map(genre => genre.title.toLocaleLowerCase()));
    return !builtIn.filter.split('|').some(name => names.has(name.toLocaleLowerCase()));
}
