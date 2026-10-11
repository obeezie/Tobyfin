package org.jellyfin.mobile.player.subtitle

import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A subtitle-only clock. This module has no video-player seek, prepare or reload dependency. */
class SubtitleTimingSession {
    private val offset = AtomicLong(0)
    private val listeners = ConcurrentHashMap<Any, () -> Unit>()
    val delayMillis: Long get() = offset.get()

    fun setDelayMillis(value: Long) {
        require(value in -MAX_DELAY_MILLIS..MAX_DELAY_MILLIS)
        if (offset.getAndSet(value) != value) listeners.values.forEach { it() }
    }

    fun nudge(millis: Long) = setDelayMillis((delayMillis + millis).coerceIn(-MAX_DELAY_MILLIS, MAX_DELAY_MILLIS))
    fun reset() = setDelayMillis(0)
    internal fun observe(owner: Any, refresh: () -> Unit) { listeners[owner] = refresh }
    internal fun forget(owner: Any) { listeners.remove(owner) }

    fun canAdjust(tracks: Tracks): Boolean = tracks.groups.any { group ->
        (0 until group.length).any { index ->
            val format = group.getTrackFormat(index)
            group.isTrackSelected(index) && (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES ||
                DefaultSubtitleParserFactory().supportsFormat(format))
        }
    }

    companion object {
        const val MAX_DELAY_MILLIS = 600_000L
        @JvmStatic fun parseSeconds(text: String): Long? = runCatching {
            val normalized = text.trim().replace(',', '.')
            if (!normalized.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return@runCatching null
            val millis = BigDecimal(normalized).multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact()
            millis.takeIf { it in -MAX_DELAY_MILLIS..MAX_DELAY_MILLIS }
        }.getOrNull()
        @JvmStatic fun formatSeconds(value: Long): String = BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()
    }
}
