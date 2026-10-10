package org.jellyfin.androidtv.data.querying

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.yield
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.Random
import java.util.UUID

class MovieRowPagerTests : FunSpec({
    test("a seven-item initial Biography page continues when the server reports more matches") {
        val genreId = UUID(8, 9)
        val movies = (0 until 47).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong())) }
        val query = GetItemsRequest(parentId = UUID(1, 2), genreIds = setOf(genreId), isPlayed = false)
        val pager = MovieRowPager(query, true)
        var first = true
        val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = { request ->
            request.genreIds shouldBe setOf(genreId)
            request.parentId shouldBe query.parentId
            request.isPlayed shouldBe false
            val items = if (first) {
                first = false
                movies.take(7)
            } else movies.drop(request.startIndex ?: 0).take(request.limit ?: 20)
            BaseItemDtoQueryResult(items = items, totalRecordCount = movies.size, startIndex = 0)
        }
        val displayed = pager.loadNext(fetch).toMutableList()
        displayed shouldHaveSize 7
        pager.hasMore shouldBe true
        var batches = 0
        while (pager.hasMore && batches++ < 20) displayed.addAll(pager.loadNext(fetch))
        displayed.map { it.id }.toSet() shouldBe movies.map { it.id }.toSet()
        displayed shouldHaveSize 47
        pager.hasMore shouldBe false
    }

	test("mixed batches cover four alphabet ranges and every matching movie exactly once") {
		val movies = (0 until 520).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong()), name = ('A' + it / 130).toString() + it) }
		val pager = MovieRowPager(GetItemsRequest(parentId = UUID(1, 1), isPlayed = false), true)
		val requests = mutableListOf<GetItemsRequest>()
		val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = { query ->
			requests.add(query)
			val items = if (query.sortBy == setOf(ItemSortBy.RANDOM)) {
				(0 until 20).map { movies[it * 26] }
			} else {
				movies.drop(query.startIndex ?: 0).take(query.limit ?: 20)
			}
			BaseItemDtoQueryResult(items = items, totalRecordCount = movies.size, startIndex = 0)
		}
		val displayed = pager.loadNext(fetch).toMutableList()
		displayed shouldHaveSize 20
		val firstBatch = pager.loadNext(fetch)
		firstBatch.map { it.name!!.first() }.toSet().size.let { (it >= 4) shouldBe true }
		displayed.addAll(firstBatch)
		while (pager.hasMore) displayed.addAll(pager.loadNext(fetch))
		displayed shouldHaveSize 520
		displayed.map { it.id }.toSet().size shouldBe 520
		requests.first().sortBy shouldBe setOf(ItemSortBy.RANDOM)
		requests.drop(1).all { it.sortBy == setOf(ItemSortBy.SORT_NAME) && it.limit!! <= 10 } shouldBe true
		requests.all { it.parentId == UUID(1, 1) && it.isPlayed == false } shouldBe true
	}

	test("failed mixed requests retry the same slices without consuming or duplicating movies") {
		val movies = (0 until 105).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong())) }
		val pager = MovieRowPager(GetItemsRequest(isPlayed = false), true)
		var fail = false
		val requested = mutableListOf<Int>()
		val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = { query ->
			if (query.enableTotalRecordCount == false) {
				requested.add(query.startIndex!!)
				if (fail) {
					yield()
					throw IllegalStateException("offline")
				}
			}
			BaseItemDtoQueryResult(items = movies.drop(query.startIndex ?: 0).take(query.limit ?: 20), totalRecordCount = movies.size, startIndex = 0)
		}
		val displayed = pager.loadNext(fetch).toMutableList()
		fail = true
		shouldThrow<IllegalStateException> { pager.loadNext(fetch) }
		val failedOffsets = requested.toSet()
		requested.clear()
		fail = false
		displayed.addAll(pager.loadNext(fetch))
		requested.toSet() shouldBe failedOffsets
		while (pager.hasMore) displayed.addAll(pager.loadNext(fetch))
		displayed.map { it.id }.toSet().size shouldBe 105
		displayed shouldHaveSize 105
	}

	test("Recently Added pages beyond twenty in order and includes watched movies") {
		val movies = (0 until 93).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong()), userData = UserItemDataDto(playbackPositionTicks = 0, playCount = 1, isFavorite = false, played = it % 2 == 0, key = it.toString(), itemId = UUID(0, it.toLong()))) }
		val query = GetItemsRequest(sortBy = linkedSetOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME))
		val pager = MovieRowPager(query, false)
		val offsets = mutableListOf<Int>()
		val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = { request ->
			request.isPlayed shouldBe null
			request.sortBy shouldBe query.sortBy
			offsets.add(request.startIndex!!)
			BaseItemDtoQueryResult(items = movies.drop(request.startIndex!!).take(request.limit!!), totalRecordCount = movies.size, startIndex = 0)
		}
		val displayed = mutableListOf<BaseItemDto>()
		while (pager.hasMore) displayed.addAll(pager.loadNext(fetch))
		displayed.map { it.id } shouldBe movies.map { it.id }
		displayed.count { it.userData?.played == true } shouldBe 47
		offsets shouldBe listOf(0, 20, 60)
	}

	test("small and empty libraries exhaust without extra requests") {
		for (total in listOf(0, 1, 7, 20)) {
			val pager = MovieRowPager(GetItemsRequest(), true)
			var calls = 0
			val fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult = {
				calls++
				BaseItemDtoQueryResult(items = (0 until total).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong())) }, totalRecordCount = total, startIndex = 0)
			}
			pager.loadNext(fetch) shouldHaveSize total
			pager.hasMore shouldBe false
			pager.loadNext(fetch) shouldHaveSize 0
			calls shouldBe 1
		}
	}

	test("slice reads are repeatable until committed and the plan fully covers boundary sizes") {
		for (total in listOf(0, 1, 9, 10, 11, 39, 40, 41, 105, 520)) {
			val plan = MovieBatchPlan(total, Random(123))
			val covered = mutableListOf<Int>()
			while (plan.hasMore()) {
				val batch = plan.nextBatch()
				plan.nextBatch() shouldBe batch
				batch.forEach { covered.addAll(it.startIndex until it.startIndex + it.limit) }
				plan.commitBatch()
			}
			covered.sorted() shouldBe (0 until total).toList()
		}
	}
})

