package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.*
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.koin.compose.koinInject

@Composable
internal fun DiscoveryAddGenresScreen(
    draft: DiscoveryLayout,
    account: String?,
    onAdd: (DiscoveryGenre) -> Unit,
    onRestore: (String) -> Unit,
    onDone: () -> Unit,
) {
    val preferences = koinInject<DiscoveryPreferences>()
    val api = koinInject<ApiClient>()
    val users = koinInject<UserRepository>()
    val userId = remember { users.currentUser.value?.id }
    var catalog by remember { mutableStateOf(emptyList<DiscoveryGenre>()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading server genres…") }
    var refresh by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(api, account) {
        while (true) { delay(30_000); refresh++ }
    }
    LaunchedEffect(api, account, refresh) {
        if (account != preferences.accountKey || userId == null) return@LaunchedEffect
        try {
            val next = withContext(Dispatchers.IO) { loadDiscoveryGenres(api, userId) }
            if (account == preferences.accountKey) {
                catalog = next
                status = if (next.isEmpty()) "No movie genres found." else "Choose genres to add. Save your layout after returning."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = "Could not refresh. Your selections are safe. Try Refresh."
        }
    }
    LaunchedEffect(api, account) {
        api.webSocket.subscribe<LibraryChangedMessage>().conflate().collect {
            delay(1_000)
            refresh++
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add genres", fontSize = 26.sp)
        Text("Search movie genres from your server", fontSize = 14.sp)
        SearchTextInput(query, { query = it }, { keyboard?.hide() }, Modifier.fillMaxWidth())
        Text(status, fontSize = 14.sp)
        val matches = catalog.filter { it.title.contains(query.trim(), ignoreCase = true) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (matches.isEmpty() && catalog.isNotEmpty()) item { Text("No matching genres.") }
            items(draft.removedGenres.filter { DiscoveryLayout.sectionTitles.getValue(it).contains(query.trim(), ignoreCase = true) }, key = { it }) { id ->
                Button(onClick = { if (account == preferences.accountKey) onRestore(id) },
                    modifier = Modifier.fillMaxWidth().focusKey("restore-" + id), shape = RoundedCornerShape(8.dp)) {
                    Text(DiscoveryLayout.sectionTitles.getValue(id), modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Text("+ Restore", fontSize = 16.sp)
                }
            }
            items(matches.filterNot { draft.selectedGenreId(it) in draft.removedGenres }, key = { it.id }) { genre ->
                val existing = draft.selectedGenreId(genre)
                val shown = existing != null && existing in draft.order && existing !in draft.hidden
                Button(onClick = {
                    if (account == preferences.accountKey && !shown) onAdd(genre)
                }, modifier = Modifier.fillMaxWidth().focusKey("add-" + genre.id), shape = RoundedCornerShape(8.dp)) {
                    Text(genre.title, modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Text(if (shown) "Added ✓" else "+ Add", fontSize = 16.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { keyboard?.hide(); onDone() }) { Text("Done · Back to layout") }
            Button(onClick = { refresh++ }) { Text("Refresh genres") }
        }
    }
}