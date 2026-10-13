package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.preference.DiscoveryGenre
import org.jellyfin.androidtv.preference.DiscoveryLayout
import org.jellyfin.androidtv.preference.DiscoveryMove
import org.jellyfin.androidtv.preference.DiscoveryPreferences
import org.jellyfin.androidtv.preference.loadDiscoveryGenres
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.MovieDiscoveryRows
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.settings.composable.LocalSettingsBackAction
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle
import org.jellyfin.androidtv.ui.startup.fragment.onBackPress
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.koin.compose.koinInject

private val Cyan = WhosWatchingStyle.Cyan
private val Muted = WhosWatchingStyle.Muted
private val Faint = Color(0xFF6B7299)

private sealed interface Overlay {
	data class RowMenu(val id: String) : Overlay
	data object ConfirmReset : Overlay
}

/**
 * Customize Discovery on the Shield: On Home on the left (OK picks a row up to move it), Add to Home on the right.
 * Changes save as they are made, with Undo. Design approved in prototype/customize-discovery-prototype.html.
 */
@Composable
fun SettingsDiscoveryScreen() {
	val preferences = koinInject<DiscoveryPreferences>()
	val messages = koinInject<CustomMessageRepository>()
	val api = koinInject<ApiClient>()
	val users = koinInject<UserRepository>()
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	val nextUpMaxDays by rememberPreference(userPreferences, UserPreferences.homeNextUpMaxDays)
	val nextUpCutoff = getNextUpCutoffOptions().firstOrNull { it.first == nextUpMaxDays }?.takeIf { it.first > 0 }?.second
	val account = remember { preferences.accountKey }
	val userId = remember { users.currentUser.value?.id }
	var layout by remember { mutableStateOf(preferences.read()) }
	val history = remember { mutableStateListOf<DiscoveryLayout>() }
	var move by remember { mutableStateOf<DiscoveryMove?>(null) }
	var overlay by remember { mutableStateOf<Overlay?>(null) }
	var status by remember { mutableStateOf<String?>(null) }
	var statusTick by remember { mutableIntStateOf(0) }
	var catalog by remember { mutableStateOf(emptyList<DiscoveryGenre>()) }
	var query by remember { mutableStateOf("") }
	var refresh by remember { mutableIntStateOf(0) }
	var focusTarget by remember { mutableStateOf<String?>(layout.onHome.firstOrNull()) }
	val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
	val addFocus = remember { mutableMapOf<String, FocusRequester>() }
	val pickupFocus = remember { FocusRequester() }
	val leftList = rememberLazyListState()
	val keyboard = LocalSoftwareKeyboardController.current
	val backAction = LocalSettingsBackAction.current

	fun say(message: String) {
		status = message
		statusTick++
	}

	/** Apply a change and save it straight away. */
	fun change(next: DiscoveryLayout, message: String) {
		if (!preferences.save(next, account)) return say("Couldn't save. The signed-in account changed; reopen Customize Discovery.")
		history += layout
		layout = next
		messages.pushMessage(CustomMessage.DiscoveryLayoutChanged(preferences.signature()))
		say(message)
	}

	fun undo() {
		val previous = history.removeLastOrNull() ?: return
		if (!preferences.save(previous, account)) return say("Couldn't save. The signed-in account changed; reopen Customize Discovery.")
		layout = previous
		messages.pushMessage(CustomMessage.DiscoveryLayoutChanged(preferences.signature()))
		say("Undone")
	}

	fun drop() {
		val picked = move ?: return
		move = null
		val from = layout.onHome.indexOf(picked.id)
		if (picked.position != from) {
			change(layout.moveOnHome(picked.id, picked.position), "Moved ${layout.titles[picked.id]} to position ${picked.position + 1}")
		}
		focusTarget = picked.id
	}

	// Back steps out of moving, a menu or the reset question before leaving the screen.
	val handleBack by rememberUpdatedState<() -> Unit> {
		when {
			move != null -> { focusTarget = move?.id; move = null }
			overlay != null -> overlay = null
		}
	}
	val busy = move != null || overlay != null
	BackHandler(enabled = busy) { handleBack() }
	DisposableEffect(busy) {
		backAction.value = if (busy) ({ handleBack() }) else null
		onDispose { backAction.value = null }
	}

	// The server's genres load in the background and refresh every 30 seconds and when the library changes.
	LaunchedEffect(api, account) {
		while (true) {
			delay(30_000)
			refresh++
		}
	}
	LaunchedEffect(api, account, refresh) {
		if (account != preferences.accountKey || userId == null) return@LaunchedEffect
		try {
			val next = withContext(Dispatchers.IO) { loadDiscoveryGenres(api, userId) }
			if (account == preferences.accountKey) catalog = next
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Exception) {
			say("Couldn't load your server's genres. Trying again shortly.")
		}
	}
	LaunchedEffect(api, account) {
		api.webSocket.subscribe<LibraryChangedMessage>().conflate().collect {
			delay(1_000)
			refresh++
		}
	}
	LaunchedEffect(statusTick) {
		if (status == null) return@LaunchedEffect
		delay(3_000)
		status = null
	}

	val onHome = move?.order ?: layout.onHome
	val addRows = layout.addToHomeRows(catalog, query)
	val serverGenres = catalog.map { it.title }

	// Keep focus on the row being worked on, or its neighbour when it leaves the list.
	LaunchedEffect(focusTarget, move == null, overlay == null) {
		if (move != null) {
			pickupFocus.requestFocus()
			return@LaunchedEffect
		}
		if (overlay != null) return@LaunchedEffect
		val target = focusTarget ?: return@LaunchedEffect
		withFrameNanos { }
		(rowFocus[target] ?: addFocus[target])?.requestFocus()
	}
	LaunchedEffect(move?.position) {
		move?.let { leftList.animateScrollToItem(it.position, -120) }
	}

	Box(
		Modifier
			.fillMaxSize()
			.background(Brush.linearGradient(listOf(Color(0xFF14123A), WhosWatchingStyle.Night, Color(0xFF071624))))
			.onPreviewKeyEvent { event ->
				val picked = move ?: return@onPreviewKeyEvent false
				when (event.key) {
					Key.DirectionUp, Key.DirectionDown -> {
						if (event.type == KeyEventType.KeyDown) move = picked.step(if (event.key == Key.DirectionUp) -1 else 1)
						true
					}
					Key.DirectionLeft, Key.DirectionRight -> true
					Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
						if (event.type == KeyEventType.KeyUp) drop()
						true
					}
					Key.Back, Key.Escape -> {
						if (event.type == KeyEventType.KeyUp) handleBack()
						true
					}
					else -> false
				}
			}
			.padding(horizontal = 40.dp, vertical = 28.dp),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Customize Discovery", fontFamily = WhosWatchingStyle.Jakarta, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
				Spacer(Modifier.weight(1f))
				status?.let { Text(it, fontSize = 15.sp, color = Muted, modifier = Modifier.padding(end = 16.dp)) }
				Pill(if (history.isEmpty()) "↶ Undo" else "↶ Undo", enabled = history.isNotEmpty() && !busy, onClick = ::undo)
			}

			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				Label("Poster size")
				listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (value, title) ->
					SizeOption(title, value, selected = layout.size == value, enabled = !busy) {
						if (layout.size != value) change(layout.copy(size = value), "Poster size: $title")
					}
				}
				// Moved here from Settings > Customization > Home, which Discovery replaces.
				Spacer(Modifier.weight(1f))
				Label("Up Next shows")
				Pill(nextUpCutoff?.let { "Last $it" } ?: "Any time", enabled = !busy) { router.push(Routes.HOME_NEXT_UP_CUTOFF) }
			}

			Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
				// On Home
				Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Heading("On Home", if (move == null) "${onHome.size} rows · OK to pick up and move" else "▲▼ move · OK drop · Back puts it back")
					Box(Modifier.weight(1f).fillMaxWidth().focusRequester(pickupFocus).focusable(move != null)) {
						LazyColumn(state = leftList, verticalArrangement = Arrangement.spacedBy(7.dp)) {
							items(onHome, key = { it }) { id ->
								OnHomeRow(
									number = onHome.indexOf(id) + 1,
									title = layout.titles[id] ?: id,
									builtIn = DiscoveryLayout.isBuiltIn(id),
									empty = MovieDiscoveryRows.hasNothingToShow(id, serverGenres),
									held = move?.id == id,
									enabled = !busy,
									focusRequester = rowFocus.getOrPut(id) { FocusRequester() },
									modifier = Modifier.animateItem(),
									onPickUp = { move = DiscoveryMove(id, layout.onHome) },
									onTakeOff = {
										val index = layout.onHome.indexOf(id)
										change(layout.takeOff(id), "${layout.titles[id]} taken off Home")
										focusTarget = layout.onHome.getOrNull(index) ?: layout.onHome.lastOrNull()
									},
									onMenu = { overlay = Overlay.RowMenu(id) },
								)
							}
						}
					}
					Pill("Reset to default", enabled = !busy, wide = true) { overlay = Overlay.ConfirmReset }
				}

				// Add to Home
				Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Heading("Add to Home", "OK to add")
					SearchTextInput(query, { query = it }, { keyboard?.hide() }, Modifier.fillMaxWidth())
					LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
						if (addRows.isEmpty()) item {
							Text(if (query.isBlank()) "Every row is On Home." else "No matching rows.", fontSize = 15.sp, color = Muted)
						}
						items(addRows, key = { it.id }) { row ->
							AddRow(
								title = row.title,
								builtIn = DiscoveryLayout.isBuiltIn(row.id),
								empty = MovieDiscoveryRows.hasNothingToShow(row.id, serverGenres),
								enabled = !busy,
								focusRequester = addFocus.getOrPut(row.id) { FocusRequester() },
								modifier = Modifier.animateItem(),
							) {
								val index = addRows.indexOf(row)
								change(row.genre?.let(layout::putOn) ?: layout.putOn(row.id), "${row.title} added to the end of On Home")
								focusTarget = layout.addToHomeRows(catalog, query).getOrNull(index)?.id
									?: layout.addToHomeRows(catalog, query).lastOrNull()?.id
							}
						}
					}
				}
			}
		}

		when (val shown = overlay) {
			is Overlay.RowMenu -> Dialog(onDismiss = { overlay = null; focusTarget = shown.id }) {
				Text(layout.titles[shown.id] ?: shown.id, fontFamily = WhosWatchingStyle.Jakarta, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
				DialogButton("Move to top", first = true) {
					overlay = null
					change(layout.moveOnHome(shown.id, 0), "${layout.titles[shown.id]} moved to the top")
					focusTarget = shown.id
				}
				DialogButton("Take off Home") {
					overlay = null
					val index = layout.onHome.indexOf(shown.id)
					change(layout.takeOff(shown.id), "${layout.titles[shown.id]} taken off Home")
					focusTarget = layout.onHome.getOrNull(index) ?: layout.onHome.lastOrNull()
				}
				DialogButton("Cancel", quiet = true) { overlay = null; focusTarget = shown.id }
			}
			Overlay.ConfirmReset -> Dialog(onDismiss = { overlay = null }) {
				Text("Reset Discovery?", fontFamily = WhosWatchingStyle.Jakarta, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
				Text("Home goes back to the default rows and poster size. Your added genres move to Add to Home.", fontSize = 15.sp, color = Muted)
				DialogButton("Cancel", first = true) { overlay = null }
				DialogButton("Reset", danger = true) {
					overlay = null
					change(DiscoveryLayout(), "Reset to the default rows")
					focusTarget = layout.onHome.firstOrNull()
				}
			}
			null -> Unit
		}
	}
}

