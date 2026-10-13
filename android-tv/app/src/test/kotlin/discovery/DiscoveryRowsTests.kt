package discovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.DiscoveryGenre
import org.jellyfin.androidtv.preference.DiscoveryLayout
import org.jellyfin.androidtv.ui.browsing.MovieDiscoveryRows

class DiscoveryRowsTests : FunSpec({
	val western = DiscoveryGenre("01234567-89ab-cdef-0123-456789abcdef", "Western")
	val serverAction = DiscoveryGenre("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Action")

	test("taking a row off moves it to the top of Add to Home, newest first") {
		val layout = DiscoveryLayout().takeOff("genre:Horror").takeOff("nextup")
		layout.onHome.contains("nextup") shouldBe false
		layout.addToHomeRows(emptyList()).take(2).map { it.id } shouldBe listOf("nextup", "genre:Horror")
	}

	test("putting a row back adds it to the end of On Home") {
		val layout = DiscoveryLayout().takeOff("resume").putOn("resume")
		layout.onHome.last() shouldBe "resume"
		layout.hidden shouldBe emptySet()
	}

	test("server genres can be added, and ones matching a built-in row are not offered twice") {
		val layout = DiscoveryLayout()
		layout.addToHomeRows(listOf(western, serverAction)).map { it.title } shouldBe listOf("Western")
		val added = layout.putOn(western)
		added.onHome.last() shouldBe western.id
		added.addToHomeRows(listOf(western)).map { it.id } shouldBe listOf("activerecordings")
	}

	test("a removed built-in genre is offered again and comes back at the end") {
		val layout = DiscoveryLayout().removeGenre("genre:Horror")
		layout.addToHomeRows(emptyList()).map { it.id }.contains("genre:Horror") shouldBe true
		layout.putOn("genre:Horror").onHome.last() shouldBe "genre:Horror"
	}

	test("moves a row among the rows On Home, ignoring rows that are off") {
		val layout = DiscoveryLayout().takeOff("nextup")
		val moved = layout.moveOnHome("genre:Horror", 1)
		moved.onHome.take(2) shouldBe listOf("resume", "genre:Horror")
		moved.hidden shouldBe setOf("nextup")
		layout.moveOnHome("nextup", 0) shouldBe layout
	}

	test("the order of rows taken off survives saving") {
		val layout = DiscoveryLayout().takeOff("genre:Drama").takeOff("genre:Comedy")
		DiscoveryLayout.decode(DiscoveryLayout.encode(layout)).addToHomeRows(emptyList()).take(2).map { it.id } shouldBe
			listOf("genre:Comedy", "genre:Drama")
	}

	test("filters Add to Home by search") {
		DiscoveryLayout().takeOff("genre:Comedy").addToHomeRows(listOf(western), "wes").map { it.title } shouldBe listOf("Western")
	}

	test("marks built-in genres the server has nothing for, once its genres are known") {
		val server = listOf("Western", "Documentary")
		MovieDiscoveryRows.hasNothingToShow("genre:Documentaries", server) shouldBe false
		MovieDiscoveryRows.hasNothingToShow("genre:Horror", server) shouldBe true
		MovieDiscoveryRows.hasNothingToShow("genre:Horror", emptyList()) shouldBe false
		MovieDiscoveryRows.hasNothingToShow("resume", server) shouldBe false
		MovieDiscoveryRows.hasNothingToShow(western.id, server) shouldBe false
	}

	test("labels only the built-in rows") {
		DiscoveryLayout.isBuiltIn("resume") shouldBe true
		DiscoveryLayout.isBuiltIn("genre:Horror") shouldBe false
		DiscoveryLayout.isBuiltIn(western.id) shouldBe false
	}
})
