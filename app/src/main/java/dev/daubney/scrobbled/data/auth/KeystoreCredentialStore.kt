package dev.daubney.scrobbled.data.auth

import android.content.Context
import kotlinx.serialization.json.Json

/** Stores [Credentials] encrypted at rest. Names match the original implementation so existing installs stay signed in. */
class KeystoreCredentialStore(context: Context) : CredentialStore {
    private val box = KeystoreBox(context, prefsName = "credentials", alias = "scrobbled_credentials")

    override fun load(): Credentials? = box.load()?.let {
        try { Json.decodeFromString<Credentials>(it) } catch (e: Exception) { box.clear(); null }
    }

    override fun save(credentials: Credentials) = box.save(Json.encodeToString(Credentials.serializer(), credentials))
    override fun clear() = box.clear()
}
