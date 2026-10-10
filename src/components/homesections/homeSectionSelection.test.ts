import { describe, expect, it } from 'vitest';
import { selectHomeSections } from './homeSectionSelection';
import { normalizeLayout } from '../discoverySettings/discoveryLayout';

describe('customizable Home order', () => {
    it('defaults to continue watching, up next, movies, TV, then genres despite old saved sections', () => {
        const sections = selectHomeSections({ get: () => 'none' });
        expect(sections.slice(0, 6)).toEqual(['resume', 'nextup', 'latestmovies', 'latesttv', 'genre:Action', 'genre:Comedy']);
    });
    it('respects arbitrary interleaving and hidden sections without re-inserting managed sections', () => {
        const layout = normalizeLayout({ order: ['genre:Horror', 'latesttv', 'resume'], hidden: ['latestmovies', 'nextup'] });
        const sections = selectHomeSections({ get: (key: string) => key === 'homesection0' ? 'latestmedia' : undefined }, layout);
        expect(sections.slice(0, 3)).toEqual(['genre:Horror', 'latesttv', 'resume']);
        expect(sections).not.toContain('latestmovies');
        expect(sections).not.toContain('nextup');
        expect(sections).not.toContain('latestmedia');
        expect(sections.filter(id => id === 'resume')).toHaveLength(1);
    });
    it('uses the local layout instead of the old server-wide genre toggle', () => {
        const sections = selectHomeSections({ get: (key: string) => key === 'enableMovieDiscovery' ? 'false' : undefined });
        expect(sections.some(id => id.startsWith('genre:'))).toBe(true);
        expect(sections[0]).toBe('resume');
    });
});
