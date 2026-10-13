import ServerConnections from 'lib/jellyfin-apiclient/ServerConnections';
import * as userSettings from 'scripts/settings/userSettings';
import { DEFAULT_SLOTS, layoutKey, readLayout, writeLayout } from './discoveryLayout';
import './discoverySettings.scss';

export function currentLayoutKey() {
    const api = ServerConnections.currentApiClient();
    return layoutKey(api?.serverId(), api?.getCurrentUserId());
}
/** The account's old Home section slots, which decide where the former slot rows start out. */
export function homeSlots(settings = userSettings) {
    return DEFAULT_SLOTS.map((fallback, index) => settings.get('homesection' + index) || fallback);
}
export function getDiscoveryLayout() {
    return readLayout(localStorage, currentLayoutKey(), homeSlots());
}
export function saveDiscoveryLayout(value, key = currentLayoutKey()) {
    if (key !== currentLayoutKey()) throw new Error('The signed-in account changed. Reopen Customize Discovery.');
    writeLayout(localStorage, key, value);
}
export function applyDiscoverySize(element, layout = getDiscoveryLayout()) {
    element.dataset.discoverySize = layout.size;
}
