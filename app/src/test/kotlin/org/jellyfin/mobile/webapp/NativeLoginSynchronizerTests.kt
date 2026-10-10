package org.jellyfin.mobile.webapp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class NativeLoginSynchronizerTests : FunSpec({
    val userId = "0123456789abcdef0123456789abcdef"
    val sync = NativeLoginSynchronizer("https://media.example/jellyfin")
    fun stored(address: String, id: String = userId) = """{"ManualAddress":"$address","UserId":"$id","AccessToken":"test-token"}"""
    fun payload(vararg servers: String) = """{"Servers":[${servers.joinToString(",") }]}"""

    test("POST capability reports trigger native sign-in without intercepting unrelated APIs") {
        val frontend = BundledWebFrontend("https://media.example/jellyfin")
        frontend.isNativeLoginRequest("https://media.example/jellyfin/Sessions/Capabilities/Full", "POST") shouldBe true
        frontend.isNativeLoginRequest("https://media.example/jellyfin/sessions/capabilities/full?x=1", "GET") shouldBe true
        frontend.isNativeLoginRequest("https://other.example/jellyfin/Sessions/Capabilities/Full", "POST") shouldBe false
        frontend.isNativeLoginRequest("https://media.example/jellyfin/Users/AuthenticateByName", "POST") shouldBe false
        frontend.isNativeLoginRequest("https://media.example/jellyfin/Sessions/Capabilities/Full", "DELETE") shouldBe false
    }
    test("reads both raw objects and WebView's JSON-encoded storage string") {
        val raw = payload(stored("https://media.example/jellyfin/"))
        for (result in listOf(raw, JsonPrimitive(raw).toString())) {
            val credentials = sync.parse(result)!!
            credentials.userId shouldBe UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
            credentials.accessToken shouldBe "test-token"
        }
    }
    test("selects the current server rather than the first saved server") {
        val raw = payload(stored("https://other.example"), stored("https://media.example:443/jellyfin", "fedcba9876543210fedcba9876543210"))
        sync.parse(raw)!!.userId shouldBe UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210")
        sync.parse(payload(stored("https://media.example/another-base-path"))) shouldBe null
    }
    test("missing malformed or incomplete storage never throws or signs in") {
        for (result in listOf(null, "null", "undefined", "{", "[]", "{}", """{"Servers":[]} """,
            payload("null"), payload("""{"UserId":{},"AccessToken":[]} """), payload(stored("https://media.example/jellyfin", "bad-id")))) {
            var signedIn = false
            sync.synchronize({ result }, { signedIn = true }, { throw AssertionError("Unexpected parse failure", it) }) shouldBe false
            signedIn shouldBe false
        }
    }
    test("native setup failure is reported instead of escaping the login coroutine") {
        var reported = 0
        sync.synchronize({ payload(stored("https://media.example/jellyfin")) }, { throw IllegalStateException("setup failed") }, { reported++ }) shouldBe false
        reported shouldBe 1
    }
    test("cancelled WebView work stays cancelled instead of being treated as a login error") {
        shouldThrow<CancellationException> {
            sync.synchronize({ throw CancellationException("view destroyed") }, {}, { throw AssertionError("Unexpected error", it) })
        }
    }
})
