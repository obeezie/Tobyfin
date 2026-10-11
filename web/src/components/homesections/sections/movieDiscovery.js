import escapeHtml from 'escape-html';

import cardBuilder from 'components/cardbuilder/cardBuilder';
import { getPortraitShape } from 'components/cardbuilder/utils/shape';
import globalize from 'lib/globalize';

import { loadMovieSuggestions } from './movieGenres';
import { attachInfiniteMovieRow } from './infiniteMovieRow';

const ROW_LIMIT = 16;

function renderRow(elem, title, items, options, loadMore) {
    if (!items.length) return;
    const row = document.createElement('div');
    row.className = 'verticalSection';
    row.innerHTML = '<h2 class="sectionTitle sectionTitle-cards padded-left">'
        + escapeHtml(title) + '</h2>'
        + '<div is="emby-scroller" class="padded-top-focusscale padded-bottom-focusscale" data-centerfocus="true">'
        + '<div is="emby-itemscontainer" class="itemsContainer scrollSlider focuscontainer-x"></div></div>';
    elem.appendChild(row);
    const container = row.querySelector('.itemsContainer');
    container.getItemsHtml = movies => cardBuilder.getCardsHtml({
        items: movies,
        shape: getPortraitShape(options.enableOverflow),
        context: 'home',
        showTitle: true,
        showYear: true,
        centerText: true,
        overlayPlayButton: true,
        showDetailsMenu: true,
        lines: 2
    });
    container.fetchData = () => Promise.resolve(items);
    container.parentContainer = row;
    if (loadMore) {
        container.afterRefresh = () => attachInfiniteMovieRow(container, loadMore, movies => {
            items.push(...movies);
            return container.getItemsHtml(movies);
        });
    }
}

export async function loadMovieDiscovery(elem, apiClient, user, userViews, options, genres) {
    elem.classList.remove('verticalSection');
    const userId = user.Id || apiClient.getCurrentUserId();
    const excluded = user.Configuration?.LatestItemsExcludes || [];
    const libraries = userViews.filter(view => view.CollectionType === 'movies'
        && view.Id && !excluded.includes(view.Id));

    for (const library of libraries) {
        if (!elem.isConnected) return;
        const rows = await loadMovieSuggestions(query => apiClient.getItems(userId, query), library.Id, ROW_LIMIT, genres);
        // Navigation can destroy the section while requests are still in flight.
        if (!elem.isConnected) return;
        rows.forEach(row => {
            renderRow(elem, globalize.translate('MovieDiscoveryFromLibrary', row.Title, library.Name),
                row.Items, options, row.LoadMore);
        });
    }
}
