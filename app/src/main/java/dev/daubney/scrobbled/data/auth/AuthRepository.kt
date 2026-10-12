package dev.daubney.scrobbled.data.auth

import dev.daubney.scrobbled.data.api.ApiException
import dev.daubney.scrobbled.data.api.LastFmClient
import dev.daubney.scrobbled.data.api.LastFmException
import dev.daubney.scrobbled.data.api.ScrobblerApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Why a connect attempt failed, in terms the UI can show. */
class ConnectException(message: String) : Exception(message)

interface AuthRepository {
    fun current(): Credentials?
    suspend fun connect(serverUrl: String, username: String, password: String): Credentials
    fun disconnect()
}

/**
 * Connect = log in for a bearer token, find or create this app's API key/secret, then use the
 * key to get a Last.fm session key (the only credential that can write scrobbles).
 */
class DefaultAuthRepository(
    private val store: CredentialStore,
    private val apiFor: (String) -> ScrobblerApi = { ScrobblerApi(it) },
    private val lastFmFor: (url: String, key: String, secret: String) -> LastFmClient =
        { url, key, secret -> LastFmClient(url, key, secret) },
) : AuthRepository {

    override fun current(): Credentials? = store.load()

    override suspend fun connect(serverUrl: String, username: String, password: String): Credentials {
        val url = normalizeServerUrl(serverUrl) ?: throw ConnectException("Enter a valid server address")
        try {
            val api = apiFor(url)
            val login = api.login(username, password)
            // Reuse our app on reconnect so repeated logins don't pile up API apps on the server.
            val app = api.listApps(login.token).firstOrNull { it.name == APP_NAME }
                ?: api.createApp(login.token, APP_NAME)
            val session = lastFmFor(url, app.apiKey, app.sharedSecret).getMobileSession(username, password)
            return Credentials(url, login.username, login.token, app.apiKey, app.sharedSecret, session.key)
                .also(store::save)
        } catch (e: ApiException) {
            throw ConnectException(
                when {
                    e.status == 401 -> "Wrong username or password"
                    e.status == -1 -> "Couldn't reach the server: ${e.message}"
                    else -> e.message.orEmpty()
                },
            )
        } catch (e: LastFmException) {
            throw ConnectException(if (e.code == -1) "Couldn't reach the server: ${e.message}" else e.message.orEmpty())
        }
    }

    override fun disconnect() = store.clear()

    companion object {
        const val APP_NAME = "Scrobbled Android"
    }
}

/** Adds a scheme if missing (https) and strips trailing slashes; null if it isn't a usable URL. */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.scheme != "http" && url.scheme != "https") return null
    return withScheme.trimEnd('/')
}
