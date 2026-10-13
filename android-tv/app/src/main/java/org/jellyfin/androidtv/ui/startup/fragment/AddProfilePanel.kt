package org.jellyfin.androidtv.ui.startup.fragment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.PublicUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.ProfileInitial
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.startup.AddProfileProblem
import org.jellyfin.androidtv.ui.startup.AddProfileStep
import org.jellyfin.androidtv.ui.startup.AddProfileViewModel
import org.jellyfin.androidtv.ui.startup.WhosWatchingServers
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Cyan
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Danger
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Field
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Jakarta
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Line
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Muted
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Panel
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.PanelRow
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Purple
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.RowFocused
import org.jellyfin.androidtv.ui.startup.fragment.WhosWatchingStyle.Scrim

/**
 * The add-profile panel: a card over the dimmed "Who's watching?" whose contents change in place.
 * Design approved in prototype/add-profile-prototype.html.
 */
@Composable
internal fun AddProfilePanel(
	viewModel: AddProfileViewModel,
	hasProfiles: Boolean,
	userImage: (Server, PublicUser) -> String?,
	onLeave: () -> Unit,
) {
	val steps by viewModel.steps.collectAsState()
	val step = steps.lastOrNull() ?: return
	var typing by remember { mutableStateOf(false) }

	// Back steps back one card. On the first card it closes the panel, or leaves Tobyfin when there is nothing to pick.
	val onBack: () -> Unit = {
		if (!viewModel.back()) {
			if (hasProfiles) viewModel.close() else onLeave()
		}
	}
	OverlayBackHandler(onBack)

	// Fade the scrim and raise the card once, when the panel opens.
	val appear = remember { Animatable(0f) }
	LaunchedEffect(Unit) { appear.animateTo(1f, tween(380, easing = CubicBezierEasing(0.22f, 1.15f, 0.36f, 1f))) }
	// The TV keyboard covers the bottom of the screen, so the card moves up while typing.
	val top by animateDpAsState(if (typing) 48.dp else 0.dp, tween(300), label = "top")

	Box(Modifier.fillMaxSize().onBackPress(onBack).alpha(appear.value.coerceIn(0f, 1f)).background(Scrim)) {
		Column(
			verticalArrangement = Arrangement.spacedBy(14.dp),
			modifier = Modifier
				.align(if (typing) Alignment.TopCenter else Alignment.Center)
				.padding(top = top)
				.offset(y = ((1f - appear.value) * 40).dp)
				.width(640.dp)
				.heightIn(max = 600.dp)
				.clip(RoundedCornerShape(24.dp))
				.background(Panel)
				.border(1.dp, Line, RoundedCornerShape(24.dp))
				.padding(horizontal = 36.dp, vertical = 32.dp)
		) {
			AnimatedContent(
				targetState = steps.size to step,
				contentKey = { (depth, step) -> depth to step::class },
				transitionSpec = {
					val forward = targetState.first >= initialState.first
					(fadeIn(tween(280)) + slideInHorizontally(tween(280)) { if (forward) it / 14 else -it / 14 }) togetherWith fadeOut(tween(120))
				},
				label = "step",
			) { (_, shown) ->
				Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
					when (shown) {
						AddProfileStep.Servers -> ServersStep(viewModel, hasProfiles)
						is AddProfileStep.Address -> AddressStep(shown, viewModel) { typing = it }
						is AddProfileStep.Connecting -> ConnectingStep(shown, viewModel)
						is AddProfileStep.QuickConnect -> QuickConnectStep(shown, viewModel)
						is AddProfileStep.Users -> UsersStep(shown, viewModel, userImage)
						is AddProfileStep.Password -> PasswordStep(shown, viewModel) { typing = it }
						is AddProfileStep.Problem -> ProblemStep(shown, viewModel)
					}
				}
			}
		}
	}

	// Typing ends when the card that has the text field goes away.
	LaunchedEffect(step::class) { if (step !is AddProfileStep.Address && step !is AddProfileStep.Password) typing = false }
}

