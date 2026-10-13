package org.jellyfin.androidtv.auth.model

/**
 * The outcome of signing in to save an account on this device without switching to it.
 */
sealed interface SaveAccountResult {
	data class Saved(val user: PrivateUser) : SaveAccountResult
	data object WrongCredentials : SaveAccountResult
	data object ServerUnavailable : SaveAccountResult
	data object Failed : SaveAccountResult
}
