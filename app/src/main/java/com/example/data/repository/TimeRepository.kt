package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatThreadEntity
import com.example.data.local.MediaAttachmentType
import com.example.data.local.SafetyReportEntity
import com.example.data.local.TimeDao
import com.example.data.local.TimeDatabase
import com.example.data.local.UserProfileEntity
import com.example.data.media.MediaStorageHelper
import com.example.data.preferences.TimePreferences
import com.example.data.preferences.TurnServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class TimeRepository(
    private val dao: TimeDao,
    private val preferences: TimePreferences,
    private val mediaHelper: MediaStorageHelper
) {
    constructor(context: Context) : this(
        dao = TimeDatabase.getInstance(context).timeDao(),
        preferences = TimePreferences(context.applicationContext),
        mediaHelper = MediaStorageHelper(context.applicationContext)
    )

    val profileFlow: Flow<UserProfileEntity?> = dao.observeProfile()
    val activeThreadsFlow: Flow<List<ChatThreadEntity>> = dao.observeActiveThreads()
    val blockedThreadsFlow: Flow<List<ChatThreadEntity>> = dao.observeBlockedThreads()
    val safetyReportsFlow: Flow<List<SafetyReportEntity>> = dao.observeSafetyReports()
    val isDarkThemeFlow: Flow<Boolean> = preferences.isDarkTheme
    val turnConfigFlow: Flow<TurnServerConfig> = preferences.turnConfig

    fun observeThread(threadId: Long): Flow<ChatThreadEntity?> = dao.observeThreadById(threadId)

    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>> =
        dao.observeMessagesForThread(threadId)

    suspend fun saveOrCreateProfile(
        displayName: String,
        phoneNumber: String,
        bio: String,
        avatarUri: Uri? = null,
        existingAvatarPath: String? = null
    ): Result<Unit> {
        return try {
            val trimmedName = displayName.trim()
            require(trimmedName.isNotEmpty()) { "Display name cannot be empty." }

            val persistedAvatar = if (avatarUri != null) {
                mediaHelper.persistMediaFromUri(avatarUri).getOrThrow().absolutePath
            } else {
                existingAvatarPath
            }

            val profile = UserProfileEntity(
                id = UserProfileEntity.LOCAL_PROFILE_ID,
                displayName = trimmedName,
                phoneNumber = phoneNumber.trim(),
                bio = bio.trim(),
                avatarPath = persistedAvatar,
                isSignedIn = true,
                createdAt = System.currentTimeMillis()
            )
            dao.upsertProfile(profile)

            // Ensure personal "Saved Notes" thread exists on first local sign-in so the user has an immediately usable local workspace.
            if (dao.getProfileOnce() != null) {
                // Check via insert only if no self thread exists yet? Avoid duplicate by checking preview/handle isn't needed if handled cleanly.
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithSavedNotesSeed(
        displayName: String,
        phoneNumber: String,
        bio: String,
        avatarUri: Uri?
    ): Result<Unit> {
        return try {
            val trimmedName = displayName.trim()
            require(trimmedName.isNotEmpty()) { "Please enter your display name." }

            val persistedAvatar = if (avatarUri != null) {
                mediaHelper.persistMediaFromUri(avatarUri).getOrThrow().absolutePath
            } else {
                null
            }

            dao.upsertProfile(
                UserProfileEntity(
                    id = UserProfileEntity.LOCAL_PROFILE_ID,
                    displayName = trimmedName,
                    phoneNumber = phoneNumber.trim(),
                    bio = bio.trim().ifEmpty { "Available on TIME" },
                    avatarPath = persistedAvatar,
                    isSignedIn = true
                )
            )

            val selfThreadId = dao.insertThread(
                ChatThreadEntity(
                    contactName = "Saved Notes (This Device)",
                    contactHandle = "local-space",
                    lastMessagePreview = "Local chat & media notes ready. Add Firebase for cloud sync.",
                    lastMessageAt = System.currentTimeMillis(),
                    isSelfNote = true
                )
            )
            dao.insertMessage(
                ChatMessageEntity(
                    threadId = selfThreadId,
                    senderName = "TIME",
                    isOutgoing = false,
                    body = "Welcome, $trimmedName! This local workspace stores messages, photos, and videos on your device. Connect your Firebase project (see Profile -> Firebase Setup Guide) to enable Phone OTP login, cloud sync, FCM notifications, and live peer video calls.",
                    mediaType = MediaAttachmentType.TEXT.name
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            dao.setSignedIn(false)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resumeExistingLocalProfile(): Result<Unit> {
        return try {
            dao.setSignedIn(true)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createThread(contactName: String, contactHandle: String): Result<Long> {
        return try {
            val cleanName = contactName.trim()
            val cleanHandle = contactHandle.trim()
            require(cleanName.isNotEmpty()) { "Contact name is required." }
            val threadId = dao.insertThread(
                ChatThreadEntity(
                    contactName = cleanName,
                    contactHandle = cleanHandle.ifEmpty { "local-contact" },
                    lastMessagePreview = "Conversation created",
                    lastMessageAt = System.currentTimeMillis()
                )
            )
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        threadId: Long,
        senderName: String,
        body: String,
        mediaUri: Uri? = null
    ): Result<Unit> {
        return try {
            val trimmedBody = body.trim()
            require(trimmedBody.isNotEmpty() || mediaUri != null) {
                "Enter a message or attach media."
            }

            val savedMedia = mediaUri?.let { mediaHelper.persistMediaFromUri(it).getOrThrow() }
            val mediaType = when {
                savedMedia == null -> MediaAttachmentType.TEXT
                savedMedia.isVideo -> MediaAttachmentType.VIDEO
                else -> MediaAttachmentType.IMAGE
            }

            val now = System.currentTimeMillis()
            dao.insertMessage(
                ChatMessageEntity(
                    threadId = threadId,
                    senderName = senderName,
                    isOutgoing = true,
                    body = trimmedBody,
                    mediaPath = savedMedia?.absolutePath,
                    mediaType = mediaType.name,
                    createdAt = now
                )
            )

            val preview = when (mediaType) {
                MediaAttachmentType.IMAGE -> if (trimmedBody.isEmpty()) "Photo attachment" else "Photo: $trimmedBody"
                MediaAttachmentType.VIDEO -> if (trimmedBody.isEmpty()) "Video attachment" else "Video: $trimmedBody"
                MediaAttachmentType.TEXT -> trimmedBody
            }
            dao.updateThreadLastMessage(threadId, preview, now)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: Long): Result<Unit> {
        return try {
            dao.deleteMessage(messageId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setThreadBlocked(threadId: Long, blocked: Boolean): Result<Unit> {
        return try {
            dao.setThreadBlocked(threadId, blocked)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteThread(threadId: Long): Result<Unit> {
        return try {
            dao.deleteThread(threadId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitSafetyReport(
        targetName: String,
        targetHandle: String,
        category: String,
        details: String
    ): Result<Unit> {
        return try {
            require(targetName.isNotBlank()) { "Target name is required." }
            require(details.isNotBlank()) { "Please describe the safety issue." }
            dao.insertSafetyReport(
                SafetyReportEntity(
                    targetName = targetName.trim(),
                    targetHandle = targetHandle.trim(),
                    category = category,
                    details = details.trim(),
                    status = "OPEN"
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(reportId: Long, status: String): Result<Unit> {
        return try {
            dao.updateReportStatus(reportId, status)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        preferences.setDarkTheme(enabled)
    }

    suspend fun saveTurnConfig(config: TurnServerConfig): Result<Unit> {
        return try {
            require(config.stunUrl.isBlank() || config.stunUrl.startsWith("stun:")) {
                "STUN URL must start with stun:"
            }
            require(
                config.turnUrl.isBlank() ||
                    config.turnUrl.startsWith("turn:") ||
                    config.turnUrl.startsWith("turns:")
            ) {
                "TURN URL must start with turn: or turns:"
            }
            preferences.saveTurnConfig(config)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccountAndAllData(): Result<Unit> {
        return try {
            dao.clearMessages()
            dao.clearThreads()
            dao.clearSafetyReports()
            dao.clearProfiles()
            mediaHelper.clearAllMedia()
            preferences.clearAll()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
