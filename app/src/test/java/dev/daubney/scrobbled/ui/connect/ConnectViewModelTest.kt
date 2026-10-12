package dev.daubney.scrobbled.ui.connect

import dev.daubney.scrobbled.data.auth.AuthRepository
import dev.daubney.scrobbled.data.auth.ConnectException
import dev.daubney.scrobbled.data.auth.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuth(var stored: Credentials? = null, var failure: ConnectException? = null) : AuthRepository {
    var connectCalls = 0
    override fun current() = stored
    override suspend fun connect(serverUrl: String, username: String, password: String): Credentials {
        connectCalls++
        failure?.let { throw it }
        return Credentials(serverUrl, username, "t", "k", "s", "sk").also { stored = it }
    }
    override fun disconnect() { stored = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun filled(vm: ConnectViewModel) {
        vm.onServerUrl("host"); vm.onUsername("alice"); vm.onPassword("pw")
    }

    @Test
    fun startsDisconnectedAndCannotSubmitUntilFilled() {
        val vm = ConnectViewModel(FakeAuth())
        assertFalse(vm.state.value.connected)
        assertFalse(vm.state.value.canSubmit)
        filled(vm)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun startsConnectedWhenCredentialsStored() {
        val vm = ConnectViewModel(FakeAuth(Credentials("https://h", "a", "t", "k", "s", "sk")))
        assertTrue(vm.state.value.connected)
        assertEquals("https://h", vm.state.value.serverUrl)
    }

    @Test
    fun successfulConnectClearsPassword() {
        val vm = ConnectViewModel(FakeAuth())
        filled(vm)
        vm.connect()
        assertTrue(vm.state.value.connected)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun failureShowsErrorAndStaysDisconnected() {
        val vm = ConnectViewModel(FakeAuth(failure = ConnectException("Wrong username or password")))
        filled(vm)
        vm.connect()
        assertEquals("Wrong username or password", vm.state.value.error)
        assertFalse(vm.state.value.connected)
        vm.onPassword("again")
        assertNull(vm.state.value.error)
    }

    @Test
    fun connectIgnoredWhenFormIncomplete() {
        val auth = FakeAuth()
        ConnectViewModel(auth).connect()
        assertEquals(0, auth.connectCalls)
    }

    @Test
    fun disconnectReturnsToForm() {
        val auth = FakeAuth(Credentials("https://h", "a", "t", "k", "s", "sk"))
        val vm = ConnectViewModel(auth)
        vm.disconnect()
        assertFalse(vm.state.value.connected)
        assertNull(auth.stored)
    }

    @Test
    fun sessionExpiredReturnsToFormKeepingServerAndUsername() {
        val auth = FakeAuth(Credentials("https://h", "alice", "t", "k", "s", "sk"))
        val vm = ConnectViewModel(auth)
        vm.onSessionExpired()
        val s = vm.state.value
        assertFalse(s.connected)
        assertEquals("https://h", s.serverUrl)
        assertEquals("alice", s.username)
        assertEquals("", s.password)
        assertEquals("Your session expired. Sign in again.", s.error)
        assertNull(auth.stored) // otherwise a restart would drop straight back into the broken session
    }
}
