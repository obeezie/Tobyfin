package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.VerticalGridView
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HomeHeaderInsetTests : FunSpec({
    fun grid(offset: Int = 0, percent: Float = 50f) = mockk<VerticalGridView>(relaxed = true).also {
        every { it.paddingTop } returns 12
        every { it.paddingLeft } returns 8
        every { it.paddingRight } returns 8
        every { it.paddingBottom } returns 27
        every { it.windowAlignmentOffset } returns offset
        every { it.windowAlignmentOffsetPercent } returns percent
    }

    test("rows can draw through header space while first-row padding and focus position are preserved") {
        val grid = grid()
        HomeHeaderInset(grid).apply(96)
        verify { grid.clipToPadding = false }
        verify { grid.setPadding(8, 108, 8, 27) }
        verify { grid.windowAlignmentOffset = 48 }
        // Same selected-row screen coordinate before/after replacing the shorter viewport.
        (96 + (540 - 96) / 2) shouldBe (48 + 540 / 2)
    }

    test("remeasuring or recomposing the header does not accumulate extra padding") {
        val grid = grid()
        val inset = HomeHeaderInset(grid)
        inset.apply(96)
        inset.apply(96)
        inset.apply(120)
        verify(exactly = 1) { grid.setPadding(8, 108, 8, 27) }
        verify(exactly = 1) { grid.setPadding(8, 132, 8, 27) }
        verify { grid.windowAlignmentOffset = 60 }
    }

    test("fixed and end-relative alignment offsets retain their original screen positions") {
        val fixed = grid(offset = 40, percent = -1f)
        HomeHeaderInset(fixed).apply(96)
        verify { fixed.windowAlignmentOffset = 136 }
        val end = grid(offset = -40, percent = 50f)
        HomeHeaderInset(end).apply(96)
        verify { end.windowAlignmentOffset = -88 }
    }
})
