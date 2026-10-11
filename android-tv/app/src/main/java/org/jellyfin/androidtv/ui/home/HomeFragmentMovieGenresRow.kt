package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.browsing.MovieDiscoveryRows
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType

class HomeFragmentMovieGenresRow(
	private val userRepository: UserRepository,
	private val userViews: Collection<BaseItemDto>,
    private val genreId: String? = null,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val excluded = userRepository.currentUser.value?.configuration?.latestItemsExcludes.orEmpty()
		val libraries = userViews.filter { it.collectionType == CollectionType.MOVIES && it.id !in excluded }
		for (library in libraries) {
			            val definitions = MovieDiscoveryRows.genreRows(context, library.id, onlyId = genreId)
            for (row in definitions) {
				val titledRow = if (libraries.size > 1) BrowseRowDef(
					context.getString(R.string.movie_genre_in_library, row.headerText, library.name),
					row.query, 40, false, false, emptyArray(), QueryType.MovieDiscovery
				) else row
				HomeFragmentBrowseRowDefRow(titledRow).addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}
		}
	}
}
