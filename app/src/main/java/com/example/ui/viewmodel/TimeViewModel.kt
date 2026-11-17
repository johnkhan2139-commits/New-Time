package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatThreadEntity
import com.example.data.local.SafetyReportEntity
import com.example.data.local.UserProfileEntity
import com.example.data.preferences.TurnServerConfig
import com.example.data.repository.TimeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data class Unauthenticated(val existingProfile: UserProfileEntity?) : SessionUiState
    data class Authenticated(val profile: UserProfileEntity) : SessionUiState
}

class TimeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TimeRepository(application.applicationContext)

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val statusMessage = _statusMessage.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val profile: StateFlow<UserProfileEntity?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _sessionReady = MutableStateFlow(false)
    val sessionState: StateFlow<SessionUiState> = kotlinx.coroutines.flow.combine(
        repository.profileFlow,
        _sessionReady
    ) { user, _ ->
        when {
            user != null && user.isSignedIn -> SessionUiState.Authenticated(user)
            else -> SessionUiState.Unauthenticated(user)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SessionUiState.Loading)

    val activeThreads: StateFlow<List<ChatThreadEntity>> = repository.activeThreadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedThreads: StateFlow<List<ChatThreadEntity>> = repository.blockedThreadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val safetyReports: StateFlow<List<SafetyReportEntity>> = repository.safetyReportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isDarkTheme: StateFlow<Boolean> = repository.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val turnConfig: StateFlow<TurnServerConfig> = repository.turnConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TurnServerConfig())

    fun observeThread(threadId: Long): StateFlow<ChatThreadEntity?> =
        repository.observeThread(threadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun observeMessages(threadId: Long): StateFlow<List<ChatMessageEntity>> =
        repository.observeMessages(threadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createProfile(
        displayName: String,
        phoneNumber: String,
        bio: String,
        avatarUri: Uri?
    ) {
        viewModelScope.launch {
            repository.signInWithSavedNotesSeed(displayName, phoneNumber, bio, avatarUri)
                .onFailure { _statusMessage.tryEmit(it.message ?: "Failed to create profile") }
        }
    }

    fun resumeExistingProfile() {
        viewModelScope.launch {
            repository.resumeExistingLocalProfile()
                .onFailure { _statusMessage.tryEmit(it.message ?: "Unable to resume profile") }
        }
    }

    fun updateProfile(
        displayName: String,
        phoneNumber: String,
        bio: String,
        avatarUri: Uri?,
        existingAvatarPath: String?
    ) {
        viewModelScope.launch {
            repository.saveOrCreateProfile(displayName, phoneNumber, bio, avatarUri, existingAvatarPath)
                .onSuccess { _statusMessage.tryEmit("Profile updated") }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Could not update profile") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
                .onFailure { _statusMessage.tryEmit(it.message ?: "Sign out failed") }
        }
    }

    fun createThread(name: String, handle: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.createThread(name, handle)
                .onSuccess { onCreated(it) }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Could not create conversation") }
        }
    }

    fun sendMessage(threadId: Long, body: String, mediaUri: Uri?) {
        viewModelScope.launch {
            val senderName = profile.value?.displayName ?: "You"
            repository.sendMessage(threadId, senderName, body, mediaUri)
                .onFailure { _statusMessage.tryEmit(it.message ?: "Failed to send message") }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onFailure { _statusMessage.tryEmit(it.message ?: "Failed to delete message") }
        }
    }

    fun blockThread(threadId: Long, blocked: Boolean) {
        viewModelScope.launch {
            repository.setThreadBlocked(threadId, blocked)
                .onSuccess {
                    _statusMessage.tryEmit(if (blocked) "User blocked locally" else "User unblocked")
                }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Action failed") }
        }
    }

    fun deleteThread(threadId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
                .onSuccess {
                    _statusMessage.tryEmit("Conversation deleted")
                    onDeleted()
                }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun submitReport(targetName: String, targetHandle: String, category: String, details: String) {
        viewModelScope.launch {
            repository.submitSafetyReport(targetName, targetHandle, category, details)
                .onSuccess { _statusMessage.tryEmit("Safety report logged for moderation") }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Could not submit report") }
        }
    }

    fun updateReportStatus(reportId: Long, status: String) {
        viewModelScope.launch {
            repository.updateReportStatus(reportId, status)
                .onSuccess { _statusMessage.tryEmit("Moderation status updated to $status") }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Could not update status") }
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(enabled) }
    }

    fun saveTurnConfig(config: TurnServerConfig) {
        viewModelScope.launch {
            repository.saveTurnConfig(config)
                .onSuccess { _statusMessage.tryEmit("ICE / TURN server settings saved") }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Invalid TURN configuration") }
        }
    }

    fun deleteAccountAndAllData() {
        viewModelScope.launch {
            repository.deleteAccountAndAllData()
                .onSuccess { _statusMessage.tryEmit("All local account data erased") }
                .onFailure { _statusMessage.tryEmit(it.message ?: "Failed to erase account") }
        }
    }
}
