package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeDao {
    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun observeProfile(id: String = UserProfileEntity.LOCAL_PROFILE_ID): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun getProfileOnce(id: String = UserProfileEntity.LOCAL_PROFILE_ID): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isSignedIn = :signedIn WHERE id = :id")
    suspend fun setSignedIn(signedIn: Boolean, id: String = UserProfileEntity.LOCAL_PROFILE_ID)

    @Query("SELECT * FROM chat_threads WHERE isBlocked = 0 ORDER BY isSelfNote DESC, lastMessageAt DESC")
    fun observeActiveThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE isBlocked = 1 ORDER BY lastMessageAt DESC")
    fun observeBlockedThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    fun observeThreadById(threadId: Long): Flow<ChatThreadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long

    @Query("UPDATE chat_threads SET lastMessagePreview = :preview, lastMessageAt = :timestamp WHERE id = :threadId")
    suspend fun updateThreadLastMessage(threadId: Long, preview: String, timestamp: Long)

    @Query("UPDATE chat_threads SET isBlocked = :blocked WHERE id = :threadId")
    suspend fun setThreadBlocked(threadId: Long, blocked: Boolean)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessagesForThread(threadId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("SELECT * FROM safety_reports ORDER BY createdAt DESC")
    fun observeSafetyReports(): Flow<List<SafetyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafetyReport(report: SafetyReportEntity): Long

    @Query("UPDATE safety_reports SET status = :status WHERE id = :reportId")
    suspend fun updateReportStatus(reportId: Long, status: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM chat_threads")
    suspend fun clearThreads()

    @Query("DELETE FROM safety_reports")
    suspend fun clearSafetyReports()

    @Query("DELETE FROM user_profile")
    suspend fun clearProfiles()
}
