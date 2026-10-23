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
    /** "standard" keeps the selected row under the toolbar; "spotlight" anchors it to the bottom edge. */
    val rowStyle: String = "standard",
) {
    val titles: Map<String, String> get() = sectionTitles + customGenres.associate { it.id to it.title }
    fun normalized(): DiscoveryLayout {
        val valid = customGenres.filter { it.title.isNotBlank() && runCatching { java.util.UUID.fromString(it.genreId) }.isSuccess }
            .map { it.copy(genreId = java.util.UUID.fromString(it.genreId).toString(), title = it.title.trim()) }
            .distinctBy { it.genreId }
        val removed = removedGenres.filter { it.startsWith("genre:") && it in sectionTitles }.toSet()
        val ids = (sectionTitles.keys + valid.map { it.id }) - removed
        return copy(
            order = (order.filter { it in ids } + ids).distinct(),
            hidden = hidden.filter { it in ids }.toSet(),
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
        )
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(value: String?) = runCatching { json.decodeFromString<DiscoveryLayout>(value.orEmpty()).normalized() }.getOrDefault(DiscoveryLayout())
        fun encode(value: DiscoveryLayout) = json.encodeToString(value.normalized())
    }
}