@Composable
private fun ColumnScope.ServersStep(viewModel: AddProfileViewModel, hasProfiles: Boolean) {
	val saved by viewModel.savedServers.collectAsState()
	val found by viewModel.foundServers.collectAsState()
	val scanning by viewModel.scanning.collectAsState()
	val first = remember { FocusRequester() }

	Eyebrow(stringResource(R.string.add_profile_eyebrow))
	Title(AnnotatedString(stringResource(if (hasProfiles) R.string.add_profile_which_server else R.string.add_profile_welcome)))
	if (!hasProfiles) Body(stringResource(R.string.add_profile_welcome_summary))

	// Long lists scroll inside the card; focus keeps the highlighted row in view.
	Column(
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
	) {
		if (saved.isNotEmpty()) {
			Group(stringResource(R.string.add_profile_on_this_tv))
			saved.forEachIndexed { index, server ->
				PanelRow(
					icon = "◉", title = server.name, subtitle = server.address.displayAddress(),
					onClick = { viewModel.chooseSavedServer(server) },
					modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
				)
			}
		}
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Group(stringResource(R.string.add_profile_found_on_network))
			if (scanning) CircularProgressIndicator(Modifier.size(14.dp), color = Cyan)
		}
		when {
			found.isNotEmpty() -> found.forEach { server ->
				PanelRow(
					icon = "◉", title = server.name,
					subtitle = stringResource(R.string.add_profile_new_server, server.address.displayAddress()),
					onClick = { viewModel.chooseFoundServer(server) },
				)
			}
			scanning -> Body(stringResource(R.string.add_profile_scanning))
			else -> Body(stringResource(R.string.add_profile_none_found))
		}
		PanelRow(
			icon = "⌨", title = stringResource(R.string.add_profile_enter_address),
			subtitle = stringResource(R.string.add_profile_enter_address_summary),
			onClick = viewModel::enterAddress,
			modifier = Modifier.padding(top = 4.dp).then(if (saved.isEmpty()) Modifier.focusRequester(first) else Modifier),
		)
	}
	LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
private fun AddressStep(step: AddProfileStep.Address, viewModel: AddProfileViewModel, onTyping: (Boolean) -> Unit) {
	val field = remember { FocusRequester() }
	Eyebrow(stringResource(R.string.add_profile_eyebrow_new_server))
	Title(AnnotatedString(stringResource(R.string.add_profile_address_title)))
	PanelTextField(
		value = step.text,
		onValueChange = viewModel::updateAddress,
		placeholder = stringResource(R.string.add_profile_address_hint),
		keyboardType = KeyboardType.Uri,
		imeAction = ImeAction.Go,
		onDone = viewModel::submitAddress,
		onTyping = onTyping,
		modifier = Modifier.focusRequester(field),
	)
	Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		listOf("https://", ".com", ".net", ":8096").forEach { shortcut ->
			Chip(shortcut) {
				viewModel.updateAddress(
					if (shortcut == "https://") "https://" + step.text.removePrefix("https://").removePrefix("http://")
					else step.text + shortcut
				)
			}
		}
	}
	Body(stringResource(R.string.add_profile_address_help))
	PanelButton(stringResource(R.string.add_profile_continue), onClick = viewModel::submitAddress)
	// Starting on the field brings up the TV keyboard straight away.
	LaunchedEffect(Unit) { field.requestFocus() }
}

