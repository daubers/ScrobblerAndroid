package dev.daubney.scrobbled.data.api

import java.security.MessageDigest

/** Last.fm request signing, matching the server's `scrobbler/lastfm/signature.py`. */
object Signer {
    private val unsigned = setOf("format", "callback", "api_sig")

    fun sign(params: Map<String, String>, secret: String): String {
        val payload = params.keys.filter { it !in unsigned }.sorted()
            .joinToString("") { it + params.getValue(it) }
        return MessageDigest.getInstance("MD5")
            .digest((payload + secret).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
