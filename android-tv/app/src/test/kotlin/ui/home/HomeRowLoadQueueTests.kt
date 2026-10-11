package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HomeRowLoadQueueTests : FunSpec({
    test("rows start in screen order with at most the limit loading at once") {
        val started = mutableListOf<String>()
        val finish = mutableMapOf<String, () -> Unit>()
        val queue = HomeRowLoadQueue<String>(limit = 2) { row, done ->
            started += row
            finish[row] = done
        }
        listOf("resume", "next up", "recently added", "action", "comedy").forEach(queue::enqueue)
        started shouldBe listOf("resume", "next up")

        finish.getValue("next up")()
        started shouldBe listOf("resume", "next up", "recently added")

        // A row reporting completion twice must not free two slots.
        finish.getValue("next up")()
        started shouldBe listOf("resume", "next up", "recently added")

        finish.getValue("resume")()
        finish.getValue("recently added")()
        started shouldBe listOf("resume", "next up", "recently added", "action", "comedy")
    }
    test("drain starts every waiting row when an earlier row never answers") {
        val started = mutableListOf<String>()
        val queue = HomeRowLoadQueue<String>(limit = 1) { row, _ -> started += row }
        listOf("stuck", "action", "comedy").forEach(queue::enqueue)
        started shouldBe listOf("stuck")
        queue.drain()
        started shouldBe listOf("stuck", "action", "comedy")
    }
})
