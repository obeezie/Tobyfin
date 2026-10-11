package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class HomeRowVisibilityTests : FunSpec({
    test("Home reveals rows top-down so a slow upper row never pushes visible rows down") {
        val source = mockk<ObjectAdapter>(relaxed = true)
        val first = mockk<ObjectAdapter>(relaxed = true)
        val second = mockk<ObjectAdapter>(relaxed = true)
        val third = mockk<ObjectAdapter>(relaxed = true)
        val sizes = mutableMapOf(first to 0, second to 0, third to 0)
        val pending = mutableSetOf(first, second, third)
        val observers = mutableMapOf<ObjectAdapter, ObjectAdapter.DataObserver>()
        val rows = listOf(ListRow(first), ListRow(second), ListRow(third))
        every { source.size() } returns rows.size
        every { source[any()] } answers { rows[firstArg<Int>()] }
        for (adapter in listOf(first, second, third)) {
            every { adapter.size() } answers { sizes.getValue(adapter) }
            every { adapter.registerObserver(any()) } answers { observers[adapter] = firstArg(); Unit }
        }
        var displayed = emptyList<Row>()
        val visibility = HomeRowVisibility(source, isPending = { it in pending }) { displayed = it }
        displayed shouldBe emptyList()

        // A lower row answers first; it waits for the rows above it.
        sizes[second] = 20
        pending.remove(second)
        observers.getValue(second).onItemRangeInserted(0, 20)
        displayed shouldBe emptyList()

        // The top row finishes empty (for example no Continue Watching): rows below it can now show.
        pending.remove(first)
        visibility.refresh()
        displayed shouldBe listOf(rows[1])

        sizes[third] = 20
        pending.remove(third)
        observers.getValue(third).onItemRangeInserted(0, 20)
        displayed shouldBe listOf(rows[1], rows[2])

        // After the first load, an emptied row still disappears as before.
        sizes[second] = 0
        observers.getValue(second).onChanged()
        displayed shouldBe listOf(rows[2])

        visibility.close()
        sizes[third] = 0
        observers.getValue(third).onChanged()
        displayed shouldBe listOf(rows[2])
    }
    test("stopWaiting shows loaded rows even if an earlier row never answers") {
        val source = mockk<ObjectAdapter>(relaxed = true)
        val stuck = mockk<ObjectAdapter>(relaxed = true)
        val loaded = mockk<ObjectAdapter>(relaxed = true)
        val rows = listOf(ListRow(stuck), ListRow(loaded))
        every { source.size() } returns rows.size
        every { source[any()] } answers { rows[firstArg<Int>()] }
        every { stuck.size() } returns 0
        every { loaded.size() } returns 20
        var displayed = emptyList<Row>()
        val visibility = HomeRowVisibility(source, isPending = { it == stuck }) { displayed = it }
        displayed shouldBe emptyList()
        visibility.stopWaiting()
        displayed shouldBe listOf(rows[1])
        visibility.close()
    }
    test("clearing a row reports its new empty state to visibility observers") {
        val items = spyk(MutableObjectAdapter<String>(), recordPrivateCalls = true)
        every { items["notifyItemRangeInserted"](any<Int>(), any<Int>()) } answers { Unit }
        items.add("poster")
        var countAtNotification = -1
        every { items["notifyItemRangeRemoved"](any<Int>(), any<Int>()) } answers {
            countAtNotification = items.size()
            Unit
        }
        items.clear()
        countAtNotification shouldBe 0
    }
    test("rows added after startup are observed and removed sources stop affecting the screen") {
        val source = mockk<ObjectAdapter>(relaxed = true)
        val child = mockk<ObjectAdapter>(relaxed = true)
        val row = ListRow(child)
        var rows = emptyList<Row>()
        var sourceObserver: ObjectAdapter.DataObserver? = null
        var childObserver: ObjectAdapter.DataObserver? = null
        var count = 0
        every { source.size() } answers { rows.size }
        every { source[any()] } answers { rows[firstArg<Int>()] }
        every { source.registerObserver(any()) } answers { sourceObserver = firstArg(); Unit }
        every { child.size() } answers { count }
        every { child.registerObserver(any()) } answers { childObserver = firstArg(); Unit }
        var shown = emptyList<Row>()
        val visibility = HomeRowVisibility(source) { shown = it }
        rows = listOf(row)
        sourceObserver!!.onItemRangeInserted(0, 1)
        shown shouldBe emptyList()
        count = 1
        childObserver!!.onChanged()
        shown shouldBe listOf(row)
        rows = emptyList()
        sourceObserver!!.onItemRangeRemoved(0, 1)
        shown shouldBe emptyList()
        visibility.close()
    }})