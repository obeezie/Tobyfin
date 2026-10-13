import { beforeEach, describe, expect, it } from 'vitest';
import { layoutKey, normalizeLayout, orderedGenres, readLayout, writeLayout, moveSection, visibleSections, sections, addGenre, removeGenre } from './discoveryLayout';

describe('saved discovery layouts', () => {
    beforeEach(() => localStorage.clear());
    it('isolates server, account and device storage', () => {
        const a = layoutKey('server1', 'user1');
        writeLayout(localStorage, a, { size: 'large', hidden: ['genre:Action'] });
        expect(readLayout(localStorage, a).size).toBe('large');
        expect(readLayout(localStorage, layoutKey('server1', 'user2')).size).toBe('medium');
        expect(readLayout(localStorage, layoutKey('server2', 'user1')).hidden).toEqual(['activerecordings']);
        expect(readLayout(sessionStorage, a).size).toBe('medium');
    });
    it('repairs old or invalid data, removes duplicates and appends newly available sections', () => {
        const layout = normalizeLayout({ order: ['genre:Horror', 'removed', 'genre:Horror'], hidden: ['removed', 'resume'], size: 'huge' });
        expect(layout.order[0]).toBe('genre:Horror');
        expect(layout.order).toHaveLength(sections.length);
        expect(layout.hidden).toEqual(['activerecordings', 'resume']);
        expect(layout.size).toBe('medium');
        localStorage.setItem('broken', '{oops');
        expect(readLayout(localStorage, 'broken')).toEqual(normalizeLayout(null));
    });
    it('supports hiding everything and restores defaults explicitly', () => {
        const layout = normalizeLayout({ hidden: sections.map(section => section.id) });
        expect(visibleSections(layout)).toEqual([]);
        expect(normalizeLayout(null).hidden).toEqual(['activerecordings']);
    });
    it('moves a section without changing others and selects genres in the saved order', () => {
        const original = normalizeLayout(null);
        const moved = moveSection(original, 'genre:Horror', -6);
        expect(moved.order[0]).toBe('genre:Horror');
        expect(original.order[0]).toBe('resume');
        const genres = orderedGenres({ ...moved, hidden: ['genre:Comedy'] });
        expect(genres[0].title).toBe('Horror');
        expect(genres.some((genre: { title: string }) => genre.title === 'Comedy')).toBe(false);
    });
    it('does not persist a draft until Save', () => {
        const key = layoutKey('server', 'user');
        const draft = moveSection(readLayout(localStorage, key), 'genre:Horror', -6);
        expect(readLayout(localStorage, key).order[0]).toBe('resume');
        writeLayout(localStorage, key, draft);
        expect(readLayout(localStorage, key).order[0]).toBe('genre:Horror');
    });
});

describe('server genre selections', () => {
    const genre = { id: 'genre:server:0123456789abcdef0123456789abcdef', genreId: '0123456789abcdef0123456789abcdef', title: 'Western' };
    it('persists exact server IDs, order and visibility while retaining old defaults', () => {
        const draft = addGenre(normalizeLayout(null), genre);
        draft.hidden = ['genre:Action'];
        const moved = moveSection(draft, genre.id, -100);
        writeLayout(localStorage, 'custom', moved);
        const restored = readLayout(localStorage, 'custom');
        expect(restored.order[0]).toBe(genre.id);
        expect(orderedGenres(restored)[0]).toEqual(genre);
        expect(removeGenre(restored, genre.id).order).not.toContain(genre.id);
        expect(normalizeLayout(null).customGenres).toEqual([]);
    });
    it('does not duplicate an existing genre and can restore a hidden default', () => {
        const draft = addGenre(normalizeLayout(null), genre);
        expect(addGenre(draft, genre).customGenres).toHaveLength(1);
        const action = { ...genre, title: 'Action' };
        const hidden = normalizeLayout({ hidden: ['genre:Action'] });
        expect(addGenre(hidden, action).customGenres).toEqual([]);
        expect(addGenre(hidden, action).hidden).toEqual(['activerecordings']);
    });
});

it('removes a default across saves and restores it without a duplicate', () => {
    const removed = removeGenre(normalizeLayout(null), 'genre:Action');
    writeLayout(localStorage, 'removed', removed);
    const saved = readLayout(localStorage, 'removed');
    expect(saved.order).not.toContain('genre:Action');
    expect(orderedGenres(saved).some((genre: { title: string }) => genre.title === 'Action')).toBe(false);
    const restored = addGenre(saved, { id: 'genre:Action', title: 'Action' });
    expect(restored.order.filter((id: string) => id === 'genre:Action')).toHaveLength(1);
    expect(restored.customGenres).toEqual([]);
    expect(normalizeLayout(null).order).toContain('genre:Action');
    expect(removeGenre(saved, 'resume')).toEqual(saved);
});
