import { describe, expect, it } from 'vitest';

import config from '../../config.json';
import defaultTheme from '..';

describe('Tobyfin theme', () => {
    it('is the only default theme in the web config', () => {
        const defaults = config.themes.filter(theme => 'default' in theme && theme.default);

        expect(defaults.map(theme => theme.id)).toEqual([ 'tobyfin' ]);
    });

    it('is a registered color scheme, used before the user picks one', () => {
        expect(defaultTheme.colorSchemes).toHaveProperty('tobyfin');
        expect(defaultTheme.defaultColorScheme).toBe('tobyfin');
    });
});
