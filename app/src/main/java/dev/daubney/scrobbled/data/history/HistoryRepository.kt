package dev.daubney.scrobbled.data.history

import dev.daubney.scrobbled.data.api.ApiException
import dev.daubney.scrobbled.data.api.ScrobblerApi
import dev.daubney.scrobbled.data.auth.AuthRepository

interface HistoryRepository {
    suspend fun nowPlaying(): NowPlaying?
    suspend fun recent(page: Int, limit: Int = PAGE_SIZE): ScrobblePage
    suspend fun summary(): Summary

    companion object {
        const val PAGE_SIZE = 50
    }
}

/** Reads the signed-in user's data with the stored bearer token. A 401 means the 30-day token lapsed. */
class DefaultHistoryRepository(
    private val auth: AuthRepository,
    private val apiFor: (String) -> ScrobblerApi = { ScrobblerApi(it) },
) : HistoryRepository {

    override suspend fun nowPlaying() = call { api, token -> api.nowPlaying(token) }
    override suspend fun recent(page: Int, limit: Int) = call { api, token -> api.recent(token, page, limit) }
    override suspend fun summary() = call { api, token -> api.summary(token) }

    private suspend fun <T> call(block: suspend (ScrobblerApi, String) -> T): T {
        val creds = auth.current() ?: throw SessionExpiredException()
        try {
            return block(apiFor(creds.serverUrl), creds.bearerToken)
        } catch (e: ApiException) {
            throw if (e.status == 401) SessionExpiredException() else e
        }
    }
}
