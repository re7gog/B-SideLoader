package dev.re7gog.b_sideloader.ui.feature.aisettings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.domain.ai.LanguageModelGateway
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AiSettingsUiState(
    val isLoaded: Boolean = false,
    val mode: AiMode = AiMode.Default,
    val provider: AiProvider = AiProvider.Default,
    /** What is typed in each provider's fields: keys, models, and the OpenAI base URL. */
    val apiKeys: ImmutableMap<AiProvider, String> = persistentMapOf(),
    val models: ImmutableMap<AiProvider, String> = persistentMapOf(),
    val openAiBaseUrl: String = "",
    /** Null while being worked out. */
    val availability: AiAvailability? = null,
    /** Bytes of the on-device model downloaded so far, while a download runs. */
    val downloadedBytes: Long? = null,
)

/**
 * The AI settings page.
 *
 * What the fields show is held here and written through on every keystroke, as the GitHub token
 * field does: echoing a text field through DataStore and back would move the cursor under the
 * user's finger whenever a write and an emission crossed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val languageModel: LanguageModelGateway,
) : ViewModel() {

    private val fields = MutableStateFlow(Fields())
    private val downloadedBytes = MutableStateFlow<Long?>(null)

    /** Bumped when a key changes or a download ends, which settings alone would not show. */
    private val availabilityTrigger = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private val ai = settingsRepository.settings.map { it.ai }.distinctUntilChanged()

    // Starts as unknown, so the page renders before AICore has answered.
    private val availability = combine(ai, availabilityTrigger) { settings, _ -> settings }
        .mapLatest<AiSettings, AiAvailability?> { languageModel.availability() }
        .onStart { emit(null) }

    val uiState: StateFlow<AiSettingsUiState> = combine(
        ai,
        fields,
        availability,
        downloadedBytes,
    ) { ai, fields, availability, downloaded ->
        AiSettingsUiState(
            isLoaded = fields.isLoaded,
            mode = ai.mode,
            provider = ai.provider,
            apiKeys = fields.apiKeys.toImmutableMap(),
            models = fields.models.toImmutableMap(),
            openAiBaseUrl = fields.openAiBaseUrl,
            availability = availability,
            downloadedBytes = downloaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), AiSettingsUiState())

    private var download: Job? = null

    init {
        viewModelScope.launch {
            val ai: AiSettings = settingsRepository.current().ai
            val keys = AiProvider.entries.associateWith { secretsRepository.getAiApiKey(it).orEmpty() }
            fields.value = Fields(
                isLoaded = true,
                apiKeys = keys,
                models = AiProvider.entries.associateWith { ai.models[it].orEmpty() },
                openAiBaseUrl = ai.openAiBaseUrl,
            )
        }
    }

    fun setMode(mode: AiMode) {
        viewModelScope.launch { settingsRepository.setAiMode(mode) }
    }

    fun setProvider(provider: AiProvider) {
        viewModelScope.launch { settingsRepository.setAiProvider(provider) }
    }

    fun setApiKey(provider: AiProvider, key: String) {
        fields.update { it.copy(apiKeys = it.apiKeys + (provider to key)) }
        viewModelScope.launch {
            suspendRunCatching { secretsRepository.setAiApiKey(provider, key) }
                .onSuccess { availabilityTrigger.update { it + 1 } }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    fun setModel(provider: AiProvider, model: String) {
        fields.update { it.copy(models = it.models + (provider to model)) }
        viewModelScope.launch { settingsRepository.setAiModel(provider, model) }
    }

    fun setOpenAiBaseUrl(url: String) {
        fields.update { it.copy(openAiBaseUrl = url) }
        viewModelScope.launch { settingsRepository.setOpenAiBaseUrl(url) }
    }

    /** Fetches the on-device model now, rather than on its first use. */
    fun downloadModel() {
        if (download?.isActive == true) return
        download = viewModelScope.launch {
            downloadedBytes.value = 0L
            suspendRunCatching { languageModel.prepare().collect { downloadedBytes.value = it } }
                .onFailure { _messages.tryEmit(it.toUiText()) }
            downloadedBytes.value = null
            availabilityTrigger.update { it + 1 }
        }
    }

    private data class Fields(
        val isLoaded: Boolean = false,
        val apiKeys: Map<AiProvider, String> = emptyMap(),
        val models: Map<AiProvider, String> = emptyMap(),
        val openAiBaseUrl: String = "",
    )

    private companion object {
        const val SUBSCRIPTION_MS = 5_000L
    }
}
