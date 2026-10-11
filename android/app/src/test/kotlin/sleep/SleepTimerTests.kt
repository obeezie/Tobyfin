package org.jellyfin.mobile.player.sleep

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class SleepTimerTests : FunSpec({
    test("elapsed time expires once even without intermediate ticks while paused or buffering") {
        var now = 1000L
        val timer = SleepTimerState { now }
        timer.start(15)
        now += 899_999
        timer.check() shouldBe false
        now += 1
        timer.check() shouldBe true
        timer.check() shouldBe false
        timer.expired shouldBe true
        timer.finishItem() shouldBe true
    }
    test("timed countdown survives episode changes and extension adds to remaining time") {
        var now = 0L
        val timer = SleepTimerState { now }
        timer.start(30)
        now = 600_000
        timer.finishItem() shouldBe false
        timer.extend()
        timer.remainingMillis shouldBe 2_100_000L
        now += 2_100_000
        timer.finishItem() shouldBe true
    }
    test("end of item blocks autoplay even after a long pause but can be cancelled") {
        var now = 0L
        val timer = SleepTimerState { now }
        timer.endOfItem()
        now = 99_999_999
        timer.check() shouldBe false
        timer.finishItem() shouldBe true
        timer.cancel()
        timer.finishItem() shouldBe false
        timer.expired shouldBe false
    }
    test("replacement and cancellation remove the previous deadline") {
        var now = 0L
        val timer = SleepTimerState { now }
        timer.start(15)
        timer.start(60)
        now = 15 * 60_000L
        timer.check() shouldBe false
        timer.endOfItem()
        timer.deadline shouldBe null
        timer.cancel()
        now = 100 * 60_000L
        timer.check() shouldBe false
        timer.finishItem() shouldBe false
    }
    test("new timer can be set after expiry and invalid durations preserve the existing timer") {
        var now = 0L
        val timer = SleepTimerState { now }
        timer.start(1)
        now = 60_000L
        timer.check() shouldBe true
        timer.extend()
        timer.expired shouldBe false
        timer.remainingMillis shouldBe 900_000L
        shouldThrow<IllegalArgumentException> { timer.start(0) }
        shouldThrow<IllegalArgumentException> { timer.start(1441) }
        timer.remainingMillis shouldBe 900_000L
    }
})
