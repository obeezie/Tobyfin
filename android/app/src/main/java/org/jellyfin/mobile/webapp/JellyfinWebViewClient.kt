package org.jellyfin.mobile.webapp

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.initLocale
import org.jellyfin.mobile.utils.inject
import timber.log.Timber
import java.io.Reader
import java.util.Locale
import kotlin.coroutines.resume

abstract class JellyfinWebViewClient(
    private val coroutineScope: CoroutineScope,
    private val server: ServerEntity,
    private val assetsPathHandler: AssetsPathHandler,
    private val mainViewModel: MainViewModel,
) : WebViewClientCompat() {
    private val bundledFrontend = BundledWebFrontend(server.hostname)
    private val nativeLogin = NativeLoginSynchronizer(server.hostname)

    abstract fun onConnectedToWebapp()

    abstract fun onErrorReceived()

    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!bundledFrontend.isServerRequest(request.url.toString())) return null
        // Capabilities are reported with POST after authentication. Observe the request,
        // then let WebView send it to the server with its original method/body/headers.
        if (bundledFrontend.isNativeLoginRequest(request.url.toString(), request.method)) {
            synchronizeNativeLogin(webView)
            return null
        }
        if (request.method != "GET") return null
        val url = request.url
        val path = url.path?.lowercase(Locale.ROOT) ?: return null
        return when {
            path.matches(Constants.MAIN_BUNDLE_PATH_REGEX) && "deferred" !in url.query.orEmpty() -> {
                onConnectedToWebapp()
                assetsPathHandler.inject("native/injectionScript.js")
            }
            // Load injected scripts from application assets
            path.contains("/native/") -> assetsPathHandler.inject("native/${url.lastPathSegment}")
            // Load the chrome.cast.js library instead
            path.endsWith(Constants.CAST_SDK_PATH) -> assetsPathHandler.inject("native/chrome.cast.js")
            else -> bundledFrontend.assetPath(url.toString())?.let { asset ->
                assetsPathHandler.inject(asset)?.apply {
                    responseHeaders = mapOf("Cache-Control" to "no-store")
                }
            }
        }
    }

    private fun synchronizeNativeLogin(webView: WebView) {
        coroutineScope.launch {
            val synchronized = nativeLogin.synchronize(
                readCredentials = {
                    suspendCancellableCoroutine { continuation ->
                        webView.evaluateJavascript("window.localStorage.getItem('jellyfin_credentials')") { result ->
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }
                },
                signIn = { credentials ->
                    mainViewModel.setupUser(server.id, credentials.userId, credentials.accessToken)
                    webView.initLocale(credentials.userId)
                },
                onFailure = { error -> Timber.e("Native login synchronization failed (%s)", error.javaClass.simpleName) },
            )
            if (!synchronized) Timber.w("Native login credentials are not ready; keeping the web session open")
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
        Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)

        if (request.isForMainFrame) onErrorReceived()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceErrorCompat,
    ) {
        val description = when {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION) -> error.description
            else -> null
        }
        val errorCode = when {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE) -> error.errorCode
            else -> ERROR_UNKNOWN
        }
        Timber.e("Received WebView error %d at %s: %s", errorCode, request.url.toString(), description)

        // Abort on some specific error codes or when the request url matches the server url
        if (request.isForMainFrame) onErrorReceived()
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Timber.e("Received SSL error: %s", error.toString())
        handler.cancel()
    }
}
