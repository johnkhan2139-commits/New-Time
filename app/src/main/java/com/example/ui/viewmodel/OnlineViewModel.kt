package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.TimePreferences
import com.example.data.preferences.TurnServerConfig
import com.example.data.remote.CallSession
import com.example.data.remote.IceCandidateDoc
import com.example.data.remote.OnlineChat
import com.example.data.remote.OnlineMediaHelper
import com.example.data.remote.OnlineMessage
import com.example.data.remote.OnlineRepository
import com.example.data.remote.OnlineSafetyReport
import com.example.data.remote.UserDirectoryProfile
import com.example.data.remote.UserPrivateInfo
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DirectoryUiState {
    data object Loading : DirectoryUiState
    data class Success(val users: List<UserDirectoryProfile>) : DirectoryUiState
    data class Error(val message: String) : DirectoryUiState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnlineViewModel(
    private val repository: OnlineRepository,
    val currentUserId: String,
    private val preferences: TimePreferences,
    private val mediaHelper: OnlineMediaHelper
) : ViewModel() {

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _directoryError = MutableStateFlow<String?>(null)

    val isDarkTheme: StateFlow<Boolean> = preferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val turnConfig: StateFlow<TurnServerConfig> = preferences.turnConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TurnServerConfig())

    val isAdmin: Boolean
        get() {
            val user = Firebase.auth.currentUser
            return user != null && user.isEmailVerified && user.email == "maminul78338@gmail.com"
        }

    val myProfile: StateFlow<UserDirectoryProfile?> = repository.observeMyProfile(currentUserId)
        .catch { e ->
            handleFlowException("profile", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val myPrivateInfo: StateFlow<UserPrivateInfo?> = repository.observeMyPrivateInfo(currentUserId)
        .catch { e ->
            handleFlowException("private info", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allUsersFlow: StateFlow<List<UserDirectoryProfile>> = repository.observeDirectoryUsers()
        .catch { e ->
            handleFlowException("directory", e)
            _directoryError.value = e.localizedMessage ?: "Failed to load online directory"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val onlineFriends: StateFlow<List<UserDirectoryProfile>> = combine(
        allUsersFlow,
        myPrivateInfo
    ) { users, priv ->
        val blocked = priv?.blockedUids?.toSet() ?: emptySet()
        users.filter { it.userId != currentUserId && it.userId !in blocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedUsers: StateFlow<List<UserDirectoryProfile>> = combine(
        allUsersFlow,
        myPrivateInfo
    ) { users, priv ->
        val blocked = priv?.blockedUids?.toSet() ?: emptySet()
        users.filter { it.userId in blocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawChatsFlow = repository.observeMyChats(currentUserId)
        .catch { e ->
            handleFlowException("chats", e)
            emit(emptyList())
        }

    val activeChats: StateFlow<List<OnlineChat>> = combine(
        rawChatsFlow,
        myPrivateInfo
    ) { chats, priv ->
        val blocked = priv?.blockedUids?.toSet() ?: emptySet()
        chats.filter { chat ->
            if (chat.groupChat) true
            else chat.memberUids.none { it != currentUserId && it in blocked }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reports: StateFlow<List<OnlineSafetyReport>> = repository.observeReports(currentUserId, isAdmin)
        .catch { e ->
            handleFlowException("safety reports", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calls: StateFlow<List<CallSession>> = repository.observeCallsForUser(currentUserId)
        .catch { e ->
            handleFlowException("calls", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomingRingingCall: StateFlow<CallSession?> = combine(
        calls,
        _activeCallId
    ) { list, currentActiveId ->
        list.firstOrNull {
            it.calleeId == currentUserId &&
                it.status == "RINGING" &&
                it.id != currentActiveId
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeCallSession: StateFlow<CallSession?> = _activeCallId
        .flatMapLatest { callId ->
            if (callId.isNullOrBlank()) flowOf(null)
            else repository.observeCallSession(callId).catch { e ->
                handleFlowException("active call", e)
                emit(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val user = Firebase.auth.currentUser
        val fallbackName = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "TIME User"
        viewModelScope.launch {
            repository.ensureUserDirectoryProfile(
                uid = currentUserId,
                displayName = fallbackName,
                phoneNumber = user?.phoneNumber
            ).onFailure { err ->
                _statusMessage.tryEmit("Profile sync warning: ${err.message}")
            }
        }
    }

    private fun handleFlowException(label: String, error: Throwable) {
        if (error is CancellationException && error !is FirebaseFirestoreException) {
            throw error
        }
        _statusMessage.tryEmit("Unable to sync $label: ${error.localizedMessage ?: "Permission or network error"}")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkTheme(enabled) }
    }

    fun saveTurnConfig(config: TurnServerConfig) {
        if (config.turnUrl.isNotBlank() &&
            !config.turnUrl.startsWith("turn:") &&
            !config.turnUrl.startsWith("turns:")
        ) {
            _statusMessage.tryEmit("TURN URL must start with turn: or turns:")
            return
        }
        viewModelScope.launch {
            preferences.saveTurnConfig(config)
            _statusMessage.tryEmit("STUN / TURN configuration saved.")
        }
    }

    fun observeChat(chatId: String): StateFlow<OnlineChat?> =
        repository.observeChat(chatId)
            .catch { e ->
                handleFlowException("chat", e)
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun observeMessages(chatId: String): StateFlow<List<OnlineMessage>> =
        repository.observeMessages(chatId, currentUserId)
            .catch { e ->
                handleFlowException("messages", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeIceCandidates(callId: String): StateFlow<List<IceCandidateDoc>> =
        repository.observeIceCandidates(callId, currentUserId)
            .catch { e ->
                handleFlowException("ICE candidates", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openDirectChat(friend: UserDirectoryProfile, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val myName = myProfile.value?.displayName ?: "You"
            repository.createOrOpenDirectChat(currentUserId, myName, friend)
                .onSuccess(onReady)
                .onFailure { _statusMessage.tryEmit(it.localizedMessage ?: "Failed to open chat") }
        }
    }

    fun createGroupChat(
        groupName: String,
        selectedFriends: List<UserDirectoryProfile>,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val myName = myProfile.value?.displayName ?: "You"
            repository.createGroupChat(currentUserId, myName, groupName, selectedFriends)
                .onSuccess(onReady)
                .onFailure { _statusMessage.tryEmit(it.localizedMessage ?: "Failed to create group") }
        }
    }

    fun sendMessage(chat: OnlineChat, body: String, mediaUri: Uri?) {
        viewModelScope.launch {
            val encodedMedia = if (mediaUri != null) {
                val payload = mediaHelper.encodeChatMedia(mediaUri)
                if (payload == null) {
                    _statusMessage.tryEmit("Could not process selected photo/video.")
                    return@launch
                }
                payload
            } else null

            val senderName = myProfile.value?.displayName ?: "TIME User"
            repository.sendMessage(
                chatId = chat.id,
                senderUid = currentUserId,
                senderName = senderName,
                memberUids = chat.memberUids,
                body = body,
                media = encodedMedia
            ).onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to send message")
            }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(chatId, messageId)
                .onFailure { _statusMessage.tryEmit("Only the sender can delete this message.") }
        }
    }

    fun deleteChat(chatId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
                .onSuccess {
                    _statusMessage.tryEmit("Conversation deleted.")
                    onDeleted()
                }
                .onFailure { _statusMessage.tryEmit("Only the creator can delete this chat.") }
        }
    }

    fun saveProfile(
        displayName: String,
        handle: String,
        bio: String,
        phoneNumber: String,
        avatarUri: Uri?
    ) {
        viewModelScope.launch {
            val encodedAvatar = if (avatarUri != null) mediaHelper.encodeAvatarToBase64(avatarUri) else null
            repository.updateDirectoryProfile(
                uid = currentUserId,
                displayName = displayName,
                handle = handle,
                bio = bio,
                avatarBase64 = encodedAvatar,
                phoneNumber = phoneNumber
            ).onSuccess {
                _statusMessage.tryEmit("Online profile updated.")
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Failed to update profile")
            }
        }
    }

    fun setUserBlocked(targetUid: String, blocked: Boolean) {
        viewModelScope.launch {
            repository.setUserBlocked(currentUserId, targetUid, blocked)
                .onSuccess {
                    _statusMessage.tryEmit(if (blocked) "User blocked." else "User unblocked.")
                }
                .onFailure {
                    _statusMessage.tryEmit("Failed to update block list.")
                }
        }
    }

    fun submitReport(
        targetUserId: String,
        targetName: String,
        category: String,
        details: String
    ) {
        viewModelScope.launch {
            repository.submitReport(currentUserId, targetUserId, targetName, category, details)
                .onSuccess { _statusMessage.tryEmit("Safety report submitted to moderation queue.") }
                .onFailure { _statusMessage.tryEmit(it.localizedMessage ?: "Failed to submit report") }
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            repository.updateReportStatus(reportId, status)
                .onSuccess { _statusMessage.tryEmit("Report marked $status.") }
                .onFailure { _statusMessage.tryEmit("Admin permissions required to moderate reports.") }
        }
    }

    fun startCall(
        calleeId: String,
        calleeName: String,
        chatId: String?,
        callType: String,
        onCallStarted: (String) -> Unit
    ) {
        viewModelScope.launch {
            val callerName = myProfile.value?.displayName ?: "TIME User"
            repository.createCallSession(
                callerId = currentUserId,
                callerName = callerName,
                calleeId = calleeId,
                calleeName = calleeName,
                chatId = chatId,
                callType = callType
            ).onSuccess { callId ->
                _activeCallId.value = callId
                onCallStarted(callId)
            }.onFailure {
                _statusMessage.tryEmit(it.localizedMessage ?: "Could not start call")
            }
        }
    }

    fun acceptIncomingCall(callId: String, onOpenCallScreen: () -> Unit) {
        _activeCallId.value = callId
        onOpenCallScreen()
    }

    fun declineIncomingCall(callId: String) {
        viewModelScope.launch {
            repository.updateCallStatus(callId, "DECLINED")
            if (_activeCallId.value == callId) {
                _activeCallId.value = null
            }
        }
    }

    fun updateCallOffer(callId: String, offerSdp: String) {
        viewModelScope.launch {
            repository.updateCallOffer(callId, offerSdp)
        }
    }

    fun answerCall(callId: String, answerSdp: String) {
        viewModelScope.launch {
            repository.answerCall(callId, answerSdp)
        }
    }

    fun sendIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        viewModelScope.launch {
            repository.addIceCandidate(
                callId = callId,
                senderId = currentUserId,
                participantUids = participantUids,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                candidate = candidate
            )
        }
    }

    fun endActiveCall(callId: String) {
        viewModelScope.launch {
            repository.updateCallStatus(callId, "ENDED")
            if (_activeCallId.value == callId) {
                _activeCallId.value = null
            }
        }
    }

    fun clearActiveCallOverlay() {
        _activeCallId.value = null
    }

    fun markOfflineForSignOut() {
        viewModelScope.launch {
            repository.updateOnlineStatus(currentUserId, false)
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteUserAccountData(currentUserId)
                .onSuccess {
                    preferences.clearAll()
                    _statusMessage.tryEmit("Account & cloud profile deleted.")
                    onComplete()
                }
                .onFailure {
                    _statusMessage.tryEmit(it.localizedMessage ?: "Re-authenticate to delete account.")
                }
        }
    }

    class Factory(
        private val repository: OnlineRepository,
        private val userId: String,
        private val preferences: TimePreferences,
        private val mediaHelper: OnlineMediaHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnlineViewModel(repository, userId, preferences, mediaHelper) as T
        }
    }
}
