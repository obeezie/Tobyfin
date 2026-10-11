import React, { useEffect, useRef, useState } from 'react';

import Cards from 'components/cardbuilder/Card/Cards';
import SectionContainer from 'components/common/SectionContainer';
import globalize from 'lib/globalize';
import type { CardOptions } from 'types/cardOptions';
import type { ItemDto } from 'types/base/models/item-dto';

type MoreMovies = { Items: ItemDto[]; HasMore: boolean };

export default function InfiniteMovieSuggestions({ title, headerUrl, initialItems, loadMore, cardOptions }: Readonly<{
    title: string;
    headerUrl?: string;
    initialItems: ItemDto[];
    loadMore: () => Promise<MoreMovies>;
    cardOptions: CardOptions;
}>) {
    const [items, setItems] = useState(initialItems);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(false);
    const [failed, setFailed] = useState(false);
    const buttonRef = useRef<HTMLButtonElement>(null);
    const pending = useRef(false);
    const active = useRef(true);
    const requestRef = useRef<() => Promise<void>>(async () => undefined);

    requestRef.current = async () => {
        if (pending.current || !hasMore) return;
        pending.current = true;
        setLoading(true);
        try {
            const result = await loadMore();
            if (!active.current) return;
            setItems(previous => [...previous, ...result.Items]);
            setHasMore(result.HasMore);
            setFailed(false);
        } catch {
            if (active.current) setFailed(true);
        } finally {
            pending.current = false;
            if (active.current) setLoading(false);
        }
    };

    useEffect(() => {
        active.current = true;
        return () => {
            active.current = false;
        };
    }, []);

    useEffect(() => {
        if (!hasMore || failed || typeof IntersectionObserver === 'undefined' || !buttonRef.current) return;
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) void requestRef.current();
        }, { rootMargin: '100px', threshold: 0.1 });
        observer.observe(buttonRef.current);
        return () => observer.disconnect();
    }, [hasMore, failed, items.length]);

    return (
        <SectionContainer
            sectionHeaderProps={{ title, url: headerUrl }}
            items={items}
            itemsContainerProps={{ queryKey: cardOptions.queryKey || ['MovieDiscoveryRow', title] }}
        >
            <>
                <Cards items={items} cardOptions={cardOptions} />
                {hasMore && (
                    <button
                        ref={buttonRef}
                        type='button'
                        className='button-flat emby-button'
                        style={{ minWidth: 160, alignSelf: 'center' }}
                        disabled={loading}
                        onClick={() => { void requestRef.current(); }}
                    >
                        {globalize.translate(failed ? 'MovieDiscoveryRetry' : 'MovieDiscoveryLoadMore')}
                    </button>
                )}
            </>
        </SectionContainer>
    );
}
