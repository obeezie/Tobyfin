package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

class MovieDiscoveryRowsTests : FunSpec({
    test("recent TV uses the incremental pager with unique series in latest-episode-added order") {
        val row = MovieDiscoveryRows.latestSeriesRow("Latest TV", UUID(1, 2))
        row.queryType shouldBe QueryType.MovieLatest
        row.query.includeItemTypes shouldBe setOf(org.jellyfin.sdk.model.api.BaseItemKind.SERIES)
        row.query.sortBy shouldBe linkedSetOf(ItemSortBy.DATE_LAST_CONTENT_ADDED, ItemSortBy.SORT_NAME)
        row.query.sortOrder shouldBe setOf(SortOrder.DESCENDING)
        row.query.parentId shouldBe UUID(1, 2)
        row.query.isPlayed shouldBe null
        row.query.enableTotalRecordCount shouldBe true
        row.chunkSize shouldBe 40
    }
    test("saved genre order and hiding apply to every library without crossing library boundaries") {
        val context = mockk<Context>()
        every { context.getString(any()) } answers { firstArg<Int>().toString() }
        val layout = org.jellyfin.androidtv.preference.DiscoveryLayout(
            order = listOf("genre:Horror", "resume", "genre:Comedy"), hidden = setOf("genre:Action")).normalized()
        for (id in listOf(UUID(1, 2), UUID(3, 4))) {
            val rows = MovieDiscoveryRows.genreRows(context, id, layout)
            rows.size shouldBe 12
            rows.first().query.genres shouldBe setOf("Horror")
            rows[1].query.genres shouldBe setOf("Comedy")
            rows.any { "Action" in it.query.genres.orEmpty() } shouldBe false
            rows.all { it.query.parentId == id } shouldBe true
        }
    }
	test("all thirteen genre rows use library-scoped filters and do not consult history or favorites") {
		val context = mockk<Context>()
		every { context.getString(any()) } answers { firstArg<Int>().toString() }
		val libraryId = UUID(1, 2)
		val rows = MovieDiscoveryRows.genreRows(context, libraryId, org.jellyfin.androidtv.preference.DiscoveryLayout())
		rows.size shouldBe 13
		rows.all { it.queryType == QueryType.MovieDiscovery && it.chunkSize == 40 } shouldBe true
		rows.all { it.query.parentId == libraryId && it.query.isPlayed == false && it.query.isFavorite == null } shouldBe true
		rows.map { it.query.genres }.contains(setOf("Crime", "Mystery")) shouldBe true
		rows.map { it.query.genres }.contains(setOf("Science Fiction", "Sci-Fi")) shouldBe true
		rows.map { it.query.genres }.contains(setOf("Documentary", "Documentaries")) shouldBe true
	}

	test("Recently Added uses newest-first item paging without watched exclusions") {
		val row = MovieDiscoveryRows.latestRow("Latest", UUID(1, 2))
		row.queryType shouldBe QueryType.MovieLatest
		row.query.isPlayed shouldBe null
		row.query.sortBy shouldBe linkedSetOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME)
		row.query.sortOrder shouldBe setOf(SortOrder.DESCENDING)
		row.query.enableTotalRecordCount shouldBe true
	}
    test("custom genres use exact IDs with the same library scope and infinite pager") {
        val genre = org.jellyfin.androidtv.preference.DiscoveryGenre(UUID(5, 6).toString(), "Western | Classics")
        val layout = org.jellyfin.androidtv.preference.DiscoveryLayout().addGenre(genre)
        val rows = MovieDiscoveryRows.genreRows(mockk<Context>(), UUID(1, 2), layout, onlyId = genre.id)
        rows.size shouldBe 1
        rows.single().headerText shouldBe genre.title
        rows.single().query.genreIds shouldBe setOf(UUID(5, 6))
        rows.single().query.genres shouldBe null
        rows.single().query.parentId shouldBe UUID(1, 2)
        rows.single().query.isPlayed shouldBe false
        rows.single().queryType shouldBe QueryType.MovieDiscovery
        MovieDiscoveryRows.genreRows(mockk<Context>(), UUID(1, 2), layout.copy(hidden = setOf(genre.id)), onlyId = genre.id).size shouldBe 0
    }})
