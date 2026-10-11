package org.jellyfin.mobile.webapp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BundledWebFrontendTests : FunSpec({
    test("loads the bundled frontend under the selected server origin") {
        val frontend = BundledWebFrontend("http://192.168.1.132:8096/")
        frontend.startUrl shouldBe "http://192.168.1.132:8096/web/index.html"
        frontend.assetPath(frontend.startUrl) shouldBe "web/index.html"
        frontend.assetPath("http://192.168.1.132:8096/web/") shouldBe "web/index.html"
    }
    test("supports reverse proxy base paths and encoded bundle filenames") {
        val frontend = BundledWebFrontend("https://media.example/jellyfin")
        frontend.startUrl shouldBe "https://media.example/jellyfin/web/index.html"
        frontend.assetPath("https://media.example/jellyfin/web/node_modules.%40jellyfin.sdk.bundle.js?v=1") shouldBe
            "web/node_modules.@jellyfin.sdk.bundle.js"
        frontend.assetPath("https://media.example/web/index.html") shouldBe null
    }
    test("leaves API images streams authentication and sockets on the server") {
        val frontend = BundledWebFrontend("https://media.example")
        for (path in listOf("Users/AuthenticateByName", "Items", "Items/123/Images/Primary", "Videos/123/stream", "socket", "Sessions/Capabilities/Full")) {
            frontend.assetPath("https://media.example/$path") shouldBe null
        }
    }
    test("rejects other servers ports protocols and directory traversal") {
        val frontend = BundledWebFrontend("https://media.example")
        for (url in listOf("https://other.example/web/main.js", "http://media.example/web/main.js",
            "https://media.example:8443/web/main.js", "https://media.example/web/../private",
            "https://media.example/web/%2e%2e/private", "https://media.example/web/foo%5cbar", "not a URL")) {
            frontend.assetPath(url) shouldBe null
        }
    }
    test("accepts explicit default ports without treating unrelated origins as the server") {
        val frontend = BundledWebFrontend("https://media.example")
        frontend.assetPath("https://MEDIA.example:443/web/main.jellyfin.bundle.js?deferred=true") shouldBe "web/main.jellyfin.bundle.js"
        frontend.isServerRequest("https://media.example.evil.test/web/main.js") shouldBe false
    }
})
