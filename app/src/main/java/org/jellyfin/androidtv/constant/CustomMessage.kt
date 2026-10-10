package org.jellyfin.androidtv.constant

sealed interface CustomMessage {
    data class DiscoveryLayoutChanged(val signature: String) : CustomMessage
	data object RefreshCurrentItem : CustomMessage
	data object ActionComplete : CustomMessage
}
