import type { BaseItemDto } from '@jellyfin/sdk/lib/generated-client/models/base-item-dto';
import type { BaseItemDtoQueryResult } from '@jellyfin/sdk/lib/generated-client/models/base-item-dto-query-result';

export interface SectionOptions {
    enableOverflow: boolean
}

export type SectionContainerElement = {
    fetchData: () => Promise<BaseItemDtoQueryResult | BaseItemDto[]>
    getItemsHtml: (items: BaseItemDto[]) => string
    parentContainer: HTMLElement
    afterRefresh?: () => void
    cleanupPaging?: () => void
    refreshItems?: () => Promise<void>
} & Element;
