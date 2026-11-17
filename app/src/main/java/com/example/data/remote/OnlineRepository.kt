package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class OnlineRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private val usersRef get() = firestore.collection("users")
    private val chatsRef get() = firestore.collection("chats")
    private val callsRef get() = firestore.collection("calls")
    private val reportsRef get() = firestore.collection("reports")

    fun observeDirectoryUsers(): Flow<List<UserDirectoryProfile>> =
        usersRef
            .limit(100)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(UserDirectoryProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<UserDirectoryProfile> { it.online }.thenBy { it.displayName.lowercase() })
            }

    fun observeMyProfile(uid: String): Flow<UserDirectoryProfile?> =
        usersRef.document(uid)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserDirectoryProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }

    fun observeMyPrivateInfo(uid: String): Flow<UserPrivateInfo?> =
        usersRef.document(uid)
            .collection("private")
            .document("info")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }

    suspend fun ensureUserDirectoryProfile(
        uid: String,
        displayName: String,
        phoneNumber: String? = null
    ): Result<Unit> {
        val docRef = usersRef.document(uid)
        val privateRef = docRef.collection("private").document("info")
        val cleanName = displayName.trim().ifBlank { "TIME User" }.take(60)
        val handle = cleanName.lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "timeuser" }
            .take(40) + "_" + uid.take(4)

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val data = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "handle" to handle,
                    "bio" to "Available on TIME",
                    "devicePlatform" to "ANDROID",
                    "online" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            } else {
                docRef.update(
                    mapOf(
                        "devicePlatform" to "ANDROID",
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val privSnap = privateRef.get().await()
            val cleanPhone = phoneNumber?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
            if (!privSnap.exists()) {
                val privMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "blockedUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone != null) {
                    privMap["phoneNumber"] = cleanPhone
                }
                privateRef.set(privMap).await()
            } else if (cleanPhone != null) {
                privateRef.update(
                    mapOf(
                        "phoneNumber" to cleanPhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun updateDirectoryProfile(
        uid: String,
        displayName: String,
        handle: String,
        bio: String,
        avatarBase64: String? = null,
        phoneNumber: String? = null
    ): Result<Unit> {
        val cleanName = displayName.trim().take(60)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        }
        val cleanHandle = handle.trim().ifBlank {
            cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "timeuser" }
        }.take(50)
        val cleanBio = bio.trim().take(200)

        return try {
            val updates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "handle" to cleanHandle,
                "bio" to cleanBio,
                "devicePlatform" to "ANDROID",
                "online" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarBase64.isNullOrBlank()) {
                updates["avatarBase64"] = avatarBase64
            }
            usersRef.document(uid).update(updates).await()

            val cleanPhone = phoneNumber?.trim()?.take(32)
            if (!cleanPhone.isNullOrBlank()) {
                usersRef.document(uid)
                    .collection("private")
                    .document("info")
                    .update(
                        mapOf(
                            "phoneNumber" to cleanPhone,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun updateOnlineStatus(uid: String, online: Boolean) {
        try {
            usersRef.document(uid).update(
                mapOf(
                    "online" to online,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    suspend fun setUserBlocked(myUid: String, targetUid: String, blocked: Boolean): Result<Unit> {
        if (targetUid.isBlank() || targetUid == myUid) {
            return Result.failure(IllegalArgumentException("Invalid target user"))
        }
        return try {
            val privateRef = usersRef.document(myUid).collection("private").document("info")
            val op = if (blocked) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid)
            privateRef.update(
                mapOf(
                    "blockedUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "users/$myUid/private/info")
            Result.failure(Exception(msg, e))
        }
    }

    fun observeMyChats(uid: String): Flow<List<OnlineChat>> =
        chatsRef
            .whereArrayContains("memberUids", uid)
            .limit(100)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(OnlineChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
            }

    fun observeChat(chatId: String): Flow<OnlineChat?> =
        chatsRef.document(chatId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(OnlineChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }

    suspend fun createOrOpenDirectChat(
        myUid: String,
        myName: String,
        friend: UserDirectoryProfile
    ): Result<String> {
        if (friend.userId.isBlank() || friend.userId == myUid) {
            return Result.failure(IllegalArgumentException("Choose another user from the online directory."))
        }
        val pair = listOf(myUid, friend.userId).sorted()
        val chatId = "dm_${pair[0]}_${pair[1]}"
        return try {
            val docRef = chatsRef.document(chatId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val data = mapOf(
                    "name" to friend.displayName.take(60),
                    "groupChat" to false,
                    "createdBy" to myUid,
                    "memberUids" to pair,
                    "memberNames" to listOf(
                        myName.ifBlank { "You" }.take(60),
                        friend.displayName.ifBlank { "Friend" }.take(60)
                    ),
                    "lastMessagePreview" to "Connected on TIME",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "chats/$chatId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun createGroupChat(
        myUid: String,
        myName: String,
        groupName: String,
        selectedFriends: List<UserDirectoryProfile>
    ): Result<String> {
        val cleanName = groupName.trim().take(60)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name is required."))
        }
        val memberUids = (listOf(myUid) + selectedFriends.map { it.userId }).distinct().take(50)
        val memberNames = (listOf(myName.ifBlank { "You" }) + selectedFriends.map { it.displayName })
            .distinct()
            .take(50)
        val chatId = "grp_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val data = mapOf(
                "name" to cleanName,
                "groupChat" to true,
                "createdBy" to myUid,
                "memberUids" to memberUids,
                "memberNames" to memberNames,
                "lastMessagePreview" to "Group created by ${myName.ifBlank { "Owner" }}",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatsRef.document(chatId).set(data).await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "chats/$chatId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            chatsRef.document(chatId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.DELETE, "chats/$chatId")
            Result.failure(Exception(msg, e))
        }
    }

    fun observeMessages(chatId: String, myUid: String): Flow<List<OnlineMessage>> =
        chatsRef.document(chatId)
            .collection("messages")
            .whereArrayContains("memberUids", myUid)
            .limit(200)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(OnlineMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    suspend fun sendMessage(
        chatId: String,
        senderUid: String,
        senderName: String,
        memberUids: List<String>,
        body: String,
        media: EncodedMediaPayload? = null
    ): Result<Unit> {
        val cleanBody = body.trim().take(2000)
        if (cleanBody.isEmpty() && media == null) {
            return Result.failure(IllegalArgumentException("Enter a message or attach media."))
        }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val mediaType = media?.mediaType ?: "TEXT"
        val preview = when {
            cleanBody.isNotBlank() -> cleanBody
            mediaType == "VIDEO" -> "Sent a video preview"
            mediaType == "IMAGE" -> "Sent a photo"
            else -> "Message"
        }.take(300)

        return try {
            val payload = mutableMapOf<String, Any>(
                "chatId" to chatId,
                "senderId" to senderUid,
                "senderName" to senderName.ifBlank { "TIME User" }.take(60),
                "body" to cleanBody,
                "mediaType" to mediaType,
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (media != null) {
                payload["mediaBase64"] = media.base64
                payload["mediaNote"] = media.note.take(300)
            }
            chatsRef.document(chatId).collection("messages").document(msgId).set(payload).await()
            chatsRef.document(chatId).update(
                mapOf(
                    "lastMessagePreview" to preview,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "chats/$chatId/messages/$msgId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            chatsRef.document(chatId).collection("messages").document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.DELETE, "chats/$chatId/messages/$messageId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun createCallSession(
        callerId: String,
        callerName: String,
        calleeId: String,
        calleeName: String,
        chatId: String?,
        callType: String,
        offerSdp: String? = null
    ): Result<String> {
        if (callerId == calleeId) {
            return Result.failure(IllegalArgumentException("Select a remote contact to place a call."))
        }
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val participants = listOf(callerId, calleeId)
        return try {
            val payload = mutableMapOf<String, Any>(
                "callerId" to callerId,
                "callerName" to callerName.ifBlank { "TIME User" }.take(60),
                "calleeId" to calleeId,
                "calleeName" to calleeName.ifBlank { "Friend" }.take(60),
                "callType" to if (callType == "AUDIO") "AUDIO" else "VIDEO",
                "status" to "RINGING",
                "participantUids" to participants,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!chatId.isNullOrBlank()) {
                payload["chatId"] = chatId
            }
            if (!offerSdp.isNullOrBlank()) {
                payload["offerSdp"] = offerSdp.take(30000)
            }
            callsRef.document(callId).set(payload).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "calls/$callId")
            Result.failure(Exception(msg, e))
        }
    }

    fun observeCallsForUser(uid: String): Flow<List<CallSession>> =
        callsRef
            .whereArrayContains("participantUids", uid)
            .limit(30)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }

    fun observeCallSession(callId: String): Flow<CallSession?> =
        callsRef.document(callId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }

    suspend fun updateCallOffer(callId: String, offerSdp: String): Result<Unit> {
        return try {
            callsRef.document(callId).update(
                mapOf(
                    "offerSdp" to offerSdp.take(30000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String): Result<Unit> {
        return try {
            callsRef.document(callId).update(
                mapOf(
                    "status" to "ACTIVE",
                    "answerSdp" to answerSdp.take(30000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun updateCallStatus(callId: String, newStatus: String): Result<Unit> {
        return try {
            callsRef.document(callId).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        senderId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ): Result<Unit> {
        val candId = "ice_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        return try {
            callsRef.document(callId).collection("candidates").document(candId).set(
                mapOf(
                    "senderId" to senderId,
                    "participantUids" to participantUids,
                    "sdpMid" to sdpMid.take(128),
                    "sdpMLineIndex" to sdpMLineIndex,
                    "candidate" to candidate.take(4000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "calls/$callId/candidates/$candId")
            Result.failure(Exception(msg, e))
        }
    }

    fun observeIceCandidates(callId: String, uid: String): Flow<List<IceCandidateDoc>> =
        callsRef.document(callId)
            .collection("candidates")
            .whereArrayContains("participantUids", uid)
            .limit(200)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(IceCandidateDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun submitReport(
        reporterId: String,
        targetUserId: String,
        targetName: String,
        category: String,
        details: String
    ): Result<Unit> {
        val cleanDetails = details.trim().take(1000)
        if (cleanDetails.isBlank()) {
            return Result.failure(IllegalArgumentException("Please describe the safety issue."))
        }
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            reportsRef.document(reportId).set(
                mapOf(
                    "reporterId" to reporterId,
                    "targetUserId" to targetUserId.ifBlank { "unknown" }.take(128),
                    "targetName" to targetName.ifBlank { "User" }.take(60),
                    "category" to category.ifBlank { "Other" }.take(40),
                    "details" to cleanDetails,
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.CREATE, "reports/$reportId")
            Result.failure(Exception(msg, e))
        }
    }

    fun observeReports(uid: String, isAdmin: Boolean): Flow<List<OnlineSafetyReport>> {
        val query = if (isAdmin) {
            reportsRef.limit(100)
        } else {
            reportsRef.whereEqualTo("reporterId", uid).limit(100)
        }
        return query.snapshots(MetadataChanges.EXCLUDE)
            .map { snap ->
                snap.toObjects(OnlineSafetyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        return try {
            reportsRef.document(reportId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun deleteUserAccountData(uid: String): Result<Unit> {
        return try {
            usersRef.document(uid).collection("private").document("info").delete().await()
            usersRef.document(uid).delete().await()
            runCatching { auth.currentUser?.delete()?.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(Exception(msg, e))
        }
    }
}
