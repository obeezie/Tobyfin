package org.jellyfin.playback.media3.exoplayer.subtitle

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.extractor.text.CuesWithTiming
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.mockk

class SubtitleTimingTests : FunSpec({
    beforeSpec {
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            firstArg<CharSequence?>()?.toString() == secondArg<CharSequence?>()?.toString()
        }
    }
    afterSpec { unmockkStatic(android.text.TextUtils::class) }
    test("precise signed offsets and reset retain the existing user convention") {
        val session = SubtitleTimingSession()
        mapOf("-0.05" to -50L, "1.234" to 1234L, ".001" to 1L, "2,5" to 2500L, "600" to 600000L).forEach { (text, expected) ->
            SubtitleTimingSession.parseSeconds(text) shouldBe expected
        }
        listOf("NaN", "600.001", "-600.001", "", "1x2").forEach {
            SubtitleTimingSession.parseSeconds(it) shouldBe null
        }
        session.nudge(100)
        session.nudge(100)
        session.nudge(-100)
        session.delayMillis shouldBe 100L
        session.reset()
        session.delayMillis shouldBe 0L
    }

    test("offset changes immediately redisplay expired and future captions without resetting the stream") {
        val fixture = RendererFixture()
        fixture.render(1_200_000)
        fixture.visible() shouldBe emptyList()
        val consumed = fixture.source.consumed
        fixture.session.setDelayMillis(300)
        fixture.visible() shouldBe listOf(fixture.first)
        fixture.session.setDelayMillis(-900)
        fixture.visible() shouldBe listOf(fixture.second)
        fixture.session.reset()
        fixture.visible() shouldBe emptyList()
        fixture.source.consumed shouldBe consumed
        fixture.source.skips shouldBe 0
        fixture.renderer.state shouldBe Renderer.STATE_STARTED
        fixture.close()
    }

    test("a paused movie remains paused while the caption preview changes") {
        val fixture = RendererFixture()
        fixture.render(1_200_000)
        fixture.renderer.stop()
        fixture.session.setDelayMillis(300)
        fixture.visible() shouldBe listOf(fixture.first)
        fixture.renderer.state shouldBe Renderer.STATE_ENABLED
        fixture.close()
    }

    test("caption history survives EOS so a later nudge can show the final caption again") {
        val fixture = RendererFixture()
        fixture.render(3_000_000)
        fixture.visible() shouldBe emptyList()
        fixture.session.setDelayMillis(900)
        fixture.visible() shouldBe listOf(fixture.second)
        fixture.close()
    }

    test("renderer timestamps are converted once and reported in media time") {
        val fixture = RendererFixture()
        fixture.render(200_000)
        fixture.visible() shouldBe listOf(fixture.first)
        fixture.outputs.last().presentationTimeUs shouldBe 200_000L
        fixture.close()
    }

    test("seeking clears stale history and delayed output from the old position") {
        val queued = mutableListOf<() -> Unit>()
        val fixture = RendererFixture { queued.add(it) }
        fixture.render(200_000)
        fixture.renderer.resetPosition(RendererFixture.OFFSET + 4_000_000, false)
        queued.forEach { it() }
        fixture.visible() shouldBe emptyList()
        fixture.session.setDelayMillis(600000)
        fixture.visible() shouldBe emptyList()
        fixture.close()
    }

    test("disabling the caption renderer removes its live-adjustment observer") {
        val fixture = RendererFixture()
        fixture.render(200_000)
        fixture.close()
        val count = fixture.outputs.size
        fixture.session.nudge(100)
        fixture.outputs.size shouldBe count
    }

    test("merging captions preserve overlap, ordering and exact end boundaries") {
        val timeline = CueTimeline<String>()
        timeline.add(listOf("short"), 10, 10, 1)
        timeline.add(listOf("long"), 10, 20, 1)
        timeline.add(listOf("later"), 15, 20, 1)
        timeline.at(9) shouldBe emptyList()
        timeline.at(15) shouldBe listOf("long", "short", "later")
        timeline.at(20) shouldBe listOf("long", "later")
        timeline.at(35) shouldBe emptyList()
    }

    test("replacement captions do not revive an older caption when a newer one ends") {
        val timeline = CueTimeline<String>().apply { replace = true }
        timeline.add(listOf("old"), 0, 100, 1)
        timeline.add(listOf("new"), 10, 10, 1)
        timeline.at(15) shouldBe listOf("new")
        timeline.at(20) shouldBe emptyList()
        timeline.at(5) shouldBe listOf("old")
    }

    test("unknown duration is replaced by the next caption including equal timestamps") {
        val timeline = CueTimeline<String>().apply { replace = true }
        timeline.add(listOf("first"), 0, C.TIME_UNSET, 1)
        timeline.add(listOf("second"), 10, C.TIME_UNSET, 1)
        timeline.add(listOf("third"), 10, C.TIME_UNSET, 1)
        timeline.at(9) shouldBe listOf("first")
        timeline.at(1000) shouldBe listOf("third")
    }

    test("history pruning uses playback time and retains a spanning replacement") {
        val timeline = CueTimeline<String>(historyUs = 100).apply { replace = true }
        timeline.add(listOf("old"), 0, C.TIME_UNSET, 1)
        timeline.add(listOf("current"), 50, C.TIME_UNSET, 1)
        timeline.add(listOf("next"), 250, 20, 1)
        timeline.prune(200)
        timeline.at(150) shouldBe listOf("current")
        timeline.at(0) shouldBe emptyList()
    }

    test("bitmap history has a bounded storage budget") {
        val timeline = CueTimeline<String>(maxBytes = 20)
        timeline.add(listOf("one"), 0, 10, 10)
        timeline.add(listOf("two"), 10, 10, 10)
        timeline.add(listOf("three"), 20, 10, 10)
        timeline.prune(20)
        timeline.at(0) shouldBe emptyList()
        timeline.at(10) shouldBe listOf("two")
        timeline.at(20) shouldBe listOf("three")
    }
})

