package discovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.DiscoveryLayout

class DiscoverySlotRowsTests : FunSpec({
	test("a new layout shows the rows the default Home section slots showed") {
		val layout = DiscoveryLayout().normalized()
		layout.hidden shouldBe setOf("activerecordings")
		layout.visible.takeLast(4) shouldBe listOf("mymedia", "resumeaudio", "livetv", "latestother")
	}

	test("an existing layout keeps Home the same: rows the old slots did not show start in Add to Home") {
		val saved = """{"order":["resume","nextup"],"hidden":[]}"""
		val layout = DiscoveryLayout.decode(saved, listOf("latestmedia", "resume", "livetv", "none"))
		layout.hidden shouldBe setOf("mymedia", "resumeaudio", "activerecordings")
		layout.visible shouldContain "livetv"
		layout.visible shouldContain "latestother"
	}

	test("the small library buttons slot counts as My Media") {
		DiscoveryLayout.decode("""{"order":[]}""", listOf("librarybuttons")).visible shouldContain "mymedia"
	}

	test("rows are placed once; the saved choice wins afterwards") {
		val first = DiscoveryLayout.decode("""{"order":[]}""", DiscoveryLayout.DEFAULT_SLOTS)
		val putBack = first.putOn("activerecordings")
		val reread = DiscoveryLayout.decode(DiscoveryLayout.encode(putBack), listOf("none"))
		reread.visible shouldContain "activerecordings"
		reread.hidden shouldNotContain "mymedia"
	}
})
