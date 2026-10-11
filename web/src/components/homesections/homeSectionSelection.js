import { DEFAULT_SECTIONS, HomeSectionType } from 'constants/homeSectionType';
import { normalizeLayout, visibleSections } from '../discoverySettings/discoveryLayout';

export function selectHomeSections(userSettings, layout = normalizeLayout(null)) {
    const managed = [HomeSectionType.MovieDiscovery, HomeSectionType.LatestMedia, HomeSectionType.Resume, HomeSectionType.NextUp];
    const extras = DEFAULT_SECTIONS.map((fallback, index) => {
        const section = userSettings.get('homesection' + index) || fallback;
        return section === 'folders' ? DEFAULT_SECTIONS[0] : section;
    }).filter(section => section !== HomeSectionType.None && !managed.includes(section));
    const ordered = visibleSections(layout);
    return [...ordered, ...new Set(extras), 'latestother'];
}
