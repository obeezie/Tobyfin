package org.jellyfin.androidtv.preference

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

suspend fun loadDiscoveryGenres(api: ApiClient, userId: UUID): List<DiscoveryGenre> {
    val genres = linkedMapOf<String, DiscoveryGenre>()
    var start = 0
    while (true) {
        val result = api.genresApi.getGenres(
            userId = userId, includeItemTypes = setOf(BaseItemKind.MOVIE),
            sortBy = setOf(ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.ASCENDING),
            startIndex = start, limit = 200, enableImages = false, enableTotalRecordCount = true,
        ).content
        val items = result.items.orEmpty()
        val previous = genres.size
        items.forEach { item ->
            val name = item.name
            if (!name.isNullOrBlank()) genres[item.id.toString()] = DiscoveryGenre(item.id.toString(), name)
        }
        start += items.size
        if (items.isEmpty() || genres.size == previous || start >= result.totalRecordCount) break
    }
    return genres.values.sortedBy { it.title.lowercase() }
}