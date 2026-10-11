package org.jellyfin.androidtv.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

class ByGenreFragment : BrowseFolderFragment() {
	private val apiClient by inject<ApiClient>()

	override suspend fun setupQueries(rowLoader: RowLoader) {
		val movieLibrary = folder?.takeIf {
			it.collectionType == CollectionType.MOVIES || includeType?.let(BaseItemKind::fromNameOrNull) == BaseItemKind.MOVIE
		}
		if (movieLibrary != null) {
			rows.clear()
			rows.addAll(MovieDiscoveryRows.genreRows(requireContext(), movieLibrary.id))
			rowLoader.loadRows(rows)
			return
		}
		val childCount = folder?.childCount ?: 0
		if (childCount <= 0) return

		// Get all genres for this folder
		val genresResponse = withContext(Dispatchers.IO) {
			apiClient.genresApi.getGenres(
				parentId = folder?.id,
				sortBy = setOf(ItemSortBy.SORT_NAME),
			).content
		}

		for (genre in genresResponse.items) {
			val itemsRequest = GetItemsRequest(
				parentId = folder?.id,
				sortBy = setOf(ItemSortBy.SORT_NAME),
				includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
				genres = setOf(genre.name.orEmpty()),
				recursive = true,
				fields = ItemRepository.itemFields,
			)
			rows.add(BrowseRowDef(genre.name, itemsRequest, 40))
		}

		rowLoader.loadRows(rows)
	}
}
