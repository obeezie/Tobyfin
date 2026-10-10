package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.io.File
import java.util.UUID

class HomeRowCacheTests : FunSpec({
    test("a saved row reads back in the same order for the same account") {
        val cache = HomeRowCache(tempdir())
        val items = listOf(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.EPISODE, name = "Bad Sisters"),
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, name = "Split"),
        )
        cache.write("user-a/resume-video", items)
        cache.read("user-a/resume-video")?.map { it.id } shouldBe items.map { it.id }
        cache.read("user-b/resume-video") shouldBe null
    }
    test("a damaged saved row is ignored rather than breaking Home") {
        val dir = tempdir()
        val cache = HomeRowCache(dir)
        File(dir, "user-a_resume-video.json").writeText("{not json")
        cache.read("user-a/resume-video") shouldBe null
    }
})
