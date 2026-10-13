package org.jellyfin.androidtv.ui.playback.subtitle

import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle
import org.jellyfin.androidtv.ui.startup.fragment.onBackPress
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import timber.log.Timber
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the side panel shows. */
private sealed interface Step {
	data object Results : Step
	data object Languages : Step
	data class Adding(val chosen: RemoteSubtitleInfo) : Step
	data object Saved : Step
}

/**
 * Open "Find other subtitles" as a side panel over the paused video.
 * Design approved in prototype/subtitle-search-prototype.html.
 *
 * @param scope outlives the panel, so a slow server can still report the new track after the panel closes.
 * @param onSwitch the new track is ready: restart playback with it.
 * @param onReadyLater the new track appeared after playback resumed: offer it in the CC menu.
 */
fun showFindOtherSubtitles(
	context: Context,
	scope: CoroutineScope,
	item: BaseItemDto,
	mediaSource: MediaSourceInfo?,
	search: SubtitleSearch,
	pause: () -> Unit,
	resume: () -> Unit,
	onSwitch: (MediaStream) -> Unit,
	onReadyLater: (MediaStream) -> Unit,
) {
	val dialog = ComponentDialog(context)
	var resumeOnDismiss = true
	val state = PanelState(item, mediaSource, search)

	fun close(resumePlayback: Boolean) {
		resumeOnDismiss = resumePlayback
		dialog.dismiss()
	}

	state.onChosen = { chosen ->
		val before = mediaSource?.mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE }.map { it.index }.toSet()
		state.step = Step.Adding(chosen)
		// The work outlives the panel: if the server is slow, the track can still arrive after playback resumes.
		scope.launch {
			val subtitleId = chosen.id ?: return@launch
			try {
				search.download(item.id, subtitleId)
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to download subtitles")
				state.failed = true
				state.step = Step.Results
				return@launch
			}
			val track = search.awaitNewTrack(item.id, mediaSource?.id, before)
			if (track != null && dialog.isShowing) {
				close(resumePlayback = false)
				onSwitch(track)
				return@launch
			}
			if (track != null) return@launch onReadyLater(track)
			// Slow server: say so, resume playback, and keep watching for the track for a while.
			if (dialog.isShowing) {
				state.step = Step.Saved
				launch { delay(4.seconds); if (dialog.isShowing) close(resumePlayback = true) }
			}
			search.awaitNewTrack(item.id, mediaSource?.id, before, timeout = 2.minutes, every = 5.seconds)?.let(onReadyLater)
		}
	}
	state.onClose = { close(resumePlayback = true) }

	dialog.setContentView(ComposeView(context).apply {
		setContent { JellyfinTheme { FindOtherSubtitlesPanel(state) } }
	})
	dialog.window?.apply {
		setBackgroundDrawable(ColorDrawable(TRANSPARENT))
		setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
	}
	dialog.setOnDismissListener {
		state.cancel()
		if (resumeOnDismiss) resume()
	}
	pause()
	dialog.show()
	state.start(scope)
}

private class PanelState(val item: BaseItemDto, val mediaSource: MediaSourceInfo?, val search: SubtitleSearch) {
	var step by mutableStateOf<Step>(Step.Results)
	var language by mutableStateOf(search.startLanguage() ?: "eng")
	var languages by mutableStateOf<List<SubtitleLanguage>>(emptyList())
	var results by mutableStateOf<List<RemoteSubtitleInfo>?>(null)
	var searchFailed by mutableStateOf(false)
	var failed by mutableStateOf(false)
	lateinit var onChosen: (RemoteSubtitleInfo) -> Unit
	lateinit var onClose: () -> Unit
	private var scope: CoroutineScope? = null
	private var searchJob: Job? = null
	private var languagesJob: Job? = null

	val releaseName get() = SubtitleSearchRules.releaseName(mediaSource, item)
	val languageName get() = languages.firstOrNull { it.code.equals(language, ignoreCase = true) }?.name
		?: Locale.forLanguageTag(language).getDisplayLanguage(Locale.getDefault()).ifBlank { language }

	fun start(scope: CoroutineScope) {
		this.scope = scope
		languagesJob = scope.launch {
			languages = try { search.languages() } catch (err: ApiClientException) { Timber.w(err, "Unable to load languages"); emptyList() }
		}
		runSearch()
	}

