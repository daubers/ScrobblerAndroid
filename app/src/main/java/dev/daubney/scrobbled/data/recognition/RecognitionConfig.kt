package dev.daubney.scrobbled.data.recognition

import android.content.Context
import dev.daubney.scrobbled.data.auth.KeystoreBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** ACRCloud project credentials, from the project's page in the ACRCloud console. */
@Serializable
data class RecognitionConfig(val host: String, val accessKey: String, val accessSecret: String) {
    val isComplete get() = host.isNotBlank() && accessKey.isNotBlank() && accessSecret.isNotBlank()

    /** Tidies what users paste: "https://identify-eu-west-1.acrcloud.com/" becomes the bare host. */
    fun normalized() = copy(
        host = host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/'),
        accessKey = accessKey.trim(), accessSecret = accessSecret.trim(),
    )
}

interface RecognitionConfigStore {
    fun load(): RecognitionConfig?
    fun save(config: RecognitionConfig)
    fun clear()
}

class KeystoreRecognitionConfigStore(context: Context) : RecognitionConfigStore {
    private val box = KeystoreBox(context, prefsName = "recognition", alias = "scrobbled_recognition")

    override fun load(): RecognitionConfig? = box.load()?.let {
        try { Json.decodeFromString<RecognitionConfig>(it).takeIf { c -> c.isComplete } } catch (e: Exception) { box.clear(); null }
    }

    override fun save(config: RecognitionConfig) = box.save(Json.encodeToString(RecognitionConfig.serializer(), config.normalized()))
    override fun clear() = box.clear()
}
