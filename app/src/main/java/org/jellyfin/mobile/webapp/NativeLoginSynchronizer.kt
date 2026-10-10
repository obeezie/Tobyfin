package org.jellyfin.mobile.webapp

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.net.URI
import java.util.UUID

class NativeLoginCredentials(val userId: UUID, val accessToken: String)

/** A delayed or malformed WebView result must not throw out of the fragment's lifecycle scope. */
class NativeLoginSynchronizer(private val serverUrl: String) {
    fun parse(result: String?): NativeLoginCredentials? {
        return runCatching {
            var value = Json.parseToJsonElement(result ?: return null)
            // evaluateJavascript(localStorage.getItem(...)) returns a JSON-encoded string.
            if (value is JsonPrimitive) {
                value = Json.parseToJsonElement(value.contentOrNull ?: return null)
            }
            val servers = ((value as? JsonObject)?.get("Servers") as? JsonArray) ?: return null
            val authenticated = servers.mapNotNull { it as? JsonObject }.filter { stored ->
                string(stored, "UserId")?.toUUIDOrNull() != null && !string(stored, "AccessToken").isNullOrBlank()
            }
            val selected = authenticated.firstOrNull { stored ->
                addresses.any { key -> string(stored, key)?.let(::sameServer) == true }
            } ?: authenticated.singleOrNull()?.takeIf { stored -> addresses.all { string(stored, it).isNullOrBlank() } }
            selected ?: return null
            NativeLoginCredentials(string(selected, "UserId")!!.toUUIDOrNull()!!, string(selected, "AccessToken")!!)
        }.getOrNull()
    }

    suspend fun synchronize(
        readCredentials: suspend () -> String?,
        signIn: suspend (NativeLoginCredentials) -> Unit,
        onFailure: (Exception) -> Unit,
    ): Boolean = try {
        val credentials = parse(readCredentials())
        if (credentials == null) {
            false
        } else {
            signIn(credentials)
            true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
        false
    }

    private fun sameServer(address: String): Boolean = runCatching {
        val selected = URI(serverUrl.trimEnd('/'))
        val stored = URI(address.trimEnd('/'))
        selected.scheme.equals(stored.scheme, true) && selected.host.equals(stored.host, true) &&
            effectivePort(selected) == effectivePort(stored) && selected.path.trimEnd('/') == stored.path.trimEnd('/')
    }.getOrDefault(false)

    private fun effectivePort(uri: URI) = if (uri.port != -1) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
    private fun string(value: JsonObject, key: String) = (value[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        val addresses = listOf("ManualAddress", "LocalAddress", "RemoteAddress")
    }
}
