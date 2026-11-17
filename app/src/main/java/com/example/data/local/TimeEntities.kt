package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = LOCAL_PROFILE_ID,
    val displayName: String,
    val phoneNumber: String,
    val bio: String,
    val avatarPath: String? = null,
    val isSignedIn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val LOCAL_PROFILE_ID = "local_user"
    }
}

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactName: String,
    val contactHandle: String,
    val lastMessagePreview: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val isSelfNote: Boolean = false
)

enum class MediaAttachmentType {
    TEXT,
    IMAGE,
    VIDEO
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val threadId: Long,
    val senderName: String,
    val isOutgoing: Boolean,
    val body: String,
    val mediaPath: String? = null,
    val mediaType: String = MediaAttachmentType.TEXT.name,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "safety_reports")
data class SafetyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetName: String,
    val targetHandle: String,
    val category: String,
    val details: String,
    val status: String = "OPEN",
    val createdAt: Long = System.currentTimeMillis()
)
