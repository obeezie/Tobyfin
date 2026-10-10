package org.jellyfin.androidtv.preference

import android.content.Context
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient

/** Local to this app installation, namespaced by server and account; no server preference writes. */
class DiscoveryPreferences(context: Context, private val api: ApiClient, private val users: UserRepository) {
    private val storage = context.getSharedPreferences("discovery-layouts-v1", Context.MODE_PRIVATE)
    val accountKey: String? get() = users.currentUser.value?.id?.let { api.baseUrl.toString() + "|" + it }
    fun read(): DiscoveryLayout = DiscoveryLayout.decode(accountKey?.let { storage.getString(it, null) })
    fun save(value: DiscoveryLayout, expectedAccount: String?): Boolean {
        val key = accountKey ?: return false
        if (key != expectedAccount) return false
        return storage.edit().putString(key, DiscoveryLayout.encode(value)).commit()
    }
    fun signature() = accountKey.orEmpty() + DiscoveryLayout.encode(read())
}
