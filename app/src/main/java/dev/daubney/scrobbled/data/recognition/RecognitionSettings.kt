package dev.daubney.scrobbled.data.recognition

import dev.daubney.scrobbled.data.search.TrackResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The current ACRCloud keys, observable so screens and the recognizer see changes immediately. */
class RecognitionSettings(private val store: RecognitionConfigStore) {
    private val _config = MutableStateFlow(store.load())
    val config: StateFlow<RecognitionConfig?> = _config.asStateFlow()

    /** True while no complete keys are saved, so results come from [FakeRecognizer]. */
    val isDemo get() = _config.value?.isComplete != true

    fun save(config: RecognitionConfig) {
        val tidy = config.normalized()
        store.save(tidy)
        _config.value = tidy
    }

    fun clear() {
        store.clear()
        _config.value = null
    }
}

/** Uses [real] once ACRCloud keys are saved, otherwise [demo]. Checked on every call so Settings changes apply at once. */
class SwitchingRecognizer(
    private val settings: RecognitionSettings,
    private val real: Recognizer,
    private val demo: Recognizer,
) : Recognizer {
    override suspend fun recognize(clip: AudioClip): TrackResult? =
        if (settings.isDemo) demo.recognize(clip) else real.recognize(clip)
}
