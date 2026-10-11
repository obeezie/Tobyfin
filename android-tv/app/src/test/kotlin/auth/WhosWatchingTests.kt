package org.jellyfin.androidtv.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior
import kotlin.time.Duration.Companion.minutes

class WhosWatchingTests : FunSpec({
	test("asks on launch only with the ask setting and more than one saved profile") {
		WhosWatching.askOnLaunch(UserSelectBehavior.ASK, savedProfiles = 2) shouldBe true
		WhosWatching.askOnLaunch(UserSelectBehavior.ASK, savedProfiles = 1) shouldBe false
		WhosWatching.askOnLaunch(UserSelectBehavior.LAST_USER, savedProfiles = 3) shouldBe false
	}

	test("asks again only after 30 minutes away, when someone is signed in") {
		WhosWatching.askOnReturn(UserSelectBehavior.ASK, 2, signedIn = true, away = 29.minutes) shouldBe false
		WhosWatching.askOnReturn(UserSelectBehavior.ASK, 2, signedIn = true, away = 30.minutes) shouldBe true
		WhosWatching.askOnReturn(UserSelectBehavior.ASK, 2, signedIn = false, away = 90.minutes) shouldBe false
		WhosWatching.askOnReturn(UserSelectBehavior.ASK, 1, signedIn = true, away = 90.minutes) shouldBe false
		WhosWatching.askOnReturn(UserSelectBehavior.SPECIFIC_USER, 2, signedIn = true, away = 90.minutes) shouldBe false
	}

	test("the highlight starts on the last profile used, or the first") {
		val profiles = listOf("Alex", "Jordan", "Sam")
		WhosWatching.initialFocus(profiles) { it == "Jordan" } shouldBe 1
		WhosWatching.initialFocus(profiles) { it == "Removed" } shouldBe 0
	}
})
