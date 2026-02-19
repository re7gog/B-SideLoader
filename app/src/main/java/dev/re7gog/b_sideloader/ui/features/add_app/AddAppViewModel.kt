package dev.re7gog.b_sideloader.ui.features.add_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAppViewModel @Inject constructor(
    private val appsRepository: AppsRepository
) : ViewModel() {
    var name by mutableStateOf("")

    val canAdd: Boolean
        get() = name.isNotBlank()  // May be more difficult conditions in the future

    fun addApp(onSuccess: () -> Unit) {
        // Test
        viewModelScope.launch {
            val app = AppEntity(
                sourceType = "github",
                name = name
            )
            val details = GithubDetailsEntity(
                id = 0,
                url = "https://github.com/$name",
                usePrereleases = false
            )
            val appWithDetails = AppType.GithubApp(app, details)
            appsRepository.addApp(appWithDetails)
            onSuccess()
        }
    }
}