package org.jellyfin.androidtv.data.querying

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.Random
import java.util.UUID

/** One pager per displayed row. Background events never replace its current roster. */
class MovieRowPager(private val query: GetItemsRequest, private val randomized: Boolean) {
	private val mutex = Mutex()
	private val seen = mutableSetOf<UUID>()
	private var initial = true
	private var offset = 0
	private var plan: MovieBatchPlan? = null
	var hasMore = true
		private set

	suspend fun loadNext(fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult): List<BaseItemDto> = mutex.withLock {
		if (!hasMore) return@withLock emptyList()
		if (initial) {
			val response = fetch(query.copy(startIndex = 0, limit = 20, enableTotalRecordCount = true,
				sortBy = if (randomized) setOf(ItemSortBy.RANDOM) else query.sortBy))
			val items = distinct(response.items)
			offset = response.items.size
			if (randomized) plan = MovieBatchPlan(response.totalRecordCount, Random())
			hasMore = response.items.isNotEmpty() && offset < response.totalRecordCount
			initial = false
			return@withLock if (randomized) items.shuffled() else items
		}
		var items = emptyList<BaseItemDto>()
		while (hasMore && items.isEmpty()) {
			val batchPlan = plan
			if (randomized && batchPlan != null) {
				// Do not consume any slices until every request succeeds.
				val responses = coroutineScope {
					batchPlan.nextBatch().map { slice ->
						async { fetch(query.copy(startIndex = slice.startIndex, limit = slice.limit,
							enableTotalRecordCount = false, sortBy = setOf(ItemSortBy.SORT_NAME))) }
					}.awaitAll()
				}
				items = distinct(responses.flatMap { it.items }).shuffled()
				batchPlan.commitBatch()
				hasMore = batchPlan.hasMore()
			} else {
				val response = fetch(query.copy(startIndex = offset, limit = 40, enableTotalRecordCount = true))
				items = distinct(response.items)
				offset += response.items.size
				hasMore = response.items.isNotEmpty() && offset < response.totalRecordCount
			}
		}
		items
	}

	private fun distinct(items: List<BaseItemDto>) = items.filter { movie ->
		// Recently Added has no played filter; genre discovery retains unwatched browsing.
		(query.isPlayed != false || movie.userData?.played != true) && seen.add(movie.id)
	}
}
