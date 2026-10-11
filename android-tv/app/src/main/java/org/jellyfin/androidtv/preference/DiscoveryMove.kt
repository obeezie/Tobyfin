package org.jellyfin.androidtv.preference

/** An uncommitted move. Dropping applies [order]; cancelling leaves the draft untouched. */
data class DiscoveryMove(val id: String, private val original: List<String>, val position: Int = original.indexOf(id)) {
    init { require(id in original) }
    val order: List<String> get() = original.filterNot { it == id }.toMutableList().apply { add(position, id) }
    fun step(delta: Int) = copy(position = (position + delta).coerceIn(0, original.lastIndex))
}