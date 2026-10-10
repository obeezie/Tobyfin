package discovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.DiscoveryLayout
import org.jellyfin.androidtv.preference.DiscoveryMove

class DiscoveryMoveTests : FunSpec({
    test("pickup previews a drop position without changing the saved draft") {
        val draft = DiscoveryLayout()
        val move = DiscoveryMove("genre:Horror", draft.order).step(-2)
        move.order.indexOf("genre:Horror") shouldBe 4
        draft.order.indexOf("genre:Horror") shouldBe 6
        val dropped = draft.copy(order = move.order)
        dropped.order[4] shouldBe "genre:Horror"
        dropped.order.distinct().size shouldBe draft.order.size
        move.step(2).order shouldBe draft.order
    }
    test("repeated remote movement stops at both ends and retains all sections") {
        val draft = DiscoveryLayout()
        val move = DiscoveryMove("resume", draft.order)
        move.step(-1).position shouldBe 0
        move.step(100).position shouldBe draft.order.lastIndex
        move.step(100).step(1).order shouldBe move.step(100).order
        move.step(100).order.toSet() shouldBe draft.order.toSet()
    }
})