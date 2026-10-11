import { getDiscoveryLayout } from 'components/discoverySettings/discoverySettings';
import { ItemFields } from '@jellyfin/sdk/lib/generated-client/models/item-fields';
import React, { type FC } from 'react';

import { CardShape } from 'components/cardbuilder/utils/shape';
import { useApi } from 'hooks/useApi';
import { useGetSuggestionSectionsWithItems } from 'hooks/useFetchItems';
import { appRouter } from 'components/router/appRouter';
import globalize from 'lib/globalize';
import Loading from 'components/loading/LoadingComponent';
import NoItemsMessage from 'components/common/NoItemsMessage';
import SectionContainer from 'components/common/SectionContainer';
import InfiniteMovieSuggestions from 'components/common/InfiniteMovieSuggestions';
import InfiniteRecentlyAddedMovies from 'components/common/InfiniteRecentlyAddedMovies';
import type { ParentId } from 'types/library';
import { SectionType, type Section } from 'types/sections';
import type { ItemDto } from 'types/base/models/item-dto';

import { useMovieRecommendations } from '../hooks/api/useMovieRecommendations';

interface SuggestionsSectionViewProps {
    parentId: ParentId;
    sectionType: SectionType[];
    isMovieRecommendationEnabled: boolean | undefined;
}

const SuggestionsSectionView: FC<SuggestionsSectionViewProps> = ({
    parentId,
    sectionType,
    isMovieRecommendationEnabled = false
}) => {
    const { __legacyApiClient__ } = useApi();
    const { isLoading, data: sectionsWithItems } =
        useGetSuggestionSectionsWithItems(parentId, sectionType);

    const {
        isLoading: isRecommendationsLoading,
        data: movieRecommendationsItems,
        dataUpdatedAt: recommendationVersion
    } = useMovieRecommendations({
        parentId: parentId || undefined,
        fields: [
            ItemFields.PrimaryImageAspectRatio,
            ItemFields.MediaSourceCount
        ],
        categoryLimit: 6,
        itemLimit: 20
    }, isMovieRecommendationEnabled);

    if (isLoading || isRecommendationsLoading) {
        return <Loading />;
    }

    if (!sectionsWithItems?.length && !movieRecommendationsItems?.length) {
        return <NoItemsMessage />;
    }

    const getRouteUrl = (section: Section) => {
        return appRouter.getRouteUrl('list', {
            serverId: window.ApiClient.serverId(),
            itemTypes: section.itemTypes,
            parentId: parentId
        });
    };

    return (
        <>
            {sectionsWithItems?.map(({ section, items }) => [SectionType.LatestMovies, SectionType.LatestEpisode].includes(section.type) ? (
                <InfiniteRecentlyAddedMovies
                    key={section.type}
                    title={globalize.translate(section.name)}
                    headerUrl={getRouteUrl(section)}
                    parentId={parentId}
                    isSeries={section.type === SectionType.LatestEpisode}
                    initialItems={items}
                    cardOptions={{ ...section.cardOptions, queryKey: ['SuggestionSectionWithItems'],
                        showTitle: true, centerText: true, cardLayout: false, overlayText: false,
                        serverId: __legacyApiClient__?.serverId() }}
                />
            ) : (
                <SectionContainer
                    key={section.type}
                    sectionHeaderProps={{
                        title: globalize.translate(section.name),
                        url: getRouteUrl(section)
                    }}
                    itemsContainerProps={{
                        queryKey: ['SuggestionSectionWithItems']
                    }}
                    items={items}
                    cardOptions={{
                        ...section.cardOptions,
                        queryKey: ['SuggestionSectionWithItems'],
                        showTitle: true,
                        centerText: true,
                        cardLayout: false,
                        overlayText: false,
                        serverId: __legacyApiClient__?.serverId()
                    }}
                />
            ))}

            <div data-discovery-size={getDiscoveryLayout().size}>
                {movieRecommendationsItems?.map((recommendation, index) => (
                    <InfiniteMovieSuggestions
                    // eslint-disable-next-line react/no-array-index-key
                        key={`${recommendation.CategoryId}-${index}-${recommendationVersion}`}
                        title={recommendation.Title}
                        initialItems={recommendation.Items as ItemDto[]}
                        loadMore={recommendation.LoadMore}
                        cardOptions={{
                            queryKey: ['MovieRecommendations'],
                            shape: CardShape.PortraitOverflow,
                            showYear: true,
                            scalable: true,
                            overlayPlayButton: true,
                            showTitle: true,
                            centerText: true,
                            cardLayout: false,
                            serverId: __legacyApiClient__?.serverId()
                        }}
                    />
                ))}
            </div>
        </>
    );
};

export default SuggestionsSectionView;
