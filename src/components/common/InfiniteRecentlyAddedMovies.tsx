import React, { useState } from 'react';

import { useApi } from 'hooks/useApi';
import { fetchMovieItems } from 'components/homesections/sections/fetchMovieSuggestions';
import { createMoviePager } from 'components/homesections/sections/moviePaging';
import InfiniteMovieSuggestions from './InfiniteMovieSuggestions';
import type { CardOptions } from 'types/cardOptions';
import type { ItemDto } from 'types/base/models/item-dto';
import type { ParentId } from 'types/library';

export default function InfiniteRecentlyAddedMovies({ title, headerUrl, parentId, initialItems, cardOptions, isSeries = false }: Readonly<{
    title: string;
    isSeries?: boolean;
    headerUrl?: string;
    parentId: ParentId;
    initialItems: ItemDto[];
    cardOptions: CardOptions;
}>) {
    const { api, user } = useApi();
    // A live pager and its displayed items belong to this mounted row. Updating
    // resume/played data elsewhere must not replace its roster or reset offsets.
    const [loadMore] = useState(() => createMoviePager(
        (query: Record<string, unknown>) => fetchMovieItems(api!, user?.Id, query),
        { ParentId: parentId, IncludeItemTypes: isSeries ? 'Series' : 'Movie', Fields: 'PrimaryImageAspectRatio,MediaSourceCount',
            SortBy: isSeries ? 'DateLastContentAdded,SortName' : 'DateCreated,SortName', SortOrder: 'Descending' },
        initialItems
    ));
    return <InfiniteMovieSuggestions title={title} headerUrl={headerUrl} initialItems={initialItems} loadMore={loadMore} cardOptions={cardOptions} />;
}