@Composable
private fun ConnectingStep(step: AddProfileStep.Connecting, viewModel: AddProfileViewModel) {
	val cancel = remember { FocusRequester() }
	Eyebrow(stringResource(R.string.add_profile_eyebrow_new_server))
	Title(AnnotatedString(stringResource(R.string.add_profile_connecting)))
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
		CircularProgressIndicator(Modifier.size(24.dp), color = Cyan)
		Body(stringResource(R.string.add_profile_connecting_to, step.address.displayAddress()))
	}
	PanelButton(stringResource(R.string.lbl_cancel), quiet = true, onClick = { viewModel.back() }, modifier = Modifier.focusRequester(cancel))
	LaunchedEffect(Unit) { cancel.requestFocus() }
}

@Composable
private fun QuickConnectStep(step: AddProfileStep.QuickConnect, viewModel: AddProfileViewModel) {
	val password = remember { FocusRequester() }
	Eyebrow(stringResource(R.string.add_profile_eyebrow_quick_connect))
	Title(emphasized(stringResource(R.string.add_profile_sign_in_to, step.server.name), step.server.name))
	Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier.size(196.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
		) {
			if (step.code != null) QrCode(WhosWatchingServers.quickConnectUrl(step.server.address, step.code), Modifier.fillMaxSize())
			else CircularProgressIndicator(Modifier.size(32.dp), color = WhosWatchingStyle.Night)
		}
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text(
				step.code?.chunked(3)?.joinToString(" ") ?: "··· ···",
				fontFamily = FontFamily.Monospace, fontSize = 60.sp, letterSpacing = 4.sp, color = Color.White,
			)
			Body("1. " + stringResource(R.string.add_profile_qc_scan))
			Body("2. " + stringResource(R.string.add_profile_qc_enter))
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				CircularProgressIndicator(Modifier.size(14.dp), color = Cyan)
				Body(stringResource(if (step.code == null) R.string.add_profile_qc_starting else R.string.add_profile_qc_waiting), size = 14)
			}
		}
	}
	PanelButton(stringResource(R.string.add_profile_use_password), quiet = true, onClick = viewModel::usePassword, modifier = Modifier.focusRequester(password))
	LaunchedEffect(Unit) { password.requestFocus() }
}

@Composable
private fun UsersStep(step: AddProfileStep.Users, viewModel: AddProfileViewModel, userImage: (Server, PublicUser) -> String?) {
	val first = remember { FocusRequester() }
	Eyebrow(stringResource(R.string.add_profile_eyebrow_password))
	Title(emphasized(stringResource(R.string.add_profile_who_are_you, step.server.name), step.server.name))
	val users = step.users
	if (users == null) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			CircularProgressIndicator(Modifier.size(24.dp), color = Cyan)
			Body(stringResource(R.string.add_profile_loading_users))
		}
		return
	}
	Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
		users.take(5).forEachIndexed { index, user ->
			UserTile(user.name, userImage(step.server, user), onClick = { viewModel.chooseUser(user.name) },
				modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
		}
		UserTile(stringResource(R.string.add_profile_someone_else), null, other = true, onClick = { viewModel.chooseUser(null) })
	}
	LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
private fun PasswordStep(step: AddProfileStep.Password, viewModel: AddProfileViewModel, onTyping: (Boolean) -> Unit) {
	val nameField = remember { FocusRequester() }
	val passwordField = remember { FocusRequester() }
	var username by remember { mutableStateOf(step.username.orEmpty()) }
	var password by remember { mutableStateOf("") }
	val submit = { viewModel.signInWithPassword(username, password) }

	Eyebrow(stringResource(R.string.add_profile_eyebrow_password))
	Title(
		if (step.username != null) emphasized(stringResource(R.string.add_profile_password_for, step.username), step.username)
		else emphasized(stringResource(R.string.add_profile_sign_in_to, step.server.name), step.server.name)
	)
	if (step.wrongPassword) Problem(stringResource(R.string.add_profile_wrong_password), stringResource(R.string.add_profile_wrong_password_summary))
	if (step.username == null) {
		Label(stringResource(R.string.add_profile_username))
		PanelTextField(
			value = username, onValueChange = { username = it }, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
			onDone = { passwordField.requestFocus() }, onTyping = onTyping, modifier = Modifier.focusRequester(nameField),
		)
	}
	Label(stringResource(R.string.add_profile_password))
	PanelTextField(
		value = password, onValueChange = { password = it }, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
		onDone = submit, onTyping = onTyping, password = true, modifier = Modifier.focusRequester(passwordField),
	)
	PanelButton(stringResource(if (step.signingIn) R.string.add_profile_signing_in else R.string.add_profile_sign_in), onClick = submit)
	LaunchedEffect(step.wrongPassword) {
		if (step.wrongPassword) password = ""
		if (step.username == null && username.isEmpty()) nameField.requestFocus() else passwordField.requestFocus()
	}
}

