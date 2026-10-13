package org.jellyfin.androidtv.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.CredentialAuthenticateMethod
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.PublicUser
import org.jellyfin.androidtv.auth.model.QuickConnectAuthenticateMethod
import org.jellyfin.androidtv.auth.model.SaveAccountResult
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** One card of the add-profile panel over "Who's watching?". */
sealed interface AddProfileStep {
	data object Servers : AddProfileStep
	data class Address(val text: String) : AddProfileStep
	data class Connecting(val address: String) : AddProfileStep
	data class QuickConnect(val server: Server, val code: String?) : AddProfileStep
	data class Users(val server: Server, val users: List<PublicUser>?) : AddProfileStep
	data class Password(val server: Server, val username: String?, val wrongPassword: Boolean = false, val signingIn: Boolean = false) : AddProfileStep
	data class Problem(val kind: AddProfileProblem, val retry: () -> Unit) : AddProfileStep
}

sealed interface AddProfileProblem {
	data class Unreachable(val address: String, val tried: List<String>) : AddProfileProblem
	data class TooOld(val server: Server) : AddProfileProblem
	data class ServerOffline(val server: Server) : AddProfileProblem
	data class SignInFailed(val server: Server) : AddProfileProblem
}

/**
 * The add-profile panel: choose or add a server, then sign in with Quick Connect or a password.
 * A saved account is reported through [saved]; the picker then opens it like any other profile.
 */
