package discovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.DiscoveryGenre
import org.jellyfin.androidtv.preference.DiscoveryLayout

class DiscoveryLayoutTests : FunSpec({
    test("removed defaults stay removed after storage and can be restored individually") {
        val removed = DiscoveryLayout.decode(DiscoveryLayout.encode(DiscoveryLayout().removeGenre("genre:Action")))
        removed.order.contains("genre:Action") shouldBe false
        removed.visible.contains("genre:Action") shouldBe false
        removed.restoreGenre("genre:Action").order.count { it == "genre:Action" } shouldBe 1
        removed.addGenre(DiscoveryGenre("01234567-89ab-cdef-0123-456789abcdef", "Action")).customGenres shouldBe emptyList()
        removed.removeGenre("resume") shouldBe removed
    }
    test("defaults start with resume next up latest movies and latest TV") {
        DiscoveryLayout().visible.take(4) shouldBe listOf("resume", "nextup", "latestmovies", "latesttv")
    }
    test("persisted order supports interleaving and hidden sections without duplicate insertion") {
        val layout = DiscoveryLayout(order = listOf("genre:Horror", "latesttv", "resume"),
            hidden = setOf("nextup", "latestmovies")).normalized()
        val restored = DiscoveryLayout.decode(DiscoveryLayout.encode(layout))
        restored.visible.take(3) shouldBe listOf("genre:Horror", "latesttv", "resume")
        restored.visible.contains("nextup") shouldBe false
        restored.order.size shouldBe 17
    }
    test("defaults are a new draft and cannot mutate saved data") {
        val saved = DiscoveryLayout.encode(DiscoveryLayout().move("genre:Horror", -6))
        val draft = DiscoveryLayout()
        draft.order.first() shouldBe "resume"
        DiscoveryLayout.decode(saved).order.first() shouldBe "genre:Horror"
    }
    test("invalid and old preferences are repaired while preserving known choices") {
        val decoded = DiscoveryLayout.decode("""{"order":["genre:Horror","removed","genre:Horror"],"hidden":["resume","removed"],"size":"huge"}""")
        decoded.order.first() shouldBe "genre:Horror"
        decoded.order.size shouldBe 17
        decoded.hidden shouldBe setOf("resume")
        decoded.size shouldBe "medium"
        DiscoveryLayout.decode("broken") shouldBe DiscoveryLayout()
    }
    test("all sections can be hidden and move boundaries are safe") {
        DiscoveryLayout(hidden = DiscoveryLayout.sectionTitles.keys).visible shouldBe emptyList()
        DiscoveryLayout().move("resume", -1) shouldBe DiscoveryLayout()
        DiscoveryLayout(size = "small").scale shouldBe 0.8f
        DiscoveryLayout(size = "large").scale shouldBe 1.2f
    }
    test("server genres survive storage, reordering and hiding, then can be removed") {
        val genre = DiscoveryGenre("01234567-89ab-cdef-0123-456789abcdef", "Western")
        val added = DiscoveryLayout().addGenre(genre).move(genre.id, -100)
        val saved = DiscoveryLayout.decode(DiscoveryLayout.encode(added.copy(hidden = setOf(genre.id))))
        saved.order.first() shouldBe genre.id
        saved.customGenres shouldBe listOf(genre)
        saved.visible.contains(genre.id) shouldBe false
        saved.addGenre(genre).hidden shouldBe emptySet()
        saved.addGenre(genre).customGenres.size shouldBe 1
        saved.removeGenre(genre.id).customGenres shouldBe emptyList()
        saved.removeGenre(genre.id).order.contains(genre.id) shouldBe false
    }
    test("adding an existing default shows it without creating a duplicate") {
        val genre = DiscoveryGenre("01234567-89ab-cdef-0123-456789abcdef", "Action")
        val layout = DiscoveryLayout(hidden = setOf("genre:Action")).addGenre(genre)
        layout.hidden shouldBe emptySet()
        layout.customGenres shouldBe emptyList()
    }})
