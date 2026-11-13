package dev.atomix.launcher.ui.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.HttpException
import dev.atomix.launcher.data.PollResult
import dev.atomix.launcher.data.Profile
import dev.atomix.launcher.data.QuickConnectStart
import dev.atomix.launcher.data.ServerLocator
import dev.atomix.launcher.data.ServerProbe
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.data.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SetupViewModelTest {
    private val server = "http://192.168.8.65:8787/".toHttpUrl()
    private lateinit var store: SessionStore

    private class FakeApi : AtomixClient {
        var codes = ArrayDeque(listOf("AAAAAA", "BBBBBB"))
        var polls = ArrayDeque<PollResult>()
        var pollCount = 0
        var profilesList = listOf(Profile(1, "Dallas"))
        var correctPin: String? = null
        var startFails = false
        var profilesExpired = false
        val selected = mutableListOf<Long>()
        override suspend fun status() = Status("Atomix")
        override suspend fun quickConnectStart(): QuickConnectStart { if (startFails) throw java.io.IOException("offline"); return QuickConnectStart(codes.removeFirst(), "poll", 300_000, 300_000) }
        override suspend fun quickConnectPoll(poll: String): PollResult { pollCount++; return polls.removeFirstOrNull() ?: PollResult.Pending }
        override suspend fun profiles(): List<Profile> { if (profilesExpired) { profilesExpired = false; throw dev.atomix.launcher.data.AuthExpiredException() }; return profilesList }
        override suspend fun selectProfile(id: Long, pin: String?) {
            if (correctPin != null && pin != correctPin) throw HttpException(403, "Wrong PIN")
            selected += id
        }
        override suspend fun homeJson() = "{}"
        override fun imageUrl(path: String?): HttpUrl? = null
    }

    private class Probe(val ok: Boolean) : ServerProbe { override suspend fun isAtomix(base: HttpUrl) = ok }

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        store = SessionStore(ctx, encrypted = false).also { it.serverUrl = null; it.lastKnownUrl = null; it.clearAuth() }
    }

    private fun TestScope.vm(api: FakeApi, found: Boolean = true) = SetupViewModel(
        apiFor = { api },
        locator = ServerLocator(Probe(found)) { listOf("192.168.8.65") },
        store = store,
        scope = backgroundScope,
        now = { testScheduler.currentTime },
    )

    @Test fun noServerFound_asksForAddress() = runTest {
        val vm = vm(FakeApi(), found = false)
        vm.start(); runCurrent()
        assertEquals(SetupState.EnterAddress(null), vm.state.value)
    }

    @Test fun quickConnect_pollsEvery2s_untilApproved_savesToken() = runTest {
        val api = FakeApi().apply { polls = ArrayDeque(listOf(PollResult.Pending, PollResult.Pending, PollResult.Approved("tok"))) }
        api.profilesList = listOf(Profile(1, "Dallas"), Profile(2, "Kid", kids = true))
        val vm = vm(api)
        vm.start(); runCurrent()
        assertEquals("AAAAAA", (vm.state.value as SetupState.QuickConnect).code)
        advanceTimeBy(2_001); runCurrent()
        assertEquals(1, api.pollCount)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(3, api.pollCount)
        assertEquals("tok", store.token)
        assertEquals(server, store.serverUrl)
        assertTrue(vm.state.value is SetupState.ChooseProfile)
    }

    @Test fun quickConnect_expired_restartsWithNewCode() = runTest {
        val api = FakeApi().apply { polls = ArrayDeque(listOf(PollResult.Expired)) }
        val vm = vm(api)
        vm.start(); runCurrent()
        advanceTimeBy(2_001); runCurrent()
        assertEquals("BBBBBB", (vm.state.value as SetupState.QuickConnect).code)
    }

    @Test fun singleProfileWithoutPin_selectsAutomatically() = runTest {
        val api = FakeApi().apply { polls = ArrayDeque(listOf(PollResult.Approved("tok"))) }
        val vm = vm(api)
        vm.start(); runCurrent()
        advanceTimeBy(2_001); runCurrent()
        assertEquals(SetupState.Done, vm.state.value)
        assertEquals(listOf(1L), api.selected)
        assertEquals(1L, store.profileId)
        assertEquals("Dallas", store.profileName)
    }

    @Test fun wrongPin_staysOnPinWithError() = runTest {
        val kid = Profile(2, "Kid", hasPin = true)
        val api = FakeApi().apply { profilesList = listOf(Profile(1, "Dallas", hasPin = true), kid); correctPin = "1234" }
        store.serverUrl = server; store.token = "tok"
        val vm = vm(api)
        vm.start(); runCurrent()
        vm.choose(kid); runCurrent()
        assertEquals(SetupState.EnterPin(kid, null), vm.state.value)
        vm.submitPin("0000"); runCurrent()
        assertEquals(SetupState.EnterPin(kid, "Wrong PIN"), vm.state.value)
        vm.submitPin("1234"); runCurrent()
        assertEquals(SetupState.Done, vm.state.value)
    }

    @Test fun networkErrorDuringSetup_showsAddressScreenWithError_insteadOfCrashing() = runTest {
        val api = FakeApi().apply { startFails = true }
        val vm = vm(api)
        vm.start(); runCurrent()
        val s = vm.state.value
        assertTrue("was $s", s is SetupState.EnterAddress && s.error != null)
    }

    @Test fun expiredSessionDuringSetup_clearsAuth_andShowsQuickConnectOnce() = runTest {
        val api = FakeApi().apply { profilesExpired = true }
        store.serverUrl = server; store.token = "old"
        val vm = vm(api)
        vm.start(); runCurrent()
        assertEquals(null, store.token)
        assertEquals("AAAAAA", (vm.state.value as SetupState.QuickConnect).code)
    }
}
