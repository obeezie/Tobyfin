import { getDiscoveryLayout, currentLayoutKey } from 'components/discoverySettings/discoverySettings';
import * as userSettings from 'scripts/settings/userSettings';
import focusManager from 'components/focusManager';
import homeSections from 'components/homesections/homesections';
import { ServerConnections } from 'lib/jellyfin-apiclient';

import 'elements/emby-itemscontainer/emby-itemscontainer';

// Home is unmounted when a details page opens. Its rendered rows are parked here so returning
// shows the same cards and scroll positions instead of rebuilding and refetching every section.
const PARK_TTL_MS = 30 * 60 * 1000;
let parked = null;

// Detaching an itemsContainer clears these, so they are saved before parking and put back after.
const CONTAINER_STATE = ['fetchData', 'getItemsHtml', 'parentContainer', 'afterRefresh'];

function getSignature() {
    return currentLayoutKey() + JSON.stringify(getDiscoveryLayout());
}

export function parkSections(sectionsContainer, signature, scrollY) {
    parked = null;
    if (!sectionsContainer?.children.length
        || !sectionsContainer.classList.contains('homeSectionsContainer')
        // A home screen left before every section finished loading is rebuilt rather than
        // restored half-done (lower sections stop loading once Home is removed).
        || !sectionsContainer.homeSectionsComplete) return;
    const containers = Array.from(sectionsContainer.querySelectorAll('.itemsContainer')).map(elem => ({
        elem,
        state: Object.fromEntries(CONTAINER_STATE.map(key => [key, elem[key]]))
    }));
    // Parking runs after React has removed Home, when scrollLeft already reads 0, so use the
    // offsets tracked while Home was on screen.
    const scrollers = Array.from(sectionsContainer.querySelectorAll('.emby-scroller')).map(elem => ({
        elem,
        left: elem.homeScrollLeft ?? 0
    }));
    const holder = document.createElement('div');
    holder.append(...sectionsContainer.childNodes);
    parked = { holder, containers, scrollers, signature, scrollY, time: Date.now() };
}

export function restoreSections(sectionsContainer, signature) {
    const state = parked;
    parked = null;
    if (!state || state.signature !== signature || Date.now() - state.time > PARK_TTL_MS) return null;

    sectionsContainer.innerHTML = '';
    sectionsContainer.append(...state.holder.childNodes);
    sectionsContainer.classList.add('homeSectionsContainer');
    for (const { elem, state: saved } of state.containers) {
        Object.assign(elem, saved);
        // Parked rows miss playback/watched events, so rows that track them (Continue Watching,
        // Next Up) refresh on return; discovery rows keep their loaded cards.
        if (elem.getAttribute('data-monitor')) elem.needsRefresh = true;
    }
    return () => {
        for (const { elem, left } of state.scrollers) {
            elem.scroller?.reload();
            elem.scrollLeft = left;
        }
        window.scrollTo(0, state.scrollY);
    };
}

export function clearParkedSections() {
    parked = null;
}

class HomeTab {
    constructor(view, params) {
        this.view = view;
        this.params = params;
        this.apiClient = ServerConnections.currentApiClient();
        this.sectionsContainer = view.querySelector('.sections');
        this.scrollY = 0;
        this.onScroll = () => {
            if (!this.paused) this.scrollY = window.scrollY;
        };
        window.addEventListener('scroll', this.onScroll, { passive: true });
        // Row scroll events do not bubble, so record each row's offset in the capture phase.
        this.sectionsContainer.addEventListener('scroll', onRowScroll, { capture: true, passive: true });
        view.querySelector('.sections').addEventListener('settingschange', onHomeScreenSettingsChanged.bind(this));
    }
    onResume(options) {
        this.paused = false;
        const signature = getSignature();
        if (signature !== this.discoverySignature) {
            this.sectionsRendered = false;
            this.discoverySignature = signature;
        }

        if (!this.sectionsRendered) {
            const restoreScroll = restoreSections(this.sectionsContainer, signature);
            if (restoreScroll) {
                this.sectionsRendered = true;
                // Only rows already marked stale (for example after playback) refetch.
                return homeSections.resume(this.sectionsContainer, { ...options, refresh: false })
                    .then(() => requestAnimationFrame(restoreScroll));
            }
        }

        if (this.sectionsRendered) {
            const sectionsContainer = this.sectionsContainer;

            if (sectionsContainer) {
                return homeSections.resume(sectionsContainer, options);
            }

            return Promise.resolve();
        }

        const view = this.view;
        const apiClient = this.apiClient;
        this.destroyHomeSections();
        this.sectionsRendered = true;
        return apiClient.getCurrentUser()
            .then(user => homeSections.loadSections(view.querySelector('.sections'), apiClient, user, userSettings))
            .then(() => {
                if (options.autoFocus) {
                    focusManager.autoFocus(view);
                }
            }).catch(err => {
                console.error(err);
            });
    }
    onPause() {
        this.paused = true;
        const sectionsContainer = this.sectionsContainer;

        if (sectionsContainer) {
            homeSections.pause(sectionsContainer);
        }
    }
    park() {
        window.removeEventListener('scroll', this.onScroll);
        if (this.sectionsRendered) {
            parkSections(this.sectionsContainer, this.discoverySignature, this.scrollY);
        }
    }
    destroy() {
        window.removeEventListener('scroll', this.onScroll);
        this.view = null;
        this.params = null;
        this.apiClient = null;
        this.destroyHomeSections();
        this.sectionsContainer = null;
    }
    destroyHomeSections() {
        const sectionsContainer = this.sectionsContainer;

        if (sectionsContainer) {
            homeSections.destroySections(sectionsContainer);
        }
    }
}

function onRowScroll(e) {
    if (e.target.classList?.contains('emby-scroller')) {
        e.target.homeScrollLeft = e.target.scrollLeft;
    }
}

function onHomeScreenSettingsChanged() {
    this.sectionsRendered = false;
    clearParkedSections();

    if (!this.paused) {
        this.onResume({
            refresh: true
        });
    }
}

export default HomeTab;
