import { QueryClient, dehydrate } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import { shouldPersistQuery } from './queryPersistence';

describe('query persistence', () => {
    it('retains normal data and excludes pagination controllers from IndexedDB snapshots', () => {
        const client = new QueryClient();
        client.setQueryDefaults(['MovieRecommendations'], { meta: { persist: false } });
        client.setQueryData(['MovieRecommendations'], [{ LoadMore: async () => [] }]);
        client.setQueryData(['Library'], { Items: [{ Id: 'movie' }] });
        const snapshot = dehydrate(client, { shouldDehydrateQuery: shouldPersistQuery });
        expect(snapshot.queries.map(query => query.queryKey)).toEqual([['Library']]);
        // eslint-disable-next-line compat/compat -- Node test simulates IndexedDB's structured cloning.
        expect(() => structuredClone(snapshot)).not.toThrow();
        client.clear();
    });
});