class AddProfileViewModel(
	private val jellyfin: Jellyfin,
	private val serverRepository: ServerRepository,
	private val serverUserRepository: ServerUserRepository,
	private val authenticationRepository: AuthenticationRepository,
	private val defaultDeviceInfo: DeviceInfo,
) : ViewModel() {
	private val _steps = MutableStateFlow<List<AddProfileStep>>(emptyList())

	/** The open cards, oldest first. Empty when the panel is closed. */
	val steps = _steps.asStateFlow()

	val savedServers = serverRepository.storedServers

	/** Servers on this network that are not saved yet. */
	val foundServers = combine(serverRepository.discoveredServers, serverRepository.storedServers) { found, saved ->
		WhosWatchingServers.newOnNetwork(found, saved)
	}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

	private val _scanning = MutableStateFlow(false)
	val scanning = _scanning.asStateFlow()

	private val _saved = Channel<PrivateUser>(Channel.BUFFERED)
	val saved = _saved.receiveAsFlow()

	private var stepJob: Job? = null
	private var scanJob: Job? = null
	private var lastAddress = ""

	/** Servers saved by this panel, forgotten again when nobody signs in to them. */
	private val addedServers = mutableSetOf<UUID>()

	private val top get() = _steps.value.lastOrNull()

	fun open() {
		if (_steps.value.isNotEmpty()) return
		_steps.value = listOf(AddProfileStep.Servers)
		viewModelScope.launch { serverRepository.loadStoredServers() }
		scan()
	}

	fun close() {
		stepJob?.cancel()
		scanJob?.cancel()
		_scanning.value = false
		_steps.value = emptyList()
		forgetUnusedServers()
	}

	/** One card back. Returns false on the first card, where Back belongs to the picker. */
	fun back(): Boolean {
		if (_steps.value.size <= 1) return false
		stepJob?.cancel()
		_steps.value = _steps.value.dropLast(1)
		// Coming back to a sign-in card restarts its work.
		when (val step = top) {
			is AddProfileStep.QuickConnect -> startQuickConnect(step.server)
			is AddProfileStep.Users -> loadUsers(step.server)
			else -> Unit
		}
		return true
	}

	fun chooseSavedServer(server: Server) = signIn(server)

	fun chooseFoundServer(server: Server) = connect(server.address)

	fun enterAddress() = push(AddProfileStep.Address(lastAddress))

	fun updateAddress(text: String) {
		val step = top as? AddProfileStep.Address ?: return
		lastAddress = text
		replaceTop(step.copy(text = text))
	}

	fun submitAddress() {
		val step = top as? AddProfileStep.Address ?: return
		val address = step.text.trim()
		if (address.isNotEmpty()) connect(address)
	}

	fun usePassword() {
		val step = top as? AddProfileStep.QuickConnect ?: return
		stepJob?.cancel()
		toPassword(step.server)
	}

	fun chooseUser(username: String?) {
		val step = top as? AddProfileStep.Users ?: return
		push(AddProfileStep.Password(step.server, username))
	}

	fun signInWithPassword(username: String, password: String) {
		val step = top as? AddProfileStep.Password ?: return
		if (username.isBlank() || step.signingIn) return
		replaceTop(step.copy(signingIn = true, wrongPassword = false))
		stepJob = viewModelScope.launch {
			when (val result = authenticationRepository.saveAccount(step.server, CredentialAuthenticateMethod(username.trim(), password))) {
				is SaveAccountResult.Saved -> finish(result.user)
				SaveAccountResult.WrongCredentials -> replaceTop(step.copy(wrongPassword = true))
				SaveAccountResult.ServerUnavailable, SaveAccountResult.Failed -> {
					replaceTop(step)
					val kind = if (result == SaveAccountResult.ServerUnavailable) AddProfileProblem.ServerOffline(step.server)
					else AddProfileProblem.SignInFailed(step.server)
					problem(kind) { signInWithPassword(username, password) }
				}
			}
		}
	}

	private fun scan() {
		if (scanJob?.isActive == true) return
		scanJob = viewModelScope.launch(Dispatchers.IO) {
			_scanning.value = true
			try {
				serverRepository.loadDiscoveryServers()
			} finally {
				_scanning.value = false
			}
		}
	}

	private fun connect(address: String) {
		push(AddProfileStep.Connecting(address))
		stepJob = viewModelScope.launch {
			serverRepository.addServer(address).collect { state ->
				when (state) {
					is ConnectingState -> Unit
					is UnableToConnectState -> problem(AddProfileProblem.Unreachable(address, state.addressCandidates.keys.toList()), replacing = true) {
						connect(address)
					}

					is ConnectedState -> {
						if (serverUserRepository.isEmpty(state.id)) addedServers += state.id
						val server = serverRepository.storedServers.value.firstOrNull { it.id == state.id }
						when {
							server == null -> problem(AddProfileProblem.Unreachable(address, emptyList()), replacing = true) { connect(address) }
							!server.versionSupported -> problem(AddProfileProblem.TooOld(server), replacing = true) { connect(address) }
							// Back from sign-in returns to the address, not to "Connecting".
							else -> {
								_steps.value = _steps.value.dropLast(1)
								signIn(server)
							}
						}
					}
				}
			}
		}
	}

	private fun signIn(server: Server) {
		push(AddProfileStep.QuickConnect(server, code = null))
		startQuickConnect(server)
	}

	private fun startQuickConnect(server: Server) {
		stepJob?.cancel()
		stepJob = viewModelScope.launch {
			val api = jellyfin.createApi(baseUrl = server.address, deviceInfo = defaultDeviceInfo.forUser(UUID.randomUUID()))
			val secret = try {
				val started = withContext(Dispatchers.IO) { api.quickConnectApi.initiateQuickConnect().content }
				showCode(server, started.code)
				started.secret
			} catch (err: InvalidStatusException) {
				// Quick Connect is turned off on this server.
				Timber.i(err, "Quick Connect unavailable on ${server.name}")
				return@launch toPassword(server, replace = true)
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to start Quick Connect")
				return@launch problem(AddProfileProblem.ServerOffline(server)) { startQuickConnect(server) }
			}

			while (true) {
				delay(3.seconds)
				val state = try {
					withContext(Dispatchers.IO) { api.quickConnectApi.getQuickConnectState(secret = secret).content }
				} catch (err: ApiClientException) {
					Timber.w(err, "Quick Connect poll failed")
					continue
				}
				if (!state.authenticated) {
					showCode(server, state.code)
					continue
				}

				when (val result = authenticationRepository.saveAccount(server, QuickConnectAuthenticateMethod(state.secret))) {
					is SaveAccountResult.Saved -> finish(result.user)
					SaveAccountResult.ServerUnavailable -> problem(AddProfileProblem.ServerOffline(server)) { startQuickConnect(server) }
					else -> problem(AddProfileProblem.SignInFailed(server)) { startQuickConnect(server) }
				}
				break
			}
		}
	}

	private fun showCode(server: Server, code: String) {
		val step = top as? AddProfileStep.QuickConnect ?: return
		if (step.server.id == server.id && step.code != code) replaceTop(step.copy(code = code))
	}

	private fun toPassword(server: Server, replace: Boolean = false) {
		val users = AddProfileStep.Users(server, users = null)
		if (replace) replaceTop(users) else push(users)
		loadUsers(server)
	}

	private fun loadUsers(server: Server) {
		stepJob?.cancel()
		stepJob = viewModelScope.launch {
			val users = serverUserRepository.getPublicServerUsers(server)
			if (top !is AddProfileStep.Users) return@launch
			// No public users: type the username instead.
			if (users.isEmpty()) replaceTop(AddProfileStep.Password(server, username = null))
			else replaceTop(AddProfileStep.Users(server, users))
		}
	}

	private fun finish(user: PrivateUser) {
		addedServers -= user.serverId
		_saved.trySend(user)
		close()
	}

	/**
	 * Show a problem card. It goes over the card that failed, so Back returns there, except while
	 * connecting ([replacing]), where Back should return to the address instead.
	 */
	private fun problem(kind: AddProfileProblem, replacing: Boolean = false, retry: () -> Unit) {
		stepJob?.cancel()
		val card = AddProfileStep.Problem(kind) {
			_steps.value = _steps.value.dropLast(1)
			retry()
		}
		if (replacing) replaceTop(card) else push(card)
	}

	private fun push(step: AddProfileStep) {
		_steps.value += step
	}

	private fun replaceTop(step: AddProfileStep) {
		_steps.value = _steps.value.dropLast(1) + step
	}

	private fun forgetUnusedServers() {
		val unused = addedServers.filter { serverUserRepository.isEmpty(it) }
		addedServers.clear()
		if (unused.isEmpty()) return
		viewModelScope.launch { unused.forEach { serverRepository.deleteServer(it) } }
	}

	private fun ServerUserRepository.isEmpty(serverId: UUID): Boolean =
		serverRepository.storedServers.value.firstOrNull { it.id == serverId }
			?.let { getStoredServerUsers(it).isEmpty() } ?: true
}

object WhosWatchingServers {
	/** Servers found on the network that are not saved on this TV yet. */
	fun newOnNetwork(found: List<Server>, saved: List<Server>): List<Server> {
		val savedIds = saved.map { it.id }.toSet()
		return found.filter { it.id !in savedIds }.distinctBy { it.id }
	}

	/** A link that opens Quick Connect on [address] with [code] filled in. */
	fun quickConnectUrl(address: String, code: String) = "${address.trimEnd('/')}/web/#/quickconnect?code=$code"
}