	fun runSearch() {
		val scope = scope ?: return
		searchJob?.cancel()
		results = null
		searchFailed = false
		searchJob = scope.launch {
			results = try {
				search.search(item.id, language)
			} catch (err: ApiClientException) {
				Timber.e(err, "Subtitle search failed")
				searchFailed = true
				emptyList()
			}
		}
	}

	fun chooseLanguage(code: String) {
		language = code
		search.rememberLanguage(code)
		step = Step.Results
		runSearch()
	}

	fun choose(result: RemoteSubtitleInfo) {
		failed = false
		search.rememberLanguage(language)
		onChosen(result)
	}

	fun back() {
		when (step) {
			Step.Languages -> step = Step.Results
			// Leaving while it is being added: playback resumes and the track still arrives in the CC menu.
			else -> onClose()
		}
	}

	fun cancel() {
		searchJob?.cancel()
		languagesJob?.cancel()
	}
}

@Composable
private fun FindOtherSubtitlesPanel(state: PanelState) {
	val slide = remember { Animatable(1f) }
	LaunchedEffect(Unit) { slide.animateTo(0f, tween(300, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) }

	Box(Modifier.fillMaxSize().onBackPress(state::back)) {
		Column(
			verticalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier
				.align(Alignment.CenterEnd)
				.fillMaxHeight()
				.width(440.dp)
				.offset(x = (440 * slide.value).dp)
				.background(Color(0xF50C1228))
				.border(1.dp, WhosWatchingStyle.Line)
				.padding(horizontal = 28.dp, vertical = 30.dp),
		) {
			when (val step = state.step) {
				Step.Results -> ResultsStep(state)
				Step.Languages -> LanguagesStep(state)
				is Step.Adding -> AddingStep(step.chosen)
				Step.Saved -> SavedStep()
			}
		}
	}
}

@Composable
private fun ColumnScope.ResultsStep(state: PanelState) {
	val first = remember { FocusRequester() }
	val languageRow = remember { FocusRequester() }
	Eyebrow(stringResource(R.string.subtitle_search_eyebrow))
	Title(stringResource(R.string.subtitle_search_title))
	state.releaseName?.let { name ->
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(WhosWatchingStyle.Field)
				.border(1.dp, WhosWatchingStyle.Line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
		) {
			Text(stringResource(R.string.subtitle_search_your_file), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = WhosWatchingStyle.Muted)
			Text(name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
		}
	}
	val results = state.results
	Column(
		verticalArrangement = Arrangement.spacedBy(10.dp),
		modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
	) {
		PanelRow(onClick = { state.step = Step.Languages }, modifier = Modifier.focusRequester(languageRow)) {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				Text(stringResource(R.string.subtitle_search_language), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
				Text(state.languageName + "  ›", fontSize = 16.sp, color = WhosWatchingStyle.Muted)
			}
		}
		if (state.failed) Message(stringResource(R.string.subtitle_search_add_failed), stringResource(R.string.subtitle_search_add_failed_summary))
		when {
			results == null -> Busy(stringResource(R.string.subtitle_search_searching, state.languageName))
			state.searchFailed -> Message(stringResource(R.string.subtitle_search_failed), stringResource(R.string.subtitle_search_failed_summary))
			results.isEmpty() -> Message(
				stringResource(R.string.subtitle_search_none, state.languageName),
				stringResource(R.string.subtitle_search_none_summary),
			)
			else -> results.forEachIndexed { index, result ->
				PanelRow(onClick = { state.choose(result) }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier) {
					Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
						Text(result.name ?: result.id.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, lineHeight = 20.sp)
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
							if (result.hearingImpaired == true) Tag(stringResource(R.string.subtitle_search_sdh), Color(0xFF2E3F73), Color(0xFFC7D3FF))
							if (result.forced == true) Tag(stringResource(R.string.subtitle_search_forced), Color(0xFF5A3B17), Color(0xFFFFD39A))
							if (result.machineTranslated == true || result.aiTranslated == true) {
								Tag(stringResource(R.string.subtitle_search_machine_translated), Color(0xFF4A2333), Color(0xFFFFB3C4))
							}
							Text(
								listOfNotNull(
									result.providerName,
									result.format?.uppercase(Locale.ROOT),
									SubtitleSearchRules.downloads(result.downloadCount)?.let { stringResource(R.string.subtitle_search_downloads, it) },
								).joinToString(" · "),
								fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = WhosWatchingStyle.Muted,
							)
						}
					}
				}
			}
		}
	}
	Text(stringResource(R.string.subtitle_search_hint), fontSize = 13.sp, color = WhosWatchingStyle.Muted)
	// Start on the first result once they arrive; until then, and when there are none, on the language row.
	LaunchedEffect(results) {
		if (!results.isNullOrEmpty() && !state.searchFailed) first.requestFocus() else languageRow.requestFocus()
	}
}

