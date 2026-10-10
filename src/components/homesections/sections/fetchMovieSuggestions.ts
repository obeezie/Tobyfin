import { getDiscoveryLayout } from 'components/discoverySettings/discoverySettings';
import { orderedGenres } from 'components/discoverySettings/discoveryLayout';
import type { Api } from '@jellyfin/sdk/lib/api';
import type { MovieApiGetMovieRecommendationsRequest } from '@jellyfin/sdk/lib/generated-client/api/movie-api';
import { BaseItemKind } from '@jellyfin/sdk/lib/generated-client/models/base-item-kind';
import type { ItemFields } from '@jellyfin/sdk/lib/generated-client/models/item-fields';
import type { ItemSortBy } from '@jellyfin/sdk/lib/generated-client/models/item-sort-by';
import type { SortOrder } from '@jellyfin/sdk/lib/generated-client/models/sort-order';
import { getLibraryApi } from '@jellyfin/sdk/lib/utils/api/library-api';
import type { AxiosRequestConfig } from 'axios';

import { loadMovieSuggestions } from './movieGenres';

export async function fetchMovieItems(api: Api, userId: string | undefined, query: Record<string, unknown>, options?: AxiosRequestConfig) {
    const response = await getLibraryApi(api).getItems({
        userId,
        parentId: query.ParentId as string | undefined,
        includeItemTypes: [query.IncludeItemTypes === 'Series' ? BaseItemKind.Series : BaseItemKind.Movie],
        recursive: true,
        fields: String(query.Fields || '').split(',').filter(Boolean) as ItemFields[],
        genreIds: query.GenreIds ? [String(query.GenreIds)] : undefined,
        genres: query.Genres ? String(query.Genres).split('|') : undefined,
        sortBy: query.SortBy ? String(query.SortBy).split(',') as ItemSortBy[] : undefined,
        sortOrder: query.SortOrder ? [query.SortOrder as SortOrder] : undefined,
        isPlayed: query.IsPlayed as boolean | undefined,
        isFavorite: query.IsFavorite as boolean | undefined,
        enableUserData: true,
        enableTotalRecordCount: query.EnableTotalRecordCount as boolean | undefined,
        limit: query.Limit as number | undefined,
        startIndex: query.StartIndex as number | undefined,
        minCommunityRating: query.MinCommunityRating as number | undefined
    }, options);
    return response.data;
}

export async function fetchMovieSuggestions(api: Api, params: MovieApiGetMovieRecommendationsRequest, options?: AxiosRequestConfig) {
    let initialLoad = true;
    const fetchMovies = (query: Record<string, unknown>) => fetchMovieItems(api, params.userId, query, {
        ...options, signal: initialLoad ? options?.signal : undefined
    });
    try {
        return await loadMovieSuggestions(fetchMovies, params.parentId, params.itemLimit ?? 20, orderedGenres(getDiscoveryLayout()));
    } finally {
        // Query cancellation only applies to the initial fetch. A row's later
        // page requests must not inherit that completed query's abort signal.
        initialLoad = false;
    }
}
