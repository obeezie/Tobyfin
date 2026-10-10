package org.jellyfin.androidtv.preference

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryGenre(val genreId: String, val title: String) {
    val id: String get() = "genre:server:" + genreId
}