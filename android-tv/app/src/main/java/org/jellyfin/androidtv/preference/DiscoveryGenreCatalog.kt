package org.jellyfin.androidtv.preference

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

/**
 * The server's movie genres, for Add to Home. They are asked for per movie library, as Jellyfin's own clients do:
 * Jellyfin 10.11 can answer a server-wide request with no genres. The server-wide request remains the fallback for
 * servers without a movie library.
 */
suspend fun loadDiscoveryGenres(api: ApiClient, userId: UUID): List<DiscoveryGenre> {
	val genres = linkedMapOf<String, DiscoveryGenre>()
	val movieLibraries = api.userViewsApi.getUserViews(userId = userId).content.items.orEmpty()
		.filter { it.collectionType == CollectionType.MOVIES }
		.map { it.id }
	for (library in movieLibraries) loadGenres(api, userId, library, genres)
	if (genres.isEmpty()) loadGenres(api, userId, null, genres)
	return genres.values.sortedBy { it.title.lowercase() }
}

private suspend fun loadGenres(api: ApiClient, userId: UUID, parentId: UUID?, genres: MutableMap<String, DiscoveryGenre>) {
	var start = 0
	while (true) {
		val result = api.genresApi.getGenres(
			userId = userId, parentId = parentId, includeItemTypes = setOf(BaseItemKind.MOVIE),
			sortBy = setOf(ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.ASCENDING),
			startIndex = start, limit = 200, enableImages = false, enableTotalRecordCount = true,
		).content
		val items = result.items.orEmpty()
		items.forEach { item ->
			val name = item.name
			if (!name.isNullOrBlank()) genres[item.id.toString()] = DiscoveryGenre(item.id.toString(), name)
		}
		start += items.size
		// A genre found in an earlier library is not new here, so stop on an empty page, not on no new genres.
		if (items.isEmpty() || start >= result.totalRecordCount) break
	}
}