@Composable
private fun ProblemStep(step: AddProfileStep.Problem, viewModel: AddProfileViewModel) {
	val retry = remember { FocusRequester() }
	val (title, detail) = when (val kind = step.kind) {
		is AddProfileProblem.Unreachable -> {
			val host = kind.address.displayAddress()
			stringResource(R.string.add_profile_unreachable, host) to buildString {
				append(stringResource(R.string.add_profile_unreachable_summary))
				if (kind.tried.isNotEmpty()) append("\n").append(stringResource(R.string.add_profile_unreachable_tried, kind.tried.joinToString(", ")))
			}
		}
		is AddProfileProblem.TooOld -> stringResource(R.string.add_profile_too_old, kind.server.name) to
			stringResource(R.string.add_profile_too_old_summary, kind.server.version.orEmpty(), ServerRepository.minimumServerVersion.toString())
		is AddProfileProblem.ServerOffline -> stringResource(R.string.add_profile_unreachable, kind.server.name) to
			stringResource(R.string.add_profile_offline_summary)
		is AddProfileProblem.SignInFailed -> stringResource(R.string.add_profile_failed, kind.server.name) to
			stringResource(R.string.add_profile_failed_summary)
	}
	Eyebrow(stringResource(R.string.add_profile_eyebrow))
	Problem(title, detail)
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		PanelButton(stringResource(R.string.add_profile_try_again), onClick = step.retry, modifier = Modifier.weight(1f).focusRequester(retry))
		PanelButton(stringResource(R.string.add_profile_back), onClick = { viewModel.back() }, modifier = Modifier.weight(1f))
	}
	LaunchedEffect(Unit) { retry.requestFocus() }
}

// Building blocks

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp, color = Muted)

@Composable
private fun Title(text: AnnotatedString) = Text(text, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.White, lineHeight = 36.sp)

@Composable
private fun Body(text: String, size: Int = 17) = Text(text, fontSize = size.sp, color = Muted, lineHeight = (size * 1.45f).sp)

@Composable
private fun Group(text: String) = Text(text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp, color = Muted)

@Composable
private fun Label(text: String) = Text(text, fontSize = 14.sp, color = Muted)

/** [text] with [part] in the picker's purple-to-cyan gradient. */
private fun emphasized(text: String, part: String): AnnotatedString = buildAnnotatedString {
	val start = text.indexOf(part)
	if (start < 0) return@buildAnnotatedString append(text)
	append(text.substring(0, start))
	withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Purple, Cyan)))) { append(part) }
	append(text.substring(start + part.length))
}

@Composable
private fun focusState(): Pair<MutableInteractionSource, Boolean> {
	val source = remember { MutableInteractionSource() }
	val focused by source.collectIsFocusedAsState()
	return source to focused
}

@Composable
private fun PanelRow(icon: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
	val (source, focused) = focusState()
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.background(if (focused) RowFocused else PanelRow)
			.border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 18.dp, vertical = 12.dp),
	) {
		Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF222C55)), contentAlignment = Alignment.Center) {
			Text(icon, fontSize = 18.sp, color = Color.White)
		}
		Column(Modifier.weight(1f)) {
			Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
			Text(subtitle, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Muted, maxLines = 1)
		}
		Text("›", fontSize = 22.sp, color = Muted)
	}
}

