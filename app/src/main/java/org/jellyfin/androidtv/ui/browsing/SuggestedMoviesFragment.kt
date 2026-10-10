package org.jellyfin.androidtv.ui.browsing

class SuggestedMoviesFragment : EnhancedBrowseFragment() {
	init {
		showViews = false
	}

	override fun setupQueries(rowLoader: RowLoader) {
		mRows.clear()
		mRows.addAll(MovieDiscoveryRows.genreRows(requireContext(), mFolder.id))
		rowLoader.loadRows(mRows)
	}
}
