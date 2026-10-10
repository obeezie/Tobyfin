package org.jellyfin.playback.media3.exoplayer.subtitle

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.peerless2012.ass.media.render.AssRenderer

/** One integration seam for all native players. Audio and video renderers pass through unchanged. */
class LiveSubtitleRenderersFactory(
    private val delegate: RenderersFactory,
    private val timing: SubtitleTimingSession,
) : RenderersFactory {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> = delegate.createRenderers(eventHandler, videoRendererEventListener,
        audioRendererEventListener, textRendererOutput, metadataRendererOutput).flatMap { renderer ->
        when (renderer) {
            is TextRenderer -> listOf(
                LiveCueRenderer(timing, { action -> eventHandler.post { action() }; Unit }, { group ->
                    @Suppress("DEPRECATION")
                    textRendererOutput.onCues(group.cues)
                    textRendererOutput.onCues(group)
                }),
                LegacyCaptionRenderer(renderer),
            )
            is AssRenderer -> listOf(LiveAssRenderer(renderer, timing))
            else -> listOf(renderer)
        }
    }.toTypedArray()
}

/** Keep the stock decoder for broadcast captions that do not use Media3's parsed cue format. */
private class LegacyCaptionRenderer(private val delegate: Renderer) : Renderer by delegate {
    private val capabilities = object : RendererCapabilities by delegate.capabilities {
        override fun supportsFormat(format: Format): Int =
            if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES)
                RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
            else delegate.capabilities.supportsFormat(format)
    }
    override fun getCapabilities(): RendererCapabilities = capabilities
}

/** ASS owns its complete caption script; only its subtitle clock needs to move. */
private class LiveAssRenderer(
    private val delegate: Renderer,
    private val timing: SubtitleTimingSession,
) : Renderer by delegate {
    private var handler: Handler? = null
    private var lastPositionUs = 0L
    private var lastElapsedUs = 0L
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        lastPositionUs = positionUs
        lastElapsedUs = elapsedRealtimeUs
        if (handler == null) {
            val refreshHandler = Handler(checkNotNull(Looper.myLooper()))
            handler = refreshHandler
            timing.observe(this) {
                refreshHandler.post {
                    if (delegate.state != Renderer.STATE_DISABLED)
                        delegate.render(lastPositionUs - timing.delayMillis * 1000, lastElapsedUs)
                }
            }
        }
        delegate.render(positionUs - timing.delayMillis * 1000, elapsedRealtimeUs)
    }
    private fun detach() {
        timing.forget(this)
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }
    override fun disable() { detach(); delegate.disable() }
    override fun reset() { detach(); delegate.reset() }
    override fun release() { detach(); delegate.release() }
}