@Composable
private fun PanelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false) {
	val (source, focused) = focusState()
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.background(
				when {
					focused -> Color.White
					quiet -> Color.Transparent
					else -> PanelRow
				}
			)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(vertical = 14.dp),
	) {
		Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (focused) WhosWatchingStyle.Night else if (quiet) Muted else Color.White)
	}
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
	val (source, focused) = focusState()
	Box(
		modifier = Modifier
			.clip(RoundedCornerShape(50))
			.background(if (focused) Color.White else PanelRow)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 8.dp),
	) {
		Text(text, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = if (focused) WhosWatchingStyle.Night else Color.White)
	}
}

@Composable
private fun UserTile(name: String, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, other: Boolean = false) {
	val (source, focused) = focusState()
	var imageFailed by remember(imageUrl) { mutableStateOf(imageUrl == null) }
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier
			.clip(RoundedCornerShape(18.dp))
			.background(if (focused) RowFocused else Color.Transparent)
			.border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(18.dp))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(8.dp),
	) {
		Box(Modifier.size(84.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
			if (other) Box(Modifier.fillMaxSize().background(Color(0xFF222C55)), contentAlignment = Alignment.Center) {
				Text("?", fontSize = 30.sp, color = Muted, fontWeight = FontWeight.Bold)
			} else {
				ProfileInitial(name, Modifier.fillMaxSize())
				if (!imageFailed) AsyncImage(
					model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
					onError = { imageFailed = true }, modifier = Modifier.fillMaxSize(),
				)
			}
		}
		Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
	}
}

@Composable
private fun PanelTextField(
	value: String,
	onValueChange: (String) -> Unit,
	keyboardType: KeyboardType,
	imeAction: ImeAction,
	onDone: () -> Unit,
	onTyping: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String = "",
	password: Boolean = false,
) {
	var focused by remember { mutableStateOf(false) }
	BasicTextField(
		value = value,
		onValueChange = onValueChange,
		singleLine = true,
		textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = Color.White),
		cursorBrush = SolidColor(Cyan),
		visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
		keyboardOptions = KeyboardOptions(
			keyboardType = keyboardType,
			imeAction = imeAction,
			autoCorrectEnabled = false,
			// Compose cannot open the keyboard on a press, so it opens when the field gets focus.
			showKeyboardOnFocus = true,
		),
		keyboardActions = KeyboardActions(onAny = { onDone() }),
		modifier = modifier
			.fillMaxWidth()
			.onFocusChanged {
				focused = it.isFocused
				onTyping(it.isFocused)
			},
		decorationBox = { inner ->
			Box(
				contentAlignment = Alignment.CenterStart,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(14.dp))
					.background(Field)
					.border(2.dp, if (focused) Color.White else Line, RoundedCornerShape(14.dp))
					.padding(horizontal = 18.dp, vertical = 14.dp),
			) {
				if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = Color(0xFF59618A))
				inner()
			}
		},
	)
}

@Composable
private fun Problem(title: String, detail: String) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.background(Danger.copy(alpha = .12f))
			.border(1.dp, Danger.copy(alpha = .35f), RoundedCornerShape(14.dp))
			.padding(16.dp),
	) {
		Text("!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Danger)
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
			Text(detail, fontSize = 15.sp, color = Muted, lineHeight = 21.sp)
		}
	}
}

/** A QR code for [text], drawn module by module. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
	val matrix = remember(text) {
		QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
	}
	Canvas(modifier) {
		val cell = size.minDimension / matrix.width
		for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
			if (matrix[x, y]) drawRect(WhosWatchingStyle.Night, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
		}
	}
}

/** An address without its scheme or trailing slash, as people type it. */
private fun String.displayAddress() = removePrefix("https://").removePrefix("http://").trimEnd('/')

