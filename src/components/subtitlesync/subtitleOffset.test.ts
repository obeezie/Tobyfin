import { describe, expect, it } from 'vitest';
import { parseSubtitleOffset, stepSubtitleOffset } from './subtitleOffset';
describe('subtitle timing entry', () => {
    it('accepts precise positive, negative and locale decimal values', () => {
        expect(parseSubtitleOffset('-1.234s')).toBe(-1.234);
        expect(parseSubtitleOffset('.05')).toBe(0.05);
        expect(parseSubtitleOffset(' +2,5 ')).toBe(2.5);
        expect(parseSubtitleOffset('-600')).toBe(-600);
    });
    it('rejects malformed and out of range input without a partial match', () => {
        for (const input of ['text 2', '1x2', 'NaN', '', '600.01', '-600.01']) {
            expect(parseSubtitleOffset(input)).toBeNull();
        }
    });
    it('steps precisely and stops at the limits', () => {
        expect(stepSubtitleOffset(0.2, 0.1)).toBe(0.3);
        expect(stepSubtitleOffset(-0.2, -0.1)).toBe(-0.3);
        expect(stepSubtitleOffset(600, 1)).toBe(600);
        expect(stepSubtitleOffset(-600, -1)).toBe(-600);
    });
});