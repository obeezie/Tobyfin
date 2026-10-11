package org.jellyfin.playback.media3.exoplayer.subtitle

import androidx.media3.common.C

/**
 * Retains original caption times so a live offset can move in either direction.
 * Replacement never revives an older caption after its replacement expires.
 */
internal class CueTimeline<T>(
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val historyUs: Long = SubtitleTimingSession.MAX_DELAY_MILLIS * 1000,
) {
    private data class Entry<T>(val cues: List<T>, val start: Long, val duration: Long, val bytes: Long) {
        val end: Long get() = if (duration == C.TIME_UNSET) Long.MAX_VALUE else start + duration
    }
    private val entries = mutableListOf<Entry<T>>()
    private var bytes = 0L
    var replace = false

    fun add(cues: List<T>, start: Long, duration: Long, sizeBytes: Long) {
        val entry = Entry(cues, start, duration, sizeBytes.coerceAtLeast(1))
        val index = entries.indexOfFirst { it.start > start }.takeIf { it >= 0 } ?: entries.size
        entries.add(index, entry)
        bytes += entry.bytes
    }

    fun at(timeUs: Long): List<T> {
        if (replace) {
            val entry = entries.lastOrNull { it.start <= timeUs } ?: return emptyList()
            return if (timeUs < entry.end) entry.cues else emptyList()
        }
        return entries.asSequence().filter { it.start <= timeUs && timeUs < it.end }
            .sortedWith(compareBy<Entry<T>> { it.start }.thenByDescending { it.duration })
            .flatMap { it.cues.asSequence() }.toList()
    }

    fun prune(playbackUs: Long) {
        val cutoff = playbackUs - historyUs
        if (replace) {
            while (entries.size > 1 && entries[1].start <= cutoff) removeFirst()
        } else {
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.end < cutoff) { bytes -= entry.bytes; iterator.remove() }
            }
        }
        while (entries.size > 1 && (bytes > maxBytes || entries.size > 10000)) removeFirst()
    }

    private fun removeFirst() { bytes -= entries.removeAt(0).bytes }
    fun clear() { entries.clear(); bytes = 0 }
    fun lastEndUs(): Long? = entries.maxOfOrNull { it.end }
}
