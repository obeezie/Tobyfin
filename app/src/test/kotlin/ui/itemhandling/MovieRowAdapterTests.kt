package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.Presenter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

class MovieRowAdapterTests : FunSpec({
	test("new movie batches retain existing card objects and background refreshes do not replace them") {
		for (type in listOf(QueryType.MovieDiscovery, QueryType.MovieLatest)) {
			val adapter = spyk(ItemRowAdapter(mockk<Context>(), GetItemsRequest(), 40,
				false, false, mockk<Presenter>(), null, type), recordPrivateCalls = true)
			// Android's Observable constructor is a JVM stub. Capture the insertion boundary
			// while exercising the real adapter storage, paging guards, and refresh logic.
			val inserted = mutableListOf<Pair<Int, Int>>()
			every { adapter["notifyItemRangeInserted"](any<Int>(), any<Int>()) } answers {
				inserted.add(firstArg<Int>() to secondArg<Int>())
				Unit
			}
			val first = (0 until 20).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong())) }
			adapter.appendMovieItems(first, true)
			// Initial selection should not eagerly fetch another batch before approaching the end.
			adapter.loadMoreItemsIfNeeded(0)
			val focusedCard = adapter[19]
			adapter.appendMovieItems((20 until 60).map { BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, it.toLong())) }, true)
			adapter.size() shouldBe 60
			inserted shouldBe (0 until 60).map { it to 1 }
			(adapter[19] === focusedCard) shouldBe true
			adapter.isSessionStable shouldBe true
			adapter.ReRetrieveIfNeeded() shouldBe false
			// A forced websocket refresh must return without accessing the API or clearing the row.
			adapter.Retrieve()
			adapter.size() shouldBe 60
			(adapter[19] === focusedCard) shouldBe true
		}
	}
})

