package org.jellyfin.mobile.player.upnext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UpNextRulesTests : FunSpec({
    test("without a credits marker the card shows 30 seconds before the end and counts down to the end") {
        val timing = UpNextRules.timing(22.minutes, creditsStart = null)!!
        timing.showAt shouldBe 22.minutes - 30.seconds
        timing.advanceAt shouldBe 22.minutes
        timing.skipsCredits shouldBe false
    }

    test("a credits marker shows the card when the credits start and starts the next episode 10 seconds later") {
        val timing = UpNextRules.timing(22.minutes, creditsStart = 20.minutes)!!
        timing.showAt shouldBe 20.minutes
        timing.advanceAt shouldBe 20.minutes + 10.seconds
        timing.skipsCredits shouldBe true
    }

    test("credits markers in the first half or inside the last 30 seconds are ignored") {
        UpNextRules.timing(22.minutes, creditsStart = 5.minutes)!!.showAt shouldBe 22.minutes - 30.seconds
        UpNextRules.timing(22.minutes, creditsStart = 22.minutes - 10.seconds)!!.showAt shouldBe 22.minutes - 30.seconds
    }

    test("no card for unknown or very short videos") {
        UpNextRules.timing(0.seconds, creditsStart = null) shouldBe null
        UpNextRules.timing(60.seconds, creditsStart = null) shouldBe null
        (UpNextRules.timing(61.seconds, creditsStart = null) != null) shouldBe true
    }

    test("the countdown rounds up to whole seconds and stops at zero") {
        val advanceAt = 100.seconds
        UpNextRules.secondsLeft(90.seconds, advanceAt) shouldBe 10
        UpNextRules.secondsLeft(90.seconds + 1.milliseconds, advanceAt) shouldBe 10
        UpNextRules.secondsLeft(99.seconds + 999.milliseconds, advanceAt) shouldBe 1
        UpNextRules.secondsLeft(100.seconds, advanceAt) shouldBe 0
        UpNextRules.secondsLeft(105.seconds, advanceAt) shouldBe 0
    }
})
