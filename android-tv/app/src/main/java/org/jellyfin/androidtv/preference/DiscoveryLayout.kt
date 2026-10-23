package org.jellyfin.androidtv.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DiscoveryLayout(
    val order: List<String> = sectionTitles.keys.toList(),
    val hidden: Set<String> = emptySet(),
    val size: String = "medium",
    val removedGenres: Set<String> = emptySet(),
    val customGenres: List<DiscoveryGenre> = emptyList(),
    /** The former Home section rows this layout has placed; null in layouts saved before they joined Discovery. */
    val known: Set<String>? = null,
    /** "standard" keeps the selected row under the toolbar; "spotlight" anchors it to the bottom edge. */
    val rowStyle: String = "standard",
) {
    val titles: Map<String, String> get() = sectionTitles + customGenres.associate { it.id to it.title }
    /**
     * [slots] are the account's old Home section slots. A layout that has not placed the former slot rows yet starts
     * each one On Home only if the slots showed it, so Home looks the same after the update.
     */
    fun normalized(slots: List<String> = DEFAULT_SLOTS): DiscoveryLayout {
        val valid = customGenres.filter { it.title.isNotBlank() && runCatching { java.util.UUID.fromString(it.genreId) }.isSuccess }
            .map { it.copy(genreId = java.util.UUID.fromString(it.genreId).toString(), title = it.title.trim()) }
            .distinctBy { it.genreId }
        val removed = removedGenres.filter { it.startsWith("genre:") && it in sectionTitles }.toSet()
        val ids = (sectionTitles.keys + valid.map { it.id }) - removed
        val newSlotRows = slotRows.filter { (id, types) ->
            id !in known.orEmpty() && types.isNotEmpty() && types.none { it in slots }
        }.keys
        return copy(
            order = (order.filter { it in ids } + ids).distinct(),
            // Placed rows go first, so Add to Home (newest first) lists them after rows the person took off.
            hidden = newSlotRows + hidden.filter { it in ids },
            known = slotRows.keys,
            size = size.takeIf { it in listOf("small", "medium", "large") } ?: "medium",
            rowStyle = rowStyle.takeIf { it in rowStyles } ?: "standard",
            customGenres = valid,
            removedGenres = removed,
        )
    }
    fun selectedGenreId(genre: DiscoveryGenre) = titles.entries.firstOrNull {
        it.key == genre.id || it.value.equals(genre.title, ignoreCase = true)
    }?.key
    fun addGenre(genre: DiscoveryGenre): DiscoveryLayout {
        val existing = selectedGenreId(genre)
        return if (existing != null) copy(hidden = hidden - existing, removedGenres = removedGenres - existing).normalized()
        else copy(customGenres = customGenres + genre).normalized()
    }
    fun restoreGenre(id: String) = copy(removedGenres = removedGenres - id, hidden = hidden - id).normalized()
    fun removeGenre(id: String) = if (!id.startsWith("genre:")) this else copy(
        removedGenres = removedGenres + id, customGenres = customGenres.filterNot { it.id == id },
    ).normalized()
    val visible: List<String> get() = order.filterNot { it in hidden }

	// Customize Discovery (2026-10-13 redesign): every row is either On Home or in Add to Home.
	// These keep the stored format, so Home and saved layouts work unchanged.

	/** The rows On Home, in order. */
	val onHome: List<String> get() = visible

	/** Take a row off Home. It goes to the top of Add to Home. */
	fun takeOff(id: String) = if (id !in order || id in hidden) this else copy(hidden = hidden + id)

	/** Put a row from Add to Home (a row taken off, or a removed built-in genre) at the end of On Home. */
	fun putOn(id: String): DiscoveryLayout {
		val restored = if (id in removedGenres) restoreGenre(id) else this
		if (id !in restored.order) return this
		return restored.copy(hidden = restored.hidden - id, order = restored.order - id + id)
	}

	/** Add one of the server's genres at the end of On Home. */
	fun putOn(genre: DiscoveryGenre): DiscoveryLayout {
		val added = addGenre(genre)
		return added.selectedGenreId(genre)?.let(added::putOn) ?: this
	}

	/** Move an On Home row to [index] among the rows On Home. Rows that are off keep their place behind them. */
	fun moveOnHome(id: String, index: Int): DiscoveryLayout {
		if (id !in onHome) return this
		val shown = onHome.filterNot { it == id }.toMutableList().apply { add(index.coerceIn(0, size), id) }
		return copy(order = shown + order.filterNot { it in shown })
	}

	/** A row that can be added to Home; [genre] is set for the server's genres that are not rows yet. */
	data class AddRow(val id: String, val title: String, val genre: DiscoveryGenre? = null)

	/**
	 * Add to Home, filtered by [query]: rows taken off (most recent first), removed built-in genres, then the
	 * server's genres that are not rows yet.
	 */
	fun addToHomeRows(catalog: List<DiscoveryGenre>, query: String = ""): List<AddRow> {
		val all = sectionTitles + customGenres.associate { it.id to it.title }
		val off = hidden.toList().asReversed().map { AddRow(it, all[it] ?: it) }
		val removed = removedGenres.map { AddRow(it, all[it] ?: it) }
		val server = catalog.filter { selectedGenreId(it) == null }.map { AddRow(it.id, it.title, it) }
		return (off + removed + server).filter { it.title.contains(query.trim(), ignoreCase = true) }
	}
    val scale: Float get() = when (size) { "small" -> 0.8f; "large" -> 1.2f; else -> 1f }
    val spotlight: Boolean get() = rowStyle == "spotlight"
    val rowStyleTitle: String get() = if (spotlight) "Spotlight" else "Standard"
    fun nextRowStyle() = copy(rowStyle = rowStyles[(rowStyles.indexOf(rowStyle) + 1) % rowStyles.size])
    fun move(id: String, delta: Int): DiscoveryLayout {
        val next = order.toMutableList()
        val from = next.indexOf(id)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, next.lastIndex)
        next.add(to, next.removeAt(from))
        return copy(order = next)
    }
    companion object {
        val rowStyles = listOf("standard", "spotlight")
        val sectionTitles = linkedMapOf(
            "resume" to "Continue Watching", "nextup" to "Up Next",
            "latestmovies" to "Recently Added Movies", "latesttv" to "Recently Added TV",
            "genre:Action" to "Action", "genre:Comedy" to "Comedy", "genre:Horror" to "Horror",
            "genre:Suspense" to "Suspense", "genre:Drama" to "Drama", "genre:Sci-Fi" to "Sci-Fi",
            "genre:Fantasy" to "Fantasy", "genre:Adventure" to "Adventure",
            "genre:Crime & Mystery" to "Crime & Mystery", "genre:Animation" to "Animation",
            "genre:Family" to "Family", "genre:Romance" to "Romance", "genre:Documentaries" to "Documentaries",
            "mymedia" to "My Media", "resumeaudio" to "Continue Listening", "livetv" to "Live TV",
            "activerecordings" to "Recordings", "latestother" to "Recently Added Music & More",
        )
        /**
         * Rows that used to come from the Home section slots, with the slot types that showed them. "Recently Added
         * Music & More" was always shown. Books are not supported here, so Continue Reading is left out.
         */
        val slotRows = linkedMapOf(
            "mymedia" to setOf("smalllibrarytiles", "librarybuttons"),
            "resumeaudio" to setOf("resumeaudio"),
            "livetv" to setOf("livetv"),
            "activerecordings" to setOf("activerecordings"),
            "latestother" to emptySet(),
        )
        /** The Home section slots' defaults, used when an account never set them. */
        val DEFAULT_SLOTS = listOf("smalllibrarytiles", "resume", "resumeaudio", "resumebook", "livetv", "nextup", "latestmedia")
        /** Continue Watching, Up Next, the Recently Added rows and the former Home section rows are built in; everything else is a genre. */
        fun isBuiltIn(id: String) = !id.startsWith("genre:")

        private val json = Json { ignoreUnknownKeys = true }
        fun decode(value: String?, slots: List<String> = DEFAULT_SLOTS) =
            (runCatching { json.decodeFromString<DiscoveryLayout>(value.orEmpty()) }.getOrNull() ?: DiscoveryLayout()).normalized(slots)
        fun encode(value: DiscoveryLayout) = json.encodeToString(value.normalized())
    }
}
