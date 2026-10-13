import { describe, expect, it } from 'vitest';
import { selectHomeSections } from './homeSectionSelection';
import { DEFAULT_SLOTS, normalizeLayout } from '../discoverySettings/discoveryLayout';

describe('Home order comes from Discovery', () => {
    it('defaults to continue watching, up next, movies, TV, then genres', () => {
        const sections = selectHomeSections();
        expect(sections.slice(0, 6)).toEqual(['resume', 'nextup', 'latestmovies', 'latesttv', 'genre:Action', 'genre:Comedy']);
    });
    it('respects arbitrary interleaving and rows taken off Home', () => {
        const layout = normalizeLayout({ order: ['genre:Horror', 'latesttv', 'resume'], hidden: ['latestmovies', 'nextup'] });
        const sections = selectHomeSections(layout);
        expect(sections.slice(0, 3)).toEqual(['genre:Horror', 'latesttv', 'resume']);
        expect(sections).not.toContain('latestmovies');
        expect(sections).not.toContain('nextup');
        expect(sections.filter((id: string) => id === 'resume')).toHaveLength(1);
    });
    it('draws My Media as library tiles, wherever it is placed', () => {
        const layout = normalizeLayout({ order: ['mymedia', 'resume'], known: ['mymedia'] });
        expect(selectHomeSections(layout).slice(0, 2)).toEqual(['smalllibrarytiles', 'resume']);
    });
});

describe('former Home section rows', () => {
    it('start on Home in a new layout when the default slots show them', () => {
        const layout = normalizeLayout(null);
        expect(layout.hidden).toEqual(['activerecordings']);
        expect(layout.order.slice(-6)).toEqual(['mymedia', 'resumeaudio', 'resumebook', 'livetv', 'activerecordings', 'latestother']);
    });
    it('keep an existing Home the same: rows the old slots did not show start in Add to Home', () => {
        const slots = ['latestmedia', 'resume', 'livetv', 'none', 'none', 'none', 'none', 'none', 'none', 'none'];
        const layout = normalizeLayout({ order: ['resume', 'nextup'], hidden: [] }, slots);
        expect(layout.hidden).toEqual(['mymedia', 'resumeaudio', 'resumebook', 'activerecordings']);
        expect(selectHomeSections(layout)).toContain('livetv');
        expect(selectHomeSections(layout)).toContain('latestother');
    });
    it('treats the small library buttons slot as My Media', () => {
        const slots = ['librarybuttons', ...DEFAULT_SLOTS.slice(1)];
        expect(normalizeLayout({ order: [] }, slots).hidden).not.toContain('mymedia');
    });
    it('are only placed once: a saved choice wins over the slots afterwards', () => {
        const first = normalizeLayout({ order: [] }, DEFAULT_SLOTS);
        const putBack = normalizeLayout({ ...first, hidden: first.hidden.filter(id => id !== 'activerecordings') }, ['none']);
        expect(putBack.hidden).toEqual([]);
    });
});
