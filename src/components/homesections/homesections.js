import { getDiscoveryLayout, applyDiscoverySize } from '../discoverySettings/discoverySettings';
import { orderedGenres, genreId } from '../discoverySettings/discoveryLayout';
import layoutManager from 'components/layoutManager';
import loading from 'components/loading/loading';
import { DEFAULT_SECTIONS, HomeSectionType } from 'constants/homeSectionType';
import { getUserViewsQuery } from 'hooks/api/useUserViews';
import globalize from 'lib/globalize';
import ServerConnections from 'lib/jellyfin-apiclient/ServerConnections';
import Dashboard from 'utils/dashboard';
import { queryClient } from 'utils/query/queryClient';

import { loadRecordings } from './sections/activeRecordings';
import { loadLibraryButtons } from './sections/libraryButtons';
import { loadLibraryTiles } from './sections/libraryTiles';
import { loadLiveTV } from './sections/liveTv';
import { loadNextUp } from './sections/nextUp';
import { loadRecentlyAdded } from './sections/recentlyAdded';
import { loadResume } from './sections/resume';
import { loadMovieDiscovery } from './sections/movieDiscovery';
import { selectHomeSections } from './homeSectionSelection';

import 'elements/emby-button/paper-icon-button-light';
import 'elements/emby-itemscontainer/emby-itemscontainer';
import 'elements/emby-scroller/emby-scroller';
import 'elements/emby-button/emby-button';

import './homesections.scss';

const SECTION_LOAD_CONCURRENCY = 3;

/** Runs tasks in the order they are queued, with at most `limit` running at once. */
function createSlotQueue(limit) {
    let running = 0;
    const waiting = [];
    const startNext = () => {
        while (running < limit && waiting.length) {
            const { task, resolve, reject } = waiting.shift();
            running++;
            Promise.resolve().then(task).then(resolve, reject).finally(() => {
                running--;
                startNext();
            });
        }
    };
    return task => new Promise((resolve, reject) => {
        waiting.push({ task, resolve, reject });
        startNext();
    });
}

export function getDefaultSection(index) {
    if (index < 0 || index > DEFAULT_SECTIONS.length) return '';
    return DEFAULT_SECTIONS[index];
}

function getAllSectionsToShow(userSettings) {
    const sections = selectHomeSections(userSettings, getDiscoveryLayout());

    // Ensure libraries are visible in TV layout
    if (
        layoutManager.tv
            && !sections.includes(HomeSectionType.SmallLibraryTiles)
            && !sections.includes(HomeSectionType.LibraryButtons)
    ) {
        return [
            HomeSectionType.SmallLibraryTiles,
            ...sections
        ];
    }

    return sections;
}

export function loadSections(elem, apiClient, user, userSettings) {
    const api = ServerConnections.getApi(apiClient.serverId());
    const userId = user.Id || apiClient.getCurrentUserId();
    return queryClient
        .fetchQuery(getUserViewsQuery(api, { userId }))
        .then(result => result.Items || [])
        .then(function (userViews) {
            let html = '';

            if (userViews.length) {
                // TV layout can have an extra section to ensure libraries are visible
                const sectionsToShow = getAllSectionsToShow(userSettings);
                const totalSectionCount = sectionsToShow.length;
                for (let i = 0; i < totalSectionCount; i++) {
                    html += '<div class="verticalSection section' + i + '"></div>';
                }

                elem.innerHTML = html;
                elem.classList.add('homeSectionsContainer');
                // Sections finish in network order, not layout order. Keep Home hidden until the
                // sections filling the first screen have their cards; anything still loading is
                // below them, so nothing visible gets pushed down.
                elem.classList.add('homeSectionsLoading');
                loading.show();
                const loadToken = {};
                elem.homeSectionsLoadToken = loadToken;
                elem.homeSectionsComplete = false;
                const sectionElems = sectionsToShow.map((_, index) => elem.querySelector('.section' + index));
                const ready = sectionsToShow.map(() => false);

                const reveal = () => {
                    // A newer load owns the container if the home screen was reloaded meanwhile.
                    if (elem.homeSectionsLoadToken !== loadToken || !elem.classList.contains('homeSectionsLoading')) return;
                    elem.classList.remove('homeSectionsLoading');
                    loading.hide();
                };
                const revealIfFirstScreenReady = () => {
                    const firstScreen = window.innerHeight - Math.max(0, elem.getBoundingClientRect().top);
                    let filled = 0;
                    for (let i = 0; i < ready.length; i++) {
                        if (!ready[i]) return;
                        filled += sectionElems[i]?.offsetHeight || 0;
                        if (filled >= firstScreen) break;
                    }
                    reveal();
                };

                // Load sections in screen order a few at a time. Starting every section at once
                // let below-the-fold genre rows take the WebView's few server connections before
                // the first screen's rows had even sent their requests.
                const nextSlot = createSlotQueue(SECTION_LOAD_CONCURRENCY);
                const promises = sectionsToShow
                    .map((section, index) => nextSlot(() => (
                        loadSection(elem, apiClient, user, userSettings, userViews, section, index)
                            // Timeout for polyfilled CustomElements (webOS 1.2)
                            .then(() => new Promise((resolve) => setTimeout(resolve, 0)))
                            .then(() => resume(sectionElems[index], { refresh: true }))
                            .finally(() => {
                                ready[index] = true;
                                revealIfFirstScreenReady();
                            })
                    )));

                return Promise.all(promises).finally(() => {
                    reveal();
                    if (elem.homeSectionsLoadToken === loadToken) elem.homeSectionsComplete = true;
                });
            } else {
                let noLibDescription;
                if (user.Policy?.IsAdministrator) {
                    noLibDescription = globalize.translate('NoCreatedLibraries', '<br><a id="button-createLibrary" class="button-link">', '</a>');
                } else {
                    noLibDescription = globalize.translate('AskAdminToCreateLibrary');
                }

                html += '<div class="centerMessage padded-left padded-right">';
                html += '<h2>' + globalize.translate('MessageNothingHere') + '</h2>';
                html += '<p>' + noLibDescription + '</p>';
                html += '</div>';
                elem.innerHTML = html;

                const createNowLink = elem.querySelector('#button-createLibrary');
                if (createNowLink) {
                    createNowLink.addEventListener('click', function () {
                        Dashboard.navigate('dashboard/libraries');
                    });
                }
            }
        });
}