private class RendererFixture(post: ((() -> Unit) -> Unit) = { it() }) {
    companion object { const val OFFSET = 1_000_000_000_000L }
    val first = Cue.Builder().setText("first").build()
    val second = Cue.Builder().setText("second").build()
    val session = SubtitleTimingSession()
    val outputs = mutableListOf<CueGroup>()
    val source = CaptionStream()
    val renderer = LiveCueRenderer(session, post, { outputs.add(it) }, { time, data ->
        val id = data.get(0).toInt()
        CuesWithTiming(listOf(if (id == 0) first else second), time, if (id == 0) 1_000_000 else 500_000)
    }, { it() })

    init {
        renderer.init(0, PlayerId.UNSET, mockk<Clock>(relaxed = true))
        renderer.enable(RendererConfiguration.DEFAULT, arrayOf(source.format), source, OFFSET, false, true,
            OFFSET, OFFSET, MediaSource.MediaPeriodId("test"))
        renderer.setCurrentStreamFinal()
        renderer.start()
    }
    fun render(position: Long) = renderer.render(OFFSET + position, 0)
    fun visible(): List<Cue> = outputs.lastOrNull()?.cues ?: emptyList()
    fun close() {
        if (renderer.state == Renderer.STATE_STARTED) renderer.stop()
        if (renderer.state == Renderer.STATE_ENABLED) renderer.disable()
    }
}

private class CaptionStream : SampleStream {
    val format: Format = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
        .setCueReplacementBehavior(Format.CUE_REPLACEMENT_BEHAVIOR_MERGE).build()
    var consumed = 0
    var skips = 0
    private var formatSent = false
    override fun isReady() = true
    override fun maybeThrowError() = Unit
    override fun skipData(positionUs: Long): Int { skips++; return 0 }
    override fun readData(holder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
        if (!formatSent || readFlags and SampleStream.FLAG_REQUIRE_FORMAT != 0) {
            holder.format = format
            formatSent = true
            return C.RESULT_FORMAT_READ
        }
        if (consumed >= 2) {
            buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
            return C.RESULT_BUFFER_READ
        }
        buffer.timeUs = if (consumed == 0) 0 else 2_000_000
        buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME)
        if (readFlags and SampleStream.FLAG_OMIT_SAMPLE_DATA == 0) {
            buffer.ensureSpaceForWrite(1)
            buffer.data!!.put(consumed.toByte())
        }
        if (readFlags and SampleStream.FLAG_PEEK == 0) consumed++
        return C.RESULT_BUFFER_READ
    }
}
