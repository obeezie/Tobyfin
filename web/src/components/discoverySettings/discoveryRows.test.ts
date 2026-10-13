import { describe, expect, it } from 'vitest';
import { addToHomeRows, isBuiltIn, isEmptyRow, moveOnHome, normalizeLayout, onHome, putOn, removeGenre, takeOff } from './discoveryLayout';

const western = { id: 'genre:server:0123456789abcdef0123456789abcdef', genreId: '0123456789abcdef0123456789abcdef', title: 'Western' };
const serverAction = { id: 'genre:server:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', genreId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', title: 'Action' };

describe('On Home and Add to Home', () => {
    it('taking a row off moves it to the top of Add to Home, newest first', () => {
        let layout = normalizeLayout(null);
        layout = takeOff(layout, 'genre:Horror');
        layout = takeOff(layout, 'nextup');
        expect(onHome(layout)).not.toContain('nextup');
        expect(addToHomeRows(layout, []).slice(0, 2).map(row => row.id)).toEqual(['nextup', 'genre:Horror']);
    });

    it('putting a row back adds it to the end of On Home', () => {
        let layout = takeOff(normalizeLayout(null), 'resume');
        layout = putOn(layout, 'resume');
        expect(onHome(layout).at(-1)).toBe('resume');
        expect(layout.hidden).toEqual([]);
    });

    it('server genres can be added, and ones matching a built-in row are not offered twice', () => {
        const layout = normalizeLayout(null);
        const offered = addToHomeRows(layout, [western, serverAction]).map(row => row.title);
        expect(offered).toEqual(['Western']);
        const added = putOn(layout, western);
        expect(onHome(added).at(-1)).toBe(western.id);
        expect(addToHomeRows(added, [western])).toEqual([]);
    });

    it('a removed built-in genre is offered again and comes back at the end', () => {
        const layout = removeGenre(normalizeLayout(null), 'genre:Horror');
        expect(addToHomeRows(layout, []).map(row => row.id)).toContain('genre:Horror');
        expect(onHome(putOn(layout, 'genre:Horror')).at(-1)).toBe('genre:Horror');
    });

    it('moves a row among the rows On Home, ignoring rows that are off', () => {
        const layout = takeOff(normalizeLayout(null), 'nextup');
        const moved = moveOnHome(layout, 'genre:Horror', 1);
        expect(onHome(moved).slice(0, 2)).toEqual(['resume', 'genre:Horror']);
        expect(moved.hidden).toEqual(['nextup']);
        expect(moveOnHome(layout, 'nextup', 0)).toBe(layout);
    });

    it('filters Add to Home by search', () => {
        const layout = takeOff(normalizeLayout(null), 'genre:Comedy');
        expect(addToHomeRows(layout, [western], 'wes').map(row => row.title)).toEqual(['Western']);
    });

    it('marks built-in genres the server has nothing for, once its genres are known', () => {
        const catalog = [western, { ...serverAction, title: 'Documentary' }];
        expect(isEmptyRow('genre:Documentaries', catalog)).toBe(false);
        expect(isEmptyRow('genre:Horror', catalog)).toBe(true);
        expect(isEmptyRow('genre:Horror', [])).toBe(false);
        expect(isEmptyRow('resume', catalog)).toBe(false);
        expect(isEmptyRow(western.id, catalog)).toBe(false);
    });

    it('labels only the built-in rows', () => {
        expect(isBuiltIn('resume')).toBe(true);
        expect(isBuiltIn('genre:Horror')).toBe(false);
        expect(isBuiltIn(western.id)).toBe(false);
    });
});
