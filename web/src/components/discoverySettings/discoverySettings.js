import ServerConnections from 'lib/jellyfin-apiclient/ServerConnections';
import { layoutKey, readLayout, writeLayout } from './discoveryLayout';
import './discoverySettings.scss';

export function currentLayoutKey() {
    const api = ServerConnections.currentApiClient();
    return layoutKey(api?.serverId(), api?.getCurrentUserId());
}
export function getDiscoveryLayout() {
    return readLayout(localStorage, currentLayoutKey());
}
export function saveDiscoveryLayout(value, key = currentLayoutKey()) {
    if (key !== currentLayoutKey()) throw new Error('The signed-in account changed. Reopen Customize Discovery.');
    writeLayout(localStorage, key, value);
}
export function applyDiscoverySize(element, layout = getDiscoveryLayout()) {
    element.dataset.discoverySize = layout.size;
}
