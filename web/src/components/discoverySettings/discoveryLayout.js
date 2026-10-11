import { getDiscoveryGenres } from '../homesections/sections/moviePaging';

export const genreId = genre => genre.id || 'genre:' + genre.title;
export const sections = [
    { id: 'resume', title: 'Continue Watching' },
    { id: 'nextup', title: 'Up Next' },
    { id: 'latestmovies', title: 'Recently Added Movies' },
    { id: 'latesttv', title: 'Recently Added TV' },
    ...getDiscoveryGenres().map(genre => ({ id: genreId(genre), title: genre.title }))
];
export function normalizeLayout(value) {
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
    return {
        customGenres,
        removedGenres,
        order: [...new Set([...order, ...ids])],
        hidden: Array.isArray(value?.hidden) ? [...new Set(value.hidden.filter(id => ids.includes(id)))] : [],
        size: ['small', 'medium', 'large'].includes(value?.size) ? value.size : 'medium'
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
export function readLayout(storage, key) {
    try {
        return normalizeLayout(key ? JSON.parse(storage.getItem(key)) : null);
    } catch {
        return normalizeLayout(null);
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
