package org.jellyfin.androidtv.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.ui.startup.WhosWatchingServers
import java.util.UUID

class AddProfileTests : FunSpec({
	fun server(name: String, id: UUID = UUID.randomUUID()) = Server(id = id, name = name, address = "http://$name:8096")

	test("servers found on the network leave out the ones already saved, once each") {
		val home = server("home")
		val nas = server("nas")
		val found = listOf(home.copy(address = "http://10.0.0.2:8096"), nas, nas.copy())

		WhosWatchingServers.newOnNetwork(found, saved = listOf(home)) shouldBe listOf(nas)
		WhosWatchingServers.newOnNetwork(found, saved = emptyList()).map { it.name } shouldBe listOf("home", "nas")
	}

	test("the Quick Connect link opens the server's page with the code filled in") {
		WhosWatchingServers.quickConnectUrl("https://jelly.example.com/", "482913") shouldBe
			"https://jelly.example.com/web/#/quickconnect?code=482913"
		WhosWatchingServers.quickConnectUrl("http://192.168.1.40:8096", "111649") shouldBe
			"http://192.168.1.40:8096/web/#/quickconnect?code=111649"
	}
})
