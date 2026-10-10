package org.jellyfin.mobile.player.subtitle

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/** Modern Media3 caption scheduling, independent of the audio/video clock and buffer state. */
internal class LiveCueRenderer(
    private val timing: SubtitleTimingSession,
    private val postOutput: (() -> Unit) -> Unit,
    private val output: (CueGroup) -> Unit,
    private val decode: (Long, ByteBuffer) -> CuesWithTiming = { time, data ->
        CueDecoder().decode(time, data.array(), data.arrayOffset(), data.limit())
    },
    private val refreshScheduler: ((() -> Unit) -> Unit)? = null,
) : BaseRenderer(C.TRACK_TYPE_TEXT) {
    private val timeline = CueTimeline<Cue>()
    private val input = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    private val peek = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    private val holder = FormatHolder()
    private val outputVersion = AtomicLong(0)
    private var refreshHandler: Handler? = null
    private var positionUs = C.TIME_UNSET
    private var inputEnded = false
    private var visible: List<Cue>? = null

    override fun getName() = "LiveSubtitleTiming"
    override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
        if (format.sampleMimeType != MimeTypes.APPLICATION_MEDIA3_CUES) C.FORMAT_UNSUPPORTED_TYPE
        else if (format.cryptoType != C.CRYPTO_TYPE_NONE) C.FORMAT_UNSUPPORTED_DRM else C.FORMAT_HANDLED
    )

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        val schedule: (() -> Unit) -> Unit = refreshScheduler ?: run {
            val handler = Handler(checkNotNull(Looper.myLooper()))
            refreshHandler = handler
            val dispatch: (() -> Unit) -> Unit = { action -> handler.post { action() }; Unit }
            dispatch
        }
        timing.observe(this) {
            schedule {
                if (state != Renderer.STATE_DISABLED && positionUs != C.TIME_UNSET) render(positionUs, 0)
            }
        }
    }

    override fun onStreamChanged(formats: Array<Format>, startPositionUs: Long, offsetUs: Long, mediaPeriodId: MediaSource.MediaPeriodId) {
        clear()
        timeline.replace = formats.first().cueReplacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, streamReset: Boolean) {
        clear()
        this.positionUs = positionUs
    }

    private fun clear() {
        timeline.clear()
        inputEnded = false
        positionUs = C.TIME_UNSET
        visible = null
        publish(emptyList(), 0)
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        this.positionUs = positionUs
        val captionUs = positionUs - timing.delayMillis * 1000
        // Read ahead only on the subtitle stream, with a bounded amount of work each frame.
        for (sample in 0 until 64) {
            if (inputEnded) break
            peek.clear()
            holder.clear()
            val peekResult = readSource(holder, peek, SampleStream.FLAG_PEEK or SampleStream.FLAG_OMIT_SAMPLE_DATA)
            if (peekResult == C.RESULT_FORMAT_READ) {
                holder.format?.let { timeline.replace = it.cueReplacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE }
                continue
            }
            if (peek.isEndOfStream) {
                val end = streamEndPositionUs
                if (end != C.TIME_UNSET && positionUs < end - 1_000_000) break
            } else if (peekResult != C.RESULT_BUFFER_READ || peek.timeUs > maxOf(positionUs, captionUs) + 1_000_000) {
                break
            }
            input.clear()
            holder.clear()
            when (readSource(holder, input, 0)) {
                C.RESULT_FORMAT_READ -> holder.format?.let {
                    timeline.replace = it.cueReplacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
                }
                C.RESULT_BUFFER_READ -> {
                    if (input.isEndOfStream) {
                        inputEnded = true
                    } else {
                        input.flip()
                        val data = input.data ?: continue
                        val packet = decode(input.timeUs, data)
                        val bytes = packet.cues.sumOf { cue ->
                            (cue.bitmap?.allocationByteCount?.toLong() ?: 0) + (cue.text?.length ?: 0) * 2L + 128
                        }
                        timeline.add(packet.cues, packet.startTimeUs, packet.durationUs, bytes)
                    }
                }
            }
        }
        timeline.prune(positionUs)
        publish(timeline.at(captionUs), positionUs - streamOffsetUs)
    }

    private fun publish(cues: List<Cue>, presentationTimeUs: Long) {
        if (cues == visible) return
        visible = cues
        val version = outputVersion.incrementAndGet()
        postOutput {
            if (version == outputVersion.get()) output(CueGroup(cues, presentationTimeUs))
        }
    }

    override fun onDisabled() {
        timing.forget(this)
        refreshHandler?.removeCallbacksAndMessages(null)
        refreshHandler = null
        clear()
    }

    override fun isReady() = true // Captions must never stall audio/video.
    override fun isEnded(): Boolean {
        if (!inputEnded || positionUs == C.TIME_UNSET) return false
        val end = streamEndPositionUs
        if (end != C.TIME_UNSET) return positionUs >= end
        val captionEnd = timeline.lastEndUs() ?: return true
        return captionEnd == Long.MAX_VALUE || positionUs - timing.delayMillis * 1000 >= captionEnd
    }
}
