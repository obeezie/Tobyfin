package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class HomeRowPositionsTests : FunSpec({
    test("each row returns to the card that was selected in it") {
        val movies = mockk<ObjectAdapter>(relaxed = true)
        val shows = mockk<ObjectAdapter>(relaxed = true)
        every { movies.size() } returns 40
        every { shows.size() } returns 40
        val positions = HomeRowPositions()
        positions.record(ListRow(movies), 17)
        positions.record(ListRow(shows), 3)
        positions.positionFor(ListRow(movies)) shouldBe 17
        positions.positionFor(ListRow(shows)) shouldBe 3
    }
    test("a row that was never browsed or has since shrunk starts from its first card") {
        val resume = mockk<ObjectAdapter>(relaxed = true)
        var size = 10
        every { resume.size() } answers { size }
        val positions = HomeRowPositions()
        positions.positionFor(ListRow(resume)) shouldBe null
        positions.record(ListRow(resume), 6)
        size = 2 // Continue Watching refreshed after playback.
        positions.positionFor(ListRow(resume)) shouldBe null
        positions.positionFor("not a row") shouldBe null
    }
})
