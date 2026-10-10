package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import org.koin.core.component.inject

object MovieDiscoveryRows : org.koin.core.component.KoinComponent {
    private val discoveryPreferences by inject<org.jellyfin.androidtv.preference.DiscoveryPreferences>()
	data class Genre(@StringRes val title: Int, val filters: Set<String>, val id: String)

	val genres = listOf(
		Genre(R.string.movie_genre_action, setOf("Action"), "genre:Action"),
		Genre(R.string.movie_genre_comedy, setOf("Comedy"), "genre:Comedy"),
		Genre(R.string.movie_genre_horror, setOf("Horror"), "genre:Horror"),
		Genre(R.string.movie_genre_suspense, setOf("Suspense", "Thriller"), "genre:Suspense"),
		Genre(R.string.movie_genre_drama, setOf("Drama"), "genre:Drama"),
		Genre(R.string.movie_genre_scifi, setOf("Science Fiction", "Sci-Fi"), "genre:Sci-Fi"),
		Genre(R.string.movie_genre_fantasy, setOf("Fantasy"), "genre:Fantasy"),
		Genre(R.string.movie_genre_adventure, setOf("Adventure"), "genre:Adventure"),
		Genre(R.string.movie_genre_crime_mystery, setOf("Crime", "Mystery"), "genre:Crime & Mystery"),
		Genre(R.string.movie_genre_animation, setOf("Animation"), "genre:Animation"),
		Genre(R.string.movie_genre_family, setOf("Family"), "genre:Family"),
		Genre(R.string.movie_genre_romance, setOf("Romance"), "genre:Romance"),
		Genre(R.string.movie_genre_documentaries, setOf("Documentary", "Documentaries"), "genre:Documentaries"),
	)

	@JvmStatic
	@JvmOverloads
    fun genreRows(context: Context, parentId: UUID, layout: org.jellyfin.androidtv.preference.DiscoveryLayout = discoveryPreferences.read(), onlyId: String? = null): List<BrowseRowDef> =
        layout.visible.filter { onlyId == null || it == onlyId }.mapNotNull { id ->
            val genre = genres.find { it.id == id }
            val custom = layout.customGenres.find { it.id == id }
            when {
                genre != null -> BrowseRowDef(context.getString(genre.title), genreQuery(parentId, genre), 40,
                    false, false, emptyArray(), QueryType.MovieDiscovery)
                custom != null -> BrowseRowDef(custom.title, latestQuery(parentId).copy(
                    genreIds = setOf(UUID.fromString(custom.genreId)), isPlayed = false,
                    sortBy = setOf(ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.ASCENDING)
                ), 40, false, false, emptyArray(), QueryType.MovieDiscovery)
                else -> null
            }
        }

    fun genreQuery(parentId: UUID, genre: Genre) = GetItemsRequest(
		parentId = parentId,
		includeItemTypes = setOf(BaseItemKind.MOVIE),
		recursive = true,
		genres = genre.filters,
		isPlayed = false,
		sortBy = setOf(ItemSortBy.SORT_NAME),
		sortOrder = setOf(SortOrder.ASCENDING),
		fields = ItemRepository.browseFields,
		enableTotalRecordCount = true,
	)

	@JvmStatic
	fun latestQuery(parentId: UUID) = GetItemsRequest(
		parentId = parentId,
		includeItemTypes = setOf(BaseItemKind.MOVIE),
		recursive = true,
		sortBy = linkedSetOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
		sortOrder = setOf(SortOrder.DESCENDING),
		fields = ItemRepository.browseFields,
		enableTotalRecordCount = true,
	)

    @JvmStatic
    fun latestSeriesRow(title: String, parentId: UUID) = BrowseRowDef(title, latestQuery(parentId).copy(
        includeItemTypes = setOf(BaseItemKind.SERIES),
        sortBy = linkedSetOf(ItemSortBy.DATE_LAST_CONTENT_ADDED, ItemSortBy.SORT_NAME),
    ), 40, false, false, emptyArray(), QueryType.MovieLatest)

	@JvmStatic
	fun latestRow(title: String, parentId: UUID) = BrowseRowDef(title, latestQuery(parentId), 40,
		false, false, emptyArray(), QueryType.MovieLatest)
}