export function destroySections(elem) {
    const elems = elem.querySelectorAll('.itemsContainer');
    for (const e of elems) {
        e.cleanupPaging?.();
        e.afterRefresh = null;
        e.fetchData = null;
        e.parentContainer = null;
        e.getItemsHtml = null;
    }

    elem.innerHTML = '';
}

export function pause(elem) {
    const elems = elem.querySelectorAll('.itemsContainer');
    for (const e of elems) {
        e.pause();
    }
}

export function resume(elem, options) {
    const elems = elem.querySelectorAll('.itemsContainer');
    const promises = [];

    Array.prototype.forEach.call(elems, section => {
        if (section.resume) {
            promises.push(section.resume(options));
        }
    });

    return Promise.all(promises);
}

function loadSection(page, apiClient, user, userSettings, userViews, section, index) {
    const elem = page.querySelector('.section' + index);
    const options = { enableOverflow: enableScrollX() };

    if (section.startsWith('genre:')) {
        applyDiscoverySize(elem);
        const genre = orderedGenres(getDiscoveryLayout()).find(value => genreId(value) === section);
        return loadMovieDiscovery(elem, apiClient, user, userViews, options, genre ? [genre] : []);
    }
    if (['resume', 'nextup', 'latestmovies', 'latesttv'].includes(section)) applyDiscoverySize(elem);
    switch (section) {
        case 'latestmovies':
        case 'latesttv':
        case 'latestother':
            loadRecentlyAdded(elem, apiClient, user, userViews.filter(view => {
                if (section === 'latestmovies') return view.CollectionType === 'movies';
                if (section === 'latesttv') return view.CollectionType === 'tvshows';
                return !['movies', 'tvshows'].includes(view.CollectionType);
            }), options);
            break;
        case HomeSectionType.MovieDiscovery:
            return loadMovieDiscovery(elem, apiClient, user, userViews, options);
        case HomeSectionType.ActiveRecordings:
            loadRecordings(elem, true, apiClient, options);
            break;
        case HomeSectionType.LatestMedia:
            loadRecentlyAdded(elem, apiClient, user, userViews, options);
            break;
        case HomeSectionType.LibraryButtons:
            loadLibraryButtons(elem, userViews);
            break;
        case HomeSectionType.LiveTv:
            return loadLiveTV(elem, apiClient, user, options);
        case HomeSectionType.NextUp:
            loadNextUp(elem, apiClient, userSettings, options);
            break;
        case HomeSectionType.Resume:
            loadResume(elem, apiClient, 'HeaderContinueWatching', 'Video', userSettings, options);
            break;
        case HomeSectionType.ResumeAudio:
            loadResume(elem, apiClient, 'HeaderContinueListening', 'Audio', userSettings, options);
            break;
        case HomeSectionType.ResumeBook:
            loadResume(elem, apiClient, 'HeaderContinueReading', 'Book', userSettings, options);
            break;
        case HomeSectionType.SmallLibraryTiles:
            loadLibraryTiles(elem, userViews, options);
            break;
        default:
            elem.innerHTML = '';
    }

    return Promise.resolve();
}

function enableScrollX() {
    return true;
}

export default {
    getDefaultSection,
    loadSections,
    destroySections,
    pause,
    resume
};