// Building blocks, in Tobyfin's style

@Composable
private fun focusState(): Pair<MutableInteractionSource, Boolean> {
	val source = remember { MutableInteractionSource() }
	val focused by source.collectIsFocusedAsState()
	return source to focused
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp, color = Muted)

@Composable
private fun Heading(title: String, hint: String) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
		Text(title, fontFamily = WhosWatchingStyle.Jakarta, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
		Spacer(Modifier.weight(1f))
		Text(hint, fontSize = 13.sp, color = Muted)
	}
}

@Composable
private fun Pill(text: String, enabled: Boolean, wide: Boolean = false, onClick: () -> Unit) {
	val (source, focused) = focusState()
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.then(if (wide) Modifier.fillMaxWidth() else Modifier)
			.clip(RoundedCornerShape(50))
			.background(if (focused) Color.White else WhosWatchingStyle.PanelRow)
			.border(1.dp, if (wide && !focused) WhosWatchingStyle.Line else Color.Transparent, RoundedCornerShape(50))
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
			.padding(horizontal = 18.dp, vertical = 9.dp),
	) {
		Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (focused) WhosWatchingStyle.Night else if (enabled) Muted else Faint)
	}
}

@Composable
private fun SizeOption(title: String, value: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
	val (source, focused) = focusState()
	val (count, width, height) = when (value) {
		"small" -> Triple(3, 7, 11)
		"large" -> Triple(1, 14, 21)
		else -> Triple(2, 10, 15)
	}
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier
			.clip(RoundedCornerShape(10.dp))
			.background(if (focused) Color.White else WhosWatchingStyle.PanelRow)
			.border(2.dp, if (selected && !focused) Cyan else Color.Transparent, RoundedCornerShape(10.dp))
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 7.dp),
	) {
		Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(21.dp)) {
			repeat(count) {
				Box(Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(2.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B4A8A), Color(0xFF222B55)))))
			}
		}
		Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (focused) WhosWatchingStyle.Night else Color.White)
	}
}

