package dev.daubney.scrobbled.data.auth

import kotlinx.serialization.Serializable

/** Everything needed to talk to the server after a successful connect. */
@Serializable
data class Credentials(
    val serverUrl: String,
    val username: String,
    /** Bearer token for `/api/v1` (30-day lifetime, no refresh). */
    val bearerToken: String,
    val apiKey: String,
    val apiSecret: String,
    /** Last.fm session key for `/2.0/` writes; does not expire. */
    val sessionKey: String,
)

interface CredentialStore {
    fun load(): Credentials?
    fun save(credentials: Credentials)
    fun clear()
}
