package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.preference.DiscoveryLayout
import org.jellyfin.androidtv.preference.DiscoveryMove
import org.jellyfin.androidtv.preference.DiscoveryPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.LocalSettingsBackAction
import org.koin.compose.koinInject

@Composable
fun SettingsDiscoveryScreen() {
    val preferences = koinInject<DiscoveryPreferences>()
    val messages = koinInject<CustomMessageRepository>()
    val account = remember { preferences.accountKey }
    var draft by remember { mutableStateOf(preferences.read()) }
    var move by remember { mutableStateOf<DiscoveryMove?>(null) }
    var addingGenres by remember { mutableStateOf(false) }
    var focusId by remember { mutableStateOf(draft.order.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val router = LocalRouter.current
    val listState = rememberLazyListState()
    val pickupFocus = remember { FocusRequester() }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val backAction = LocalSettingsBackAction.current
    val handleBack by rememberUpdatedState<() -> Unit> {
        if (move != null) move = null else addingGenres = false
    }
    BackHandler(enabled = move != null || addingGenres) { handleBack() }
    DisposableEffect(move != null, addingGenres) {
        backAction.value = if (move != null || addingGenres) ({ handleBack() }) else null
        onDispose { backAction.value = null }
    }

    if (addingGenres) {
        DiscoveryAddGenresScreen(draft, account, onAdd = { genre ->
            draft = draft.addGenre(genre)
            focusId = draft.selectedGenreId(genre) ?: focusId
        }, onRestore = { id -> draft = draft.restoreGenre(id); focusId = id }, onDone = { addingGenres = false })
        return
    }

    val order = move?.order ?: draft.order
    suspend fun revealPosition(position: Int, animate: Boolean) {
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == position }
        if (item == null || item.offset < info.viewportStartOffset || item.offset + item.size > info.viewportEndOffset) {
            val offset = -(info.viewportEndOffset - info.viewportStartOffset) / 3
            if (animate) listState.animateScrollToItem(position, offset)
            else listState.scrollToItem(position, offset)
        }
    }
    LaunchedEffect(move?.id, addingGenres, focusId) {
        if (move != null) {
            pickupFocus.requestFocus()
        } else {
            revealPosition(order.indexOf(focusId).coerceAtLeast(0), animate = false)
            withFrameNanos { }
            rowFocus[focusId]?.requestFocus()
        }
    }
    LaunchedEffect(move?.position) {
        move?.let { revealPosition(it.position, animate = true) }
    }

    fun drop() {
        move?.let { draft = draft.copy(order = it.order); focusId = it.id }
        move = null
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp).onPreviewKeyEvent { event ->
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
                    if (event.type == KeyEventType.KeyUp) move = null
                    true
                }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Customize Discovery", fontSize = 26.sp)
        Text(
            if (move == null) "OK picks up a section. Use ↑ ↓ to choose its position, then OK to drop. Right opens its visibility control."
            else "Moving " + draft.titles.getValue(move!!.id) + "  ·  ↑ ↓ choose position  ·  OK drop  ·  Back undo",
            fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { addingGenres = true }, enabled = move == null) { Text("+ Add genres") }
            Button(onClick = {
                val sizes = listOf("small", "medium", "large")
                draft = draft.copy(size = sizes[(sizes.indexOf(draft.size) + 1) % sizes.size])
            }, enabled = move == null) { Text("Posters: " + draft.size.replaceFirstChar { it.uppercase() }) }
            Button(onClick = { draft = draft.nextRowStyle() }, enabled = move == null) { Text("Layout: " + draft.rowStyleTitle) }
        }
        if (draft.spotlight) Text(
            "Spotlight keeps the selected row on the bottom edge and shows the highlighted title, its details and artwork above it. The next row's name peeks in from below.",
            fontSize = 13.sp,
        )
        Box(Modifier.weight(1f).fillMaxWidth().focusRequester(pickupFocus).focusable(move != null)) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { it }) { id ->
                    val picked = move?.id == id
                    val requester = rowFocus.getOrPut(id) { FocusRequester() }
                    Row(
                        Modifier.fillMaxWidth().animateItem()
                            .border(if (picked) 3.dp else 0.dp, if (picked) Color(0xFF00B7EF) else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(onClick = {
                            if (move == null) { focusId = id; move = DiscoveryMove(id, draft.order) }
                            else if (picked) drop()
                        }, modifier = Modifier.weight(1f).focusRequester(requester).focusKey(id), shape = RoundedCornerShape(8.dp)) {
                            Text((order.indexOf(id) + 1).toString().padStart(2, '0'), fontSize = 16.sp)
                            Spacer(Modifier.width(16.dp))
                            Text(draft.titles.getValue(id), modifier = Modifier.weight(1f), fontSize = 18.sp)
                            Text(if (picked) "↕ Drop here" else if (id in draft.hidden) "Hidden" else "↕ Move", fontSize = 14.sp)
                        }
                        Button(onClick = {
                            draft = draft.copy(hidden = if (id in draft.hidden) draft.hidden - id else draft.hidden + id)
                        }, enabled = move == null) { Text(if (id in draft.hidden) "Show" else "Hide") }
                        if (id.startsWith("genre:")) {
                            Button(onClick = {
                                draft = draft.removeGenre(id)
                                focusId = draft.order.first()
                            }, enabled = move == null) { Text("Remove") }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFFAAAA), fontSize = 14.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (preferences.save(draft, account)) {
                    messages.pushMessage(CustomMessage.DiscoveryLayoutChanged(preferences.signature()))
                    router.back()
                } else error = "Unable to save. Reopen Customize Discovery and check the signed-in account."
            }, enabled = move == null, modifier = Modifier.focusKey("discovery-save")) { Text("Save changes") }
            Button(onClick = { router.back() }, enabled = move == null) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { draft = DiscoveryLayout(); focusId = draft.order.first() }, enabled = move == null) { Text("Restore defaults") }
        }
    }
}