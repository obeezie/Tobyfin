package org.jellyfin.mobile.webapp

import java.net.URI

/** Serve only the selected server's web directory from the APK; APIs stay on the network. */
class BundledWebFrontend(serverUrl: String) {
    private val server = URI(serverUrl.trimEnd('/'))
    private val webRoot = server.path.trimEnd('/') + "/web/"
    val startUrl = serverUrl.trimEnd('/') + "/web/index.html"

    fun isServerRequest(url: String): Boolean {
        val request = runCatching { URI(url) }.getOrNull() ?: return false
        return request.scheme.equals(server.scheme, ignoreCase = true) &&
            request.host.equals(server.host, ignoreCase = true) && port(request) == port(server)
    }

    fun assetPath(url: String): String? {
        if (!isServerRequest(url)) return null
        val path = URI(url).path ?: return null
        if (!path.startsWith(webRoot)) return null
        val relative = path.removePrefix(webRoot).ifEmpty { "index.html" }
        if (relative.split('/').any { it == ".." || it == "." } || '\\' in relative || '\u0000' in relative) return null
        return "web/$relative"
    }

    fun isNativeLoginRequest(url: String, method: String): Boolean =
        isServerRequest(url) && (method.equals("POST", true) || method.equals("GET", true)) &&
            URI(url).path?.trimEnd('/')?.equals(server.path.trimEnd('/') + "/Sessions/Capabilities/Full", true) == true

    private fun port(uri: URI): Int = if (uri.port != -1) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
}
