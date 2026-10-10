package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private companion object {
		val FIRST_LOAD_WAIT = 8.seconds
		const val ROW_LOAD_CONCURRENCY = 3
	}

	private val api by inject<ApiClient>()
    private val discoveryPreferences by inject<org.jellyfin.androidtv.preference.DiscoveryPreferences>()
    private var discoverySignature: String? = null
    private var layoutJob: kotlinx.coroutines.Job? = null
    private var sourceRows = MutableObjectAdapter<Row>(PositionableListRowPresenter())
    private var rowVisibility: HomeRowVisibility? = null
    private val rowPositions = HomeRowPositions()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userPreferences) }

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }

	private var overlayHeaderHeight = 0
	private var headerInset: HomeHeaderInset? = null

	fun setOverlayHeaderHeight(height: Int) {
		overlayHeaderHeight = height
		headerInset?.apply(height)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		headerInset = HomeHeaderInset(requireNotNull(verticalGridView)).also { it.apply(overlayHeaderHeight) }
	}

	override fun onDestroyView() {
		headerInset = null
		super.onDestroyView()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())

        loadHomeRows()

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
                        is CustomMessage.DiscoveryLayoutChanged -> if (discoverySignature != discoveryPreferences.signature()) {
                            currentItem = null
                            currentRow = null
                            loadHomeRows()
                        }
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	    private fun loadHomeRows() {
        layoutJob?.cancel()
        discoverySignature = discoveryPreferences.signature()
		layoutJob = lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// Start out with default sections
			val savedSections = userSettingPreferences.activeHomesections
            val layout = discoveryPreferences.read()
            val homesections = savedSections.filter { it !in listOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RESUME, HomeSectionType.NEXT_UP) }
            val userViews = userViewsRepository.views.first()

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Discovery owns its complete order; other Home sections retain their saved order.
            for (id in layout.visible) when (id) {
                "resume" -> rows.add(helper.loadResumeVideo())
                "nextup" -> rows.add(helper.loadNextUp())
                "latestmovies" -> rows.add(helper.loadRecentlyAdded(userViews.filter { it.collectionType == org.jellyfin.sdk.model.api.CollectionType.MOVIES }))
                "latesttv" -> rows.add(helper.loadRecentlyAdded(userViews.filter { it.collectionType == org.jellyfin.sdk.model.api.CollectionType.TVSHOWS }))
                else -> if (id.startsWith("genre:")) rows.add(HomeFragmentMovieGenresRow(userRepository, userViews, id))
            }
            val discoveryRowCount = rows.size

            // Actually add the remaining sections
			for (section in homesections) when (section) {
				HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(userViews))
				HomeSectionType.LIBRARY_TILES_SMALL -> rows.add(HomeFragmentViewsRow(small = false))
				HomeSectionType.LIBRARY_BUTTONS -> rows.add(HomeFragmentViewsRow(small = true))
				HomeSectionType.RESUME -> rows.add(helper.loadResumeVideo())
				HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
				HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
				HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
				HomeSectionType.NEXT_UP -> rows.add(helper.loadNextUp())
				HomeSectionType.LIVE_TV -> if (currentUser.policy?.enableLiveTvAccess == true) {
					rows.add(HomeFragmentLiveTVRow(requireActivity(), userRepository))
					rows.add(helper.loadOnNow())
				}

				HomeSectionType.NONE -> Unit
			}

			rows.add(helper.loadRecentlyAdded(userViews.filter { it.collectionType !in listOf(
                org.jellyfin.sdk.model.api.CollectionType.MOVIES, org.jellyfin.sdk.model.api.CollectionType.TVSHOWS) }))

            // Add sections to layout
			withContext(Dispatchers.Main) {
				val cardPresenter = CardPresenter()
                val discoveryPresenter = CardPresenter().apply { discoveryScale = layout.scale }
                rowVisibility?.close()
                sourceRows = MutableObjectAdapter<Row>(PositionableListRowPresenter())
                val displayedRows = MutableObjectAdapter<Row>(PositionableListRowPresenter().apply {
                    savedPosition = rowPositions::positionFor
                })
                adapter = displayedRows
                val visibility = HomeRowVisibility(
                    sourceRows,
                    isPending = { (it as? ItemRowAdapter)?.isAwaitingFirstResults() == true },
                ) { visible ->
                    displayedRows.replaceAll(visible)
                }
                rowVisibility = visibility

				// Add rows in order; their requests start in that order, a few at a time.
				val loadQueue = HomeRowLoadQueue<ItemRowAdapter>(ROW_LOAD_CONCURRENCY) { adapter, done ->
					adapter.addFirstRetrieveListener { lifecycleScope.launch(Dispatchers.Main) { done() } }
					adapter.Retrieve()
				}
				HomeRowLoadQueue.current = loadQueue
				try {
					notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, sourceRows)
					nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, sourceRows)
					for ((index, row) in rows.withIndex()) row.addToRowsAdapter(requireContext(),
						if (index < discoveryRowCount) discoveryPresenter else cardPresenter, sourceRows)
				} finally {
					HomeRowLoadQueue.current = null
				}

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				@Suppress("UNCHECKED_CAST")
				val rowsAdapter = sourceRows
				for (i in 0 until rowsAdapter.size()) {
					val listRow = rowsAdapter.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						val previousRow = rowsAdapter.get(i - 1)
						if (previousRow != null) itemAdapter.setSiblingRow(previousRow)
					}
					// A row that answers with nothing changes no items, so re-check the rows below it.
					itemAdapter.addFirstRetrieveListener {
						lifecycleScope.launch(Dispatchers.Main) { visibility.refresh() }
					}
				}
				visibility.refresh()

				// Never hold the rest of Home back indefinitely behind one slow or failed row.
				lifecycleScope.launch {
					delay(FIRST_LOAD_WAIT)
					loadQueue.drain()
					visibility.stopWaiting()
				}
			}
		}

    }

override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()
        if (discoverySignature != discoveryPreferences.signature()) {
            currentItem = null
            currentRow = null
            loadHomeRows()
            return
        }

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), sourceRows)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), sourceRows)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch {
			if (delayed) delay(1.5.seconds)

			repeat(sourceRows.size()) { i ->
				val rowAdapter = (sourceRows[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (rowAdapter?.isSessionStable == true) return@repeat
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		super.onDestroy()

		rowVisibility?.close()
        rowVisibility = null
        mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item is GridButton) {
				when (item.id) {
					LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
					LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
					LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
					LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
				}
			}

			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) {
				currentItem = null
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))
				rowPositions.record(row, itemRowAdapter?.indexOf(item) ?: -1)

				backgroundService.setBackground(item.baseItem)
			}
		}
	}
}
