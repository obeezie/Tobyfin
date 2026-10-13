import { HomeSectionType } from 'constants/homeSectionType';
import { normalizeLayout, visibleSections } from '../discoverySettings/discoveryLayout';

/**
 * Home's rows, in order. Customize Discovery decides all of them; the former Home section slots are Discovery rows
 * now ("My Media" draws the library tiles).
 */
export function selectHomeSections(layout = normalizeLayout(null)) {
    return visibleSections(layout).map(id => (id === 'mymedia' ? HomeSectionType.SmallLibraryTiles : id));
}
