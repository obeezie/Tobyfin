package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.WhosWatching
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProfileColors
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.startup.AddProfileViewModel
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Tobyfin's "Who's watching?" picker: the profiles saved on this TV in a centered carousel.
 * Design approved in prototype/whos-watching-prototype.html.
 */
class WhosWatchingFragment : Fragment() {
	private val startupViewModel: StartupViewModel by activityViewModel()
	private val addProfileViewModel: AddProfileViewModel by viewModel()
	private val serverRepository: ServerRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val authenticationStore: AuthenticationStore by inject()
	private val authenticationPreferences: AuthenticationPreferences by inject()

	private data class Profile(val server: Server, val user: PrivateUser, val imageUrl: String?) {
		/** Shown on screen only; sign-in keeps the exact account name. */
		val displayName get() = user.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
	}

	private var profiles by mutableStateOf<List<Profile>>(emptyList())
	private var focus by mutableIntStateOf(0)
	private var opening by mutableStateOf<Int?>(null)
	private var menuFor by mutableStateOf<Profile?>(null)
	private var multipleServers by mutableStateOf(false)

	/** A profile just added from the panel: it grows into the carousel before opening. */
	private var arriving by mutableStateOf<UUID?>(null)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return ComposeView(requireContext()).apply {
			setContent { JellyfinTheme { WhosWatchingScreen() } }
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				addProfileViewModel.saved.collect { user ->
					// The new profile joins the carousel, then opens like choosing any profile.
					val isNew = profiles.none { it.user.id == user.id && it.server.id == user.serverId }
					loadProfiles(keepFocus = true).join()
					val index = profiles.indexOfFirst { it.user.id == user.id && it.server.id == user.serverId }
					if (index < 0) return@collect
					// Someone already on this TV just had their sign-in refreshed: no arrival, no duplicate.
					arriving = user.id.takeIf { isNew }
					focus = index
					delay(ARRIVE_MILLIS)
					choose()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		opening = null
		loadProfiles(keepFocus = profiles.isNotEmpty())
	}

	/** Saved profiles in the order they were added to this TV, across all saved servers. */
	private fun loadProfiles(keepFocus: Boolean) = lifecycleScope.launch {
		serverRepository.loadStoredServers()
		val servers = serverRepository.storedServers.value.associateBy { it.id }
		val loaded = authenticationStore.getServers().flatMap { (serverId, stored) ->
			val server = servers[serverId] ?: return@flatMap emptyList()
			val users = serverUserRepository.getStoredServerUsers(server).associateBy { it.id }
			stored.users.keys.mapNotNull { userId -> users[userId] }
				.map { user -> Profile(server, user, startupViewModel.getUserImage(server, user)) }
		}
		multipleServers = loaded.map { it.server.id }.distinct().size > 1
		profiles = loaded
		// Nobody to pick yet: the add panel opens over the empty picker.
		if (loaded.isEmpty()) {
			focus = 0
			addProfileViewModel.open()
			return@launch
		}
		if (!keepFocus) {
			val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
			focus = WhosWatching.initialFocus(loaded) { it.user.id == lastUserId }
		}
		focus = focus.coerceIn(0, loaded.size)
	}

	private fun move(step: Int) {
		val next = (focus + step).coerceIn(0, profiles.size)
		focus = next
	}

	private fun choose() {
		if (opening != null) return
		val profile = profiles.getOrNull(focus) ?: return addProfile()
		opening = focus
		lifecycleScope.launch {
			// Let the avatar grow into Home before the next screen takes over.
			delay(OPEN_MILLIS)
			signIn(profile)
		}
	}

	private fun signIn(profile: Profile) {
		startupViewModel.authenticate(profile.server, profile.user).onEach { state ->
			when (state) {
				AuthenticatingState, AuthenticatedState -> Unit
				RequireSignInState -> navigate<UserLoginFragment>(createBundle {
					putString(UserLoginFragment.ARG_SERVER_ID, profile.server.id.toString())
					putString(UserLoginFragment.ARG_USERNAME, profile.user.name)
				})
				ServerUnavailableState, is ApiClientErrorLoginState -> {
					opening = null
					Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()
				}
				is ServerVersionNotSupported -> {
					opening = null
					Toast.makeText(
						context,
						getString(R.string.server_issue_outdated_version, state.server.version, ServerRepository.recommendedServerVersion.toString()),
						Toast.LENGTH_LONG
					).show()
				}
			}
		}.launchIn(lifecycleScope)
	}

	private fun addProfile() = addProfileViewModel.open()

	private fun remove(profile: Profile) {
		serverUserRepository.deleteStoredUser(profile.user)
		menuFor = null
		lifecycleScope.launch {
			// This TV forgets a server once none of its profiles are left.
			if (serverUserRepository.getStoredServerUsers(profile.server).isEmpty()) serverRepository.deleteServer(profile.server.id)
			loadProfiles(keepFocus = true)
		}
	}

	private inline fun <reified F : Fragment> navigate(args: Bundle) = requireActivity().supportFragmentManager.commit {
		replace<F>(R.id.content_view, null, args)
		addToBackStack(null)
	}

	@Composable
	private fun WhosWatchingScreen() {
		val focusRequester = remember { FocusRequester() }
		var okHeld by remember { mutableStateOf(false) }
		val opened = opening != null
		val panelOpen = addProfileViewModel.steps.collectAsState().value.isNotEmpty()
		val chromeAlpha by animateFloatAsState(if (opened) 0f else 1f, tween(250), label = "chrome")

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Night)
				.focusRequester(focusRequester)
				.focusable()
				.onPreviewKeyEvent { event ->
					// Swallow the rest of the hold that opened the menu. Remotes keep repeating OK while it
					// is held, and those repeats plus the release would otherwise press the menu's button.
					val code = event.key.nativeKeyCode
					val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
					if (!okHeld || menuFor == null || !isOk) return@onPreviewKeyEvent false
					if (event.type == KeyEventType.KeyUp) okHeld = false
					true
				}
				.onKeyEvent { event ->
					if (menuFor != null || opened || panelOpen) return@onKeyEvent false
					val code = event.key.nativeKeyCode
					val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
					when {
						event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1); true }
						event.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1); true }
						// Holding OK repeats the key: the first repeat opens the profile's menu.
						isOk && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
							if (!okHeld) {
								okHeld = true
								profiles.getOrNull(focus)?.let { menuFor = it }
							}
							true
						}
						isOk && event.type == KeyEventType.KeyUp -> {
							if (okHeld) okHeld = false else choose()
							true
						}
						isOk -> true
						else -> false
					}
				}
		) {
			AmbientGlow()
			Box(
				Modifier
					.fillMaxSize()
					.background(Brush.radialGradient(listOf(Color.Transparent, Color(0xBF03050C)), radius = 1400f))
			)

			Image(
				painter = painterResource(R.drawable.tobyfin_logo),
				contentDescription = stringResource(R.string.app_name),
				modifier = Modifier
					.padding(start = 48.dp, top = 36.dp)
					.width(144.dp)
					.alpha(chromeAlpha),
				contentScale = ContentScale.Fit,
			)

			Text(
				text = stringResource(R.string.whos_watching_title),
				fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 34.sp, color = Color(0xEBEEF0FA),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 92.dp)
					.alpha(chromeAlpha),
			)

			Box(Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.Center) {
				profiles.forEachIndexed { index, profile ->
					key(profile.server.id, profile.user.id) {
						ProfileCard(index, profile.displayName, profile.server.name.takeIf { multipleServers }, arriving = profile.user.id == arriving) {
							ProfileAvatar(profile)
						}
					}
				}
				ProfileCard(profiles.size, stringResource(R.string.whos_watching_add_profile), null) { AddAvatar() }
			}

			menuFor?.let { RemoveMenu(it) }
			if (panelOpen) AddProfilePanel(
				viewModel = addProfileViewModel,
				hasProfiles = profiles.isNotEmpty(),
				userImage = { server, user -> startupViewModel.getUserImage(server, user) },
				onLeave = { requireActivity().finish() },
			)
		}

		LaunchedEffect(menuFor, panelOpen) {
			// The OK release that ended a hold went to the menu, so the next OK press starts fresh.
			if (menuFor == null && !panelOpen) {
				okHeld = false
				focusRequester.requestFocus()
			}
		}
	}

	@Composable
	private fun ProfileCard(index: Int, name: String, serverName: String?, arriving: Boolean = false, avatar: @Composable () -> Unit) {
		val arrival = remember { Animatable(if (arriving) 0.4f else 1f) }
		LaunchedEffect(arriving) { if (arriving) arrival.animateTo(1f, tween(MOVE_MILLIS, easing = SpringySettle)) }
		val d = index - focus
		val focused = d == 0
		val isOpening = opening == index
		val hidden = opening != null && !isOpening
		val x by animateFloatAsState(d * CARD_SPACING, tween(MOVE_MILLIS, easing = SpringySettle), label = "x")
		val scale by animateFloatAsState(
			when {
				isOpening -> 3.2f
				focused -> 1f
				else -> 0.6f
			},
			if (isOpening) tween(OPEN_MILLIS.toInt(), easing = FastOutSlowInEasing) else tween(MOVE_MILLIS, easing = SpringySettle),
			label = "scale",
		)
		val alpha by animateFloatAsState(
			when {
				isOpening || hidden -> 0f
				focused -> 1f
				abs(d) > 2 -> 0f
				else -> 0.38f
			},
			tween(if (isOpening) OPEN_MILLIS.toInt() else MOVE_MILLIS * 3 / 4),
			label = "alpha",
		)
		val ring by animateFloatAsState(if (focused && opening == null) 1f else 0f, tween(300), label = "ring")
		val nameAlpha by animateFloatAsState(
			if (focused && opening == null) 1f else 0f,
			tween(300, delayMillis = if (focused) MOVE_MILLIS * 35 / 100 else 0),
			label = "name",
		)

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(16.dp),
			modifier = Modifier
				.offset(x = x.dp)
				// Fade per draw call: an offscreen layer would clip the growing avatar and its glow to a square.
				.graphicsLayer {
					this.alpha = alpha
					compositingStrategy = CompositingStrategy.ModulateAlpha
				}
		) {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.scale(scale * arrival.value)
					// Room for the glow: while fading, the card is drawn on a layer clipped to its bounds.
					.size(AVATAR + GLOW_ROOM)
					.drawBehind {
						if (ring > 0f) {
							val ringRadius = (AVATAR + 18.dp).toPx() / 2
							drawCircle(
								Brush.radialGradient(listOf(Purple.copy(alpha = .45f * ring), Cyan.copy(alpha = .2f * ring), Color.Transparent), radius = size.minDimension / 2),
								radius = size.minDimension / 2,
							)
							drawCircle(
								Brush.linearGradient(listOf(Purple, Cyan), center - Offset(ringRadius, ringRadius), center + Offset(ringRadius, ringRadius)),
								radius = ringRadius,
								alpha = ring,
							)
						}
					}
			) {
				Box(Modifier.size(AVATAR + 6.dp).clip(CircleShape).background(Night.copy(alpha = ring)), contentAlignment = Alignment.Center) {
					Box(Modifier.size(AVATAR).clip(CircleShape), contentAlignment = Alignment.Center) { avatar() }
				}
			}
			Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = -(GLOW_ROOM / 2 - 9.dp)).alpha(nameAlpha)) {
				Text(name, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White)
				if (serverName != null) Text(serverName, fontSize = 14.sp, color = Muted)
			}
		}
	}

	@Composable
	private fun ProfileAvatar(profile: Profile) {
		var imageFailed by remember(profile.imageUrl) { mutableStateOf(profile.imageUrl == null) }
		Box(
			Modifier.fillMaxSize().background(initialColor(profile.user.name)),
			contentAlignment = Alignment.Center,
		) {
			Text(
				profile.user.name.take(1).uppercase(),
				fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 78.sp, color = Color.White,
			)
			if (!imageFailed) AsyncImage(
				model = profile.imageUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				onError = { imageFailed = true },
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	@Composable
	private fun AddAvatar() {
		Box(
			Modifier
				.fillMaxSize()
				.drawBehind {
					drawCircle(Muted, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
					// Drawn rather than a "+" glyph, whose font metrics sit off the circle's center.
					val arm = 22.dp.toPx()
					val width = 5.dp.toPx()
					drawLine(Muted, center - Offset(arm, 0f), center + Offset(arm, 0f), width, StrokeCap.Round)
					drawLine(Muted, center - Offset(0f, arm), center + Offset(0f, arm), width, StrokeCap.Round)
				},
		)
	}

	@Composable
	private fun RemoveMenu(profile: Profile) {
		val cancelFocus = remember { FocusRequester() }
		OverlayBackHandler { menuFor = null }
		Box(Modifier.fillMaxSize().onBackPress { menuFor = null }.background(Color(0xB804060E)), contentAlignment = Alignment.Center) {
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier
					.width(420.dp)
					.clip(RoundedCornerShape(20.dp))
					.background(Panel)
					.border(BorderStroke(1.dp, Line), RoundedCornerShape(20.dp))
					.padding(28.dp)
			) {
				Text(profile.displayName, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
				Text(stringResource(R.string.whos_watching_remove_summary, profile.displayName), fontSize = 15.sp, color = Muted)
				if (profiles.count { it.server.id == profile.server.id } == 1) {
					Text(stringResource(R.string.whos_watching_remove_last_on_server, profile.server.name), fontSize = 15.sp, color = Muted)
				}
				Button(onClick = { remove(profile) }, modifier = Modifier.fillMaxWidth()) {
					Text(stringResource(R.string.whos_watching_remove))
				}
				Button(onClick = { menuFor = null }, modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus)) {
					Text(stringResource(R.string.lbl_cancel))
				}
			}
		}
		// Start on Cancel so removing a profile always takes a deliberate move and press.
		LaunchedEffect(Unit) { cancelFocus.requestFocus() }
	}

	@Composable
	private fun AmbientGlow() {
		// Two slow drifting color fields; plain gradients rather than a blur, which the Shield draws cheaply.
		val drift by rememberInfiniteTransition(label = "glow").animateFloat(
			0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
		)
		Box(
			Modifier
				.fillMaxSize()
				.drawBehind {
					val w = size.width
					val h = size.height
					drawCircle(
						Brush.radialGradient(listOf(Purple.copy(alpha = .42f), Color.Transparent), Offset(w * (.27f + .06f * drift), h * (.38f + .04f * drift)), w * .42f),
						radius = w * .42f, center = Offset(w * (.27f + .06f * drift), h * (.38f + .04f * drift)),
					)
					drawCircle(
						Brush.radialGradient(listOf(Cyan.copy(alpha = .32f), Color.Transparent), Offset(w * (.74f - .06f * drift), h * (.64f - .05f * drift)), w * .38f),
						radius = w * .38f, center = Offset(w * (.74f - .06f * drift), h * (.64f - .05f * drift)),
					)
				}
		)
	}

	private fun initialColor(name: String) = ProfileColors.forName(name)

	private companion object {
		const val MOVE_MILLIS = 600
		const val OPEN_MILLIS = 420L
		const val ARRIVE_MILLIS = 750L
		const val CARD_SPACING = 230f
		val AVATAR = 180.dp
		val GLOW_ROOM = 120.dp
		// The approved prototype's "springy settle": a slight overshoot as the profile lands.
		val SpringySettle = CubicBezierEasing(0.22f, 1.25f, 0.36f, 1f)
		val Night = WhosWatchingStyle.Night
		val Panel = WhosWatchingStyle.Panel
		val Line = WhosWatchingStyle.Line
		val Muted = WhosWatchingStyle.Muted
		val Purple = WhosWatchingStyle.Purple
		val Cyan = WhosWatchingStyle.Cyan
		val Jakarta = WhosWatchingStyle.Jakarta
	}
}
