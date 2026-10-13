package org.jellyfin.androidtv.ui.playback.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A language the server's subtitle providers can be searched in. [code] is the 3-letter ISO 639-2 code. */
data class SubtitleLanguage(val code: String, val name: String)

/**
 * "Find other subtitles": search the server's subtitle providers for the playing video, download one, and
 * wait for the server to list it as a new subtitle track.
 */
class SubtitleSearch : KoinComponent {
	private val api: ApiClient by inject()
	private val userRepository: UserRepository by inject()
	private val userPreferences: UserPreferences by inject()

	/** Only accounts allowed to manage subtitles can search; the server refuses everyone else. */
	fun isAllowed(): Boolean {
		val policy = userRepository.currentUser.value?.policy ?: return false
		return policy.enableSubtitleManagement || policy.isAdministrator
	}

	/** The language to search first: the last one searched on this device, else the account's preference. */
	fun startLanguage(): String? = userPreferences[UserPreferences.subtitleSearchLanguage].ifBlank { null }
		?: userRepository.currentUser.value?.configuration?.subtitleLanguagePreference?.ifBlank { null }

	fun rememberLanguage(code: String) {
		userPreferences[UserPreferences.subtitleSearchLanguage] = code
	}

	suspend fun languages(): List<SubtitleLanguage> = withContext(Dispatchers.IO) {
		api.localizationApi.getCultures().content
			.mapNotNull { culture ->
				val code = culture.threeLetterIsoLanguageName?.ifBlank { null } ?: return@mapNotNull null
				SubtitleLanguage(code, culture.displayName ?: culture.name ?: code)
			}
			.distinctBy { it.code }
			.sortedBy { it.name.lowercase(Locale.ROOT) }
	}

	suspend fun search(itemId: UUID, language: String): List<RemoteSubtitleInfo> = withContext(Dispatchers.IO) {
		api.subtitleApi.searchRemoteSubtitles(itemId = itemId, language = language).content
	}

	suspend fun download(itemId: UUID, subtitleId: String) = withContext(Dispatchers.IO) {
		api.subtitleApi.downloadRemoteSubtitles(itemId = itemId, subtitleId = subtitleId)
	}

	/**
	 * After a download the server rescans the video in the background. Wait for a subtitle track that was not
	 * there before, or give up after [timeout].
	 */
	suspend fun awaitNewTrack(
		itemId: UUID,
		mediaSourceId: String?,
		before: Set<Int>,
		timeout: Duration = 15.seconds,
		every: Duration = 1500.milliseconds,
	): MediaStream? {
		var waited = Duration.ZERO
		while (waited < timeout) {
			delay(every)
			waited += every
			val streams = try {
				withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = itemId).content }.subtitleStreams(mediaSourceId)
			} catch (err: ApiClientException) {
				Timber.w(err, "Unable to check for the new subtitle track")
				continue
			}
			SubtitleSearchRules.newTrack(before, streams)?.let { return it }
		}
		return null
	}

	private fun BaseItemDto.subtitleStreams(mediaSourceId: String?): List<MediaStream> {
		val source = mediaSources?.firstOrNull { it.id == mediaSourceId } ?: mediaSources?.firstOrNull()
		return (source?.mediaStreams ?: mediaStreams).orEmpty().filter { it.type == MediaStreamType.SUBTITLE }
	}
}

/** The decisions behind "Find other subtitles", kept free of Android so they can be unit tested. */
object SubtitleSearchRules {
	/** Videos that have a file on the server; Live TV and missing episodes have nothing to save subtitles next to. */
	fun canSearch(item: BaseItemDto?): Boolean = item != null &&
		item.locationType != LocationType.VIRTUAL &&
		item.channelId == null

	/** The release name of the playing file, such as "Movie.2019.1080p.BluRay-GROUP". */
	fun releaseName(source: MediaSourceInfo?, item: BaseItemDto?): String? {
		val fileName = source?.path?.substringAfterLast('/')?.substringAfterLast('\\')?.ifBlank { null }
		return fileName?.substringBeforeLast('.', fileName) ?: source?.name ?: item?.name
	}

	/** Your languages first (the search language, then the account's preference), then all others A to Z. */
	fun orderLanguages(all: List<SubtitleLanguage>, mine: List<String?>): Pair<List<SubtitleLanguage>, List<SubtitleLanguage>> {
		val codes = mine.filterNotNull().filter { it.isNotBlank() }.distinct()
		val first = codes.mapNotNull { code -> all.firstOrNull { it.code.equals(code, ignoreCase = true) } }
		return first to all.filterNot { it in first }
	}

	/** "18.2k downloads" style counts for a TV screen. */
	fun downloads(count: Int?): String? = when {
		count == null -> null
		count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
		count >= 1_000 -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0).replace(".0k", "k")
		else -> count.toString()
	}

	/** A subtitle track the server lists now but did not before the download. */
	fun newTrack(before: Set<Int>, now: List<MediaStream>): MediaStream? =
		now.filter { it.type == MediaStreamType.SUBTITLE && it.index !in before }.maxByOrNull { it.index }
}