@Composable
private fun Tags(builtIn: Boolean, empty: Boolean) {
	if (builtIn) {
		Text(
			"Built-in", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC7D3FF),
			modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF23305E)).padding(horizontal = 8.dp, vertical = 2.dp),
		)
	}
	if (empty) Text("Nothing to show yet", fontSize = 12.sp, color = Faint)
}

@Composable
private fun OnHomeRow(
	number: Int,
	title: String,
	builtIn: Boolean,
	empty: Boolean,
	held: Boolean,
	enabled: Boolean,
	focusRequester: FocusRequester,
	modifier: Modifier,
	onPickUp: () -> Unit,
	onTakeOff: () -> Unit,
	onMenu: () -> Unit,
) {
	val (bodySource, bodyFocused) = focusState()
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		modifier = modifier
			.fillMaxWidth()
			.scale(if (held) 1.02f else 1f)
			.clip(RoundedCornerShape(12.dp))
			.background(if (held) Color(0xFF1B2652) else if (bodyFocused) WhosWatchingStyle.RowFocused else WhosWatchingStyle.Panel)
			.border(2.dp, if (held) Cyan else if (bodyFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
			.padding(start = 6.dp, end = 8.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier
				.weight(1f)
				.focusRequester(focusRequester)
				.focusKey("onhome-$title")
				.clickable(interactionSource = bodySource, indication = null, enabled = enabled, onClick = onPickUp)
				.padding(vertical = 10.dp),
		) {
			Text(number.toString(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Faint, modifier = Modifier.width(24.dp))
			Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
			Tags(builtIn, empty)
		}
		if (held) {
			Text("▲▼ move · OK drop", fontSize = 13.sp, color = Cyan, modifier = Modifier.padding(end = 6.dp))
		} else {
			SwitchButton(enabled, onTakeOff)
			MoreButton(enabled, onMenu)
		}
	}
}

@Composable
private fun SwitchButton(enabled: Boolean, onClick: () -> Unit) {
	val (source, focused) = focusState()
	Box(
		contentAlignment = Alignment.CenterEnd,
		modifier = Modifier
			.border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
			.padding(3.dp)
			.size(40.dp, 22.dp)
			.clip(RoundedCornerShape(50))
			.background(Brush.horizontalGradient(listOf(WhosWatchingStyle.Purple, Cyan)))
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
			.padding(3.dp),
	) {
		Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
	}
}

@Composable
private fun MoreButton(enabled: Boolean, onClick: () -> Unit) {
	val (source, focused) = focusState()
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.size(34.dp)
			.clip(CircleShape)
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
	) {
		Text("⋯", fontSize = 18.sp, color = if (focused) WhosWatchingStyle.Night else Muted)
	}
}

@Composable
private fun AddRow(
	title: String,
	builtIn: Boolean,
	empty: Boolean,
	enabled: Boolean,
	focusRequester: FocusRequester,
	modifier: Modifier,
	onAdd: () -> Unit,
) {
	val (source, focused) = focusState()
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		modifier = modifier
			.fillMaxWidth()
			.focusRequester(focusRequester)
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) WhosWatchingStyle.RowFocused else Color.Transparent)
			.border(if (focused) 2.dp else 1.dp, if (focused) Color.White else WhosWatchingStyle.Line, RoundedCornerShape(12.dp))
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onAdd)
			.padding(horizontal = 14.dp, vertical = 10.dp),
	) {
		Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
		Tags(builtIn, empty)
		Spacer(Modifier.weight(1f))
		Text("+", fontSize = 20.sp, color = Cyan)
	}
}

@Composable
private fun Dialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier.fillMaxSize().onBackPress(onDismiss).background(Color(0xB804060E)),
	) {
		Column(
			verticalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier
				.width(380.dp)
				.clip(RoundedCornerShape(18.dp))
				.background(WhosWatchingStyle.Panel)
				.border(1.dp, WhosWatchingStyle.Line, RoundedCornerShape(18.dp))
				.padding(24.dp),
		) { content() }
	}
}

@Composable
private fun DialogButton(text: String, first: Boolean = false, danger: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) {
	val (source, focused) = focusState()
	val requester = remember { FocusRequester() }
	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.fillMaxWidth()
			.focusRequester(requester)
			.clip(RoundedCornerShape(12.dp))
			.background(
				when {
					focused && danger -> WhosWatchingStyle.Danger
					focused -> Color.White
					quiet -> Color.Transparent
					else -> WhosWatchingStyle.PanelRow
				}
			)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(vertical = 12.dp),
	) {
		Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (focused) WhosWatchingStyle.Night else if (quiet) Muted else Color.White)
	}
	if (first) LaunchedEffect(Unit) { requester.requestFocus() }
}
