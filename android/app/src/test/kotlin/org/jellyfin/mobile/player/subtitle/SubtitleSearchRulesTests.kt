package org.jellyfin.mobile.player.subtitle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import java.util.UUID

class SubtitleSearchRulesTests : FunSpec({
	fun item(location: LocationType? = LocationType.FILE_SYSTEM, channel: UUID? = null) =
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, name = "The Long Weekend", locationType = location, channelId = channel)

	fun source(path: String?, name: String? = null) = MediaSourceInfo(
		protocol = MediaProtocol.FILE, type = MediaSourceType.DEFAULT, path = path, name = name,
		isRemote = false, readAtNativeFramerate = false, ignoreDts = false, ignoreIndex = false, genPtsInput = false,
		supportsTranscoding = true, supportsDirectStream = true, supportsDirectPlay = true, isInfiniteStream = false,
		useMostCompatibleTranscodingProfile = false, requiresOpening = false, requiresClosing = false, requiresLooping = false,
		supportsProbing = true, transcodingSubProtocol = org.jellyfin.sdk.model.api.MediaStreamProtocol.HTTP,
		hasSegments = false,
	)

	fun subtitle(index: Int) = MediaStream(
		type = MediaStreamType.SUBTITLE, index = index, isInterlaced = false, isDefault = false, isForced = false,
		isHearingImpaired = false, isExternal = true, isTextSubtitleStream = true, supportsExternalStream = true,
		videoRange = VideoRange.UNKNOWN, videoRangeType = VideoRangeType.UNKNOWN,
		audioSpatialFormat = org.jellyfin.sdk.model.api.AudioSpatialFormat.NONE,
	)

	test("only videos with a file on the server can search") {
		SubtitleSearchRules.canSearch(item()) shouldBe true
		SubtitleSearchRules.canSearch(item(location = LocationType.VIRTUAL)) shouldBe false
		SubtitleSearchRules.canSearch(item(channel = UUID.randomUUID())) shouldBe false
		SubtitleSearchRules.canSearch(null) shouldBe false
	}

	test("the release name is the file name without its extension") {
		SubtitleSearchRules.releaseName(source("/media/movies/The.Long.Weekend.2019.1080p.BluRay.x264-SPARKS.mkv"), null) shouldBe
			"The.Long.Weekend.2019.1080p.BluRay.x264-SPARKS"
		SubtitleSearchRules.releaseName(source("""D:\Movies\Weekend.mp4"""), null) shouldBe "Weekend"
		SubtitleSearchRules.releaseName(source(null, name = "Weekend 1080p"), null) shouldBe "Weekend 1080p"
		SubtitleSearchRules.releaseName(null, item()) shouldBe "The Long Weekend"
	}

	test("your languages come first, then the rest") {
		val all = listOf(SubtitleLanguage("dut", "Dutch"), SubtitleLanguage("eng", "English"), SubtitleLanguage("spa", "Spanish"))
		val (mine, others) = SubtitleSearchRules.orderLanguages(all, listOf("spa", "ENG", null, "spa"))
		mine.map { it.code } shouldBe listOf("spa", "eng")
		others.map { it.code } shouldBe listOf("dut")
	}

	test("download counts are short") {
		SubtitleSearchRules.downloads(312) shouldBe "312"
		SubtitleSearchRules.downloads(18_240) shouldBe "18.2k"
		SubtitleSearchRules.downloads(9_000) shouldBe "9k"
		SubtitleSearchRules.downloads(1_500_000) shouldBe "1.5M"
		SubtitleSearchRules.downloads(null) shouldBe null
	}

	test("the new track is the subtitle that was not there before the download") {
		SubtitleSearchRules.newTrack(setOf(2, 3), listOf(subtitle(2), subtitle(3), subtitle(4)))?.index shouldBe 4
		SubtitleSearchRules.newTrack(setOf(2, 3), listOf(subtitle(2), subtitle(3))) shouldBe null
	}
})
