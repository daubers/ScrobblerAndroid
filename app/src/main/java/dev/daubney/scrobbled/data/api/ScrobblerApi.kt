package dev.daubney.scrobbled.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class ApiApp(val name: String, val apiKey: String, val sharedSecret: String)

data class Login(val token: String, val username: String)

/** [status] is the HTTP status, or -1 for transport/parse failures. */
class ApiException(val status: Int, val code: String?, message: String) : Exception(message)

/** Client for the server's bearer-token `/api/v1` JSON API. */
class ScrobblerApi(
    baseUrl: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val base = baseUrl.trimEnd('/') + "/api/v1/"

    suspend fun login(username: String, password: String): Login {
        val body = buildJsonObject { put("username", username); put("password", password) }
        val user = send("auth/login", null, body).jsonObject
        return Login(
            token = user.str("token") ?: throw ApiException(-1, null, "Login response had no token"),
            username = user["user"]?.jsonObject?.str("username") ?: username,
        )
    }

    suspend fun listApps(token: String): List<ApiApp> =
        send("apps", token, null).jsonArray.map { it.jsonObject.toApp() }

    suspend fun createApp(token: String, name: String): ApiApp =
        send("apps", token, buildJsonObject { put("name", name) }).jsonObject.toApp()

    private fun JsonObject.toApp() = ApiApp(
        name = str("name").orEmpty(),
        apiKey = str("api_key") ?: throw ApiException(-1, null, "App had no api_key"),
        sharedSecret = str("shared_secret") ?: throw ApiException(-1, null, "App had no shared_secret"),
    )

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    /** GET when [body] is null, otherwise POST JSON. */
    private suspend fun send(path: String, token: String?, body: JsonObject?): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url((base + path).toHttpUrl()).apply {
                token?.let { header("Authorization", "Bearer $it") }
                if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
            }.build()
            val (status, text) = try {
                http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw ApiException(-1, null, e.message ?: "Network error")
            }
            val parsed = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                null
            }
            if (status !in 200..299) {
                val err = (parsed as? JsonObject)?.get("error") as? JsonObject
                throw ApiException(status, err?.str("code"), err?.str("message") ?: "Request failed ($status)")
            }
            parsed ?: throw ApiException(-1, null, "Unexpected response from server")
        }
}
