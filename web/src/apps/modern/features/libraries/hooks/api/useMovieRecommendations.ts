import { getDiscoveryLayout } from 'components/discoverySettings/discoverySettings';
import type { Api } from '@jellyfin/sdk/lib/api';
import type { MovieApiGetMovieRecommendationsRequest } from '@jellyfin/sdk/lib/generated-client/api/movie-api';
import { queryOptions, useQuery } from '@tanstack/react-query';

import { useApi } from 'hooks/useApi';
import { fetchMovieSuggestions } from 'components/homesections/sections/fetchMovieSuggestions';

/** Query options for fetching movie recommendations. */
export const getMovieRecommendationsQuery = (
    api?: Api,
    params: MovieApiGetMovieRecommendationsRequest = {},
    enabled = true
) => queryOptions({
    queryKey: ['MovieRecommendations', 'genres-v7', api?.basePath, params?.userId, params, JSON.stringify(getDiscoveryLayout())],
    queryFn: ({ signal }) => fetchMovieSuggestions(api!, params, { signal }),
    // Rows contain live pagination controllers, not persistable server DTOs.
    meta: { persist: false },
    // Keep the shuffled roster stable while browsing, even after websocket
    // invalidations. A deliberate page reload starts a fresh discovery session.
    staleTime: 'static',
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchOnMount: false,
    gcTime: 0,
    enabled: !!api && enabled
});

/** Hook for fetching movie recommendations. */
export const useMovieRecommendations = (
    params?: MovieApiGetMovieRecommendationsRequest,
    enabled?: boolean
) => {
    const { api, user } = useApi();
    return useQuery(getMovieRecommendationsQuery(
        api,
        {
            ...params,
            userId: params?.userId || user?.Id
        },
        enabled
    ));
};
