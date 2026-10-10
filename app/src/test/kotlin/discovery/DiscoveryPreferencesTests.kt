package discovery

import android.content.Context
import android.content.SharedPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.DiscoveryLayout
import org.jellyfin.androidtv.preference.DiscoveryPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.UserDto
import java.util.UUID

class DiscoveryPreferencesTests : FunSpec({
    test("layouts are isolated by account and server and saving checks account identity") {
        val values = mutableMapOf<String, String?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
        every { editor.commit() } returns true
        val storage = mockk<SharedPreferences>()
        every { storage.getString(any(), any()) } answers { values[firstArg()] }
        every { storage.edit() } returns editor
        val context = mockk<Context>()
        every { context.getSharedPreferences("discovery-layouts-v1", Context.MODE_PRIVATE) } returns storage
        val first = UserDto(id = UUID.randomUUID(), hasPassword = false, hasConfiguredPassword = false, hasConfiguredEasyPassword = false)
        val second = UserDto(id = UUID.randomUUID(), hasPassword = false, hasConfiguredPassword = false, hasConfiguredEasyPassword = false)
        val userFlow = MutableStateFlow<UserDto?>(first)
        val users = mockk<UserRepository>()
        every { users.currentUser } returns userFlow
        var server = "https://one.test"
        val api = mockk<ApiClient>()
        every { api.baseUrl } answers { server }
        val prefs = DiscoveryPreferences(context, api, users)
        val firstKey = prefs.accountKey
        prefs.save(DiscoveryLayout(size = "large"), firstKey) shouldBe true
        userFlow.value = second
        prefs.read().size shouldBe "medium"
        prefs.save(DiscoveryLayout(size = "small"), firstKey) shouldBe false
        userFlow.value = first
        prefs.read().size shouldBe "large"
        server = "https://two.test"
        prefs.read().size shouldBe "medium"
        userFlow.value = null
        prefs.save(DiscoveryLayout(), null) shouldBe false
    }
})
