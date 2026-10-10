package org.jellyfin.mobile.webapp

import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID

class LoginPlaybackHandoffTests : FunSpec({
    test("a real POST login handoff authorizes native playback while leaving the request on the network") {
        val server = ServerEntity(7, "https://media.example/jellyfin", 0)
        val user = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
        val storage = """{"Servers":[{"ManualAddress":"${server.hostname}","UserId":"${user.toString().replace("-", "")}","AccessToken":"test-token"}]}"""
        val viewModel = mockk<MainViewModel>()
        var nativePlaybackToken: String? = null
        coEvery { viewModel.setupUser(server.id, user, "test-token") } answers { nativePlaybackToken = "test-token" }
        val webView = mockk<WebView>()
        every { webView.context } returns mockk<Context>()
        every { webView.evaluateJavascript(any(), any()) } answers {
            val script = firstArg<String>()
            secondArg<ValueCallback<String>>().onReceiveValue(
                if (script.contains("jellyfin_credentials")) JsonPrimitive(storage).toString() else "null",
            )
        }
        val assets = mockk<AssetsPathHandler>()
        val client = object : JellyfinWebViewClient(CoroutineScope(Dispatchers.Unconfined), server, assets, viewModel) {
            override fun onConnectedToWebapp() = Unit
            override fun onErrorReceived() = error("Login must not close the working web session")
        }
        val request = mockk<WebResourceRequest>()
        val uri = mockk<Uri>()
        every { request.method } returns "POST"
        every { request.url } returns uri
        every { uri.toString() } returns "${server.hostname}/Sessions/Capabilities/Full"
        every { uri.path } returns "/jellyfin/Sessions/Capabilities/Full"
        every { uri.query } returns null

        client.shouldInterceptRequest(webView, request) shouldBe null
        coVerify(exactly = 1) { viewModel.setupUser(server.id, user, "test-token") }
        nativePlaybackToken shouldBe "test-token"
        verify { assets wasNot Called }
    }

    test("a failed streaming startup reaches the UI instead of reporting success for a black player") {
        val failure = PlayerException.NetworkFailure()
        val api = mockk<ApiClient>(relaxed = true)
        every { api.getOrCreateApi(VideosApi::class, any()) } returns mockk<VideosApi>()
        val resolver = mockk<MediaSourceResolver>()
        coEvery { resolver.resolveMediaSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(failure)
        val profileBuilder = mockk<DeviceProfileBuilder>()
        every { profileBuilder.getDeviceProfile() } returns mockk<DeviceProfile>()
        startKoin {
            modules(module {
                single { api }
                single { resolver }
                single { profileBuilder }
            })
        }
        try {
            val queue = QueueManager(mockk<PlayerViewModel>(relaxed = true))
            val options = PlayOptions(listOf(UUID(0, 1)), null, 0, null, null, null, false)
            queue.initializePlaybackQueue(options) shouldBe failure
        } finally {
            stopKoin()
        }
    }
})