@Composable
private fun ColumnScope.LanguagesStep(state: PanelState) {
	val first = remember { FocusRequester() }
	val (mine, others) = SubtitleSearchRules.orderLanguages(state.languages, listOf(state.language, state.search.startLanguage()))
	Eyebrow(stringResource(R.string.subtitle_search_title))
	Title(stringResource(R.string.subtitle_search_language))
	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
	) {
		if (state.languages.isEmpty()) Busy(stringResource(R.string.subtitle_search_loading_languages))
		if (mine.isNotEmpty()) Group(stringResource(R.string.subtitle_search_your_languages))
		mine.forEachIndexed { index, language -> LanguageRow(state, language, if (index == 0) Modifier.focusRequester(first) else Modifier) }
		if (others.isNotEmpty()) Group(stringResource(R.string.subtitle_search_all_languages))
		others.forEachIndexed { index, language -> LanguageRow(state, language, if (mine.isEmpty() && index == 0) Modifier.focusRequester(first) else Modifier) }
	}
	LaunchedEffect(state.languages.isEmpty()) { if (state.languages.isNotEmpty()) first.requestFocus() }
}

@Composable
private fun LanguageRow(state: PanelState, language: SubtitleLanguage, modifier: Modifier) {
	PanelRow(onClick = { state.chooseLanguage(language.code) }, modifier = modifier) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(language.name, fontSize = 16.sp, color = Color.White)
			if (language.code.equals(state.language, ignoreCase = true)) Text("✓", fontSize = 16.sp, color = WhosWatchingStyle.Cyan)
		}
	}
}

@Composable
private fun AddingStep(chosen: RemoteSubtitleInfo) {
	Eyebrow(stringResource(R.string.subtitle_search_title))
	Title(stringResource(R.string.subtitle_search_adding))
	Busy(stringResource(R.string.subtitle_search_adding_summary))
	Text(chosen.name.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = WhosWatchingStyle.Muted)
}

@Composable
private fun SavedStep() {
	Eyebrow(stringResource(R.string.subtitle_search_title))
	Title(stringResource(R.string.subtitle_search_saved))
	Message(stringResource(R.string.subtitle_search_saved_title), stringResource(R.string.subtitle_search_saved_summary))
}

// Building blocks, in the Who's watching style

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp, color = WhosWatchingStyle.Muted)

@Composable
private fun Title(text: String) = Text(text, fontFamily = WhosWatchingStyle.Jakarta, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White)

@Composable
private fun Group(text: String) = Text(
	text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp, color = WhosWatchingStyle.Muted,
	modifier = Modifier.padding(top = 6.dp),
)

@Composable
private fun Busy(text: String) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
		CircularProgressIndicator(Modifier.size(20.dp), color = WhosWatchingStyle.Cyan)
		Text(text, fontSize = 16.sp, color = WhosWatchingStyle.Muted)
	}
}

@Composable
private fun Message(title: String, detail: String) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 6.dp)) {
		Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
		Text(detail, fontSize = 15.sp, color = WhosWatchingStyle.Muted, lineHeight = 21.sp)
	}
}

@Composable
private fun Tag(text: String, background: Color, color: Color) {
	Text(
		text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
		modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
	)
}

@Composable
private fun PanelRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	val source = remember { MutableInteractionSource() }
	val focused by source.collectIsFocusedAsState()
	Box(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) WhosWatchingStyle.RowFocused else WhosWatchingStyle.PanelRow)
			.border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 11.dp),
	) { content() }
}
