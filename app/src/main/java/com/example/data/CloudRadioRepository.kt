package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class CloudRadioRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    private fun currentDisplayName(): String {
        val user = auth.currentUser
        val name = user?.displayName?.trim()
        return if (!name.isNullOrBlank()) name.take(40) else "صديق"
    }

    fun observeMyRooms(): Flow<List<CloudRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<CloudRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CloudRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeVoicePackets(roomId: String): Flow<List<VoicePacket>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/packets"
        emitAll(
            db.collection("rooms").document(roomId).collection("packets")
                .whereArrayContains("memberUids", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(6)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VoicePacket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        roomName: String,
        passcode: String,
        creatorName: String? = null
    ): Result<CloudRoom> {
        val code = generateRoomCode()
        val path = "rooms/$code"
        val cleanPasscode = passcode.trim().take(32)
        if (cleanPasscode.length < 4) {
            return Result.failure(IllegalArgumentException("يرجى إدخال رمز دخول للغرفة (4 أحرف أو أرقام على الأقل)"))
        }
        return try {
            val uid = requireUserId()
            val cleanName = roomName.trim().ifBlank { "غرفة الأصدقاء" }.take(60)
            val displayName = creatorName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            val memberNames = mapOf(uid to displayName)
            val payload = mapOf(
                "ownerId" to uid,
                "roomCode" to code,
                "roomName" to cleanName,
                "roomPasscode" to cleanPasscode,
                "memberUids" to listOf(uid),
                "memberNames" to memberNames,
                "activeSpeakerUid" to "",
                "activeSpeakerName" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("rooms").document(code).set(payload).await()
            Result.success(
                CloudRoom(
                    id = code,
                    ownerId = uid,
                    roomCode = code,
                    roomName = cleanName,
                    roomPasscode = cleanPasscode,
                    memberUids = listOf(uid),
                    memberNames = memberNames
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(
        rawCode: String,
        passcode: String,
        memberName: String? = null
    ): Result<CloudRoom> {
        val code = rawCode.trim().uppercase().filter { it.isLetterOrDigit() }
        if (code.length < 4 || code.length > 12) {
            return Result.failure(IllegalArgumentException("رمز الغرفة غير صالح"))
        }
        val path = "rooms/$code"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("rooms").document(code)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("لم يتم العثور على غرفة بهذا الرمز"))
            }
            val room = snap.toObject(
                CloudRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("بيانات الغرفة غير صالحة"))

            if (room.roomPasscode.isNotBlank() && room.roomPasscode != passcode.trim() && uid !in room.memberUids) {
                return Result.failure(IllegalArgumentException("رمز دخول الغرفة غير صحيح"))
            }

            val displayName = memberName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            val updatedNames = room.memberNames + (uid to displayName)
            if (uid !in room.memberUids) {
                if (room.memberUids.size >= 25) {
                    return Result.failure(IllegalStateException("الغرفة ممتلئة"))
                }
                val updatedMembers = room.memberUids + uid
                docRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "memberNames" to updatedNames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(room.copy(memberUids = updatedMembers, memberNames = updatedNames))
            } else {
                docRef.update(
                    mapOf(
                        "memberNames" to updatedNames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(room.copy(memberNames = updatedNames))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberDisplayName(room: CloudRoom, displayName: String): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            val clean = displayName.trim().take(40).ifBlank { currentDisplayName() }
            val updatedNames = room.memberNames + (uid to clean)
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(room: CloudRoom): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            val remaining = room.memberUids.filter { it != uid }
            if (remaining.isEmpty()) {
                if (room.ownerId == uid) {
                    db.collection("rooms").document(room.id).delete().await()
                }
                return Result.success(Unit)
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "memberUids" to remaining,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoom(roomId: String): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateActiveSpeaker(
        roomId: String,
        speaking: Boolean,
        speakerName: String? = null
    ): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val name = speakerName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            val payload = mapOf(
                "activeSpeakerUid" to if (speaking) uid else "",
                "activeSpeakerName" to if (speaking) name else "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("rooms").document(roomId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendVoicePacket(
        roomId: String,
        memberUids: List<String>,
        audioBase64: String,
        speakerName: String? = null
    ): Result<Unit> {
        val docRef = db.collection("rooms").document(roomId).collection("packets").document()
        val path = docRef.path
        return try {
            val uid = requireUserId()
            if (audioBase64.isBlank() || audioBase64.length > 64000) {
                return Result.failure(IllegalArgumentException("حجم الحزمة الصوتية غير صالح"))
            }
            val safeMembers = if (uid in memberUids) memberUids.take(25) else (memberUids + uid).take(25)
            val name = speakerName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            val payload = mapOf(
                "senderUid" to uid,
                "senderName" to name,
                "audioBase64" to audioBase64,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendPing(roomId: String, senderName: String? = null): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val name = senderName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "lastPingUid" to uid,
                    "lastPingName" to name,
                    "lastPingAt" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMuteMember(room: CloudRoom, targetUid: String): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            if (room.ownerId != uid || targetUid == room.ownerId) {
                return Result.failure(IllegalArgumentException("غير مصرح"))
            }
            val updatedMuted = if (targetUid in room.mutedUids) {
                room.mutedUids.filter { it != targetUid }
            } else {
                (room.mutedUids + targetUid).distinct().take(25)
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "mutedUids" to updatedMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun kickMember(room: CloudRoom, targetUid: String): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            if (room.ownerId != uid || targetUid == room.ownerId) {
                return Result.failure(IllegalArgumentException("غير مصرح"))
            }
            val updatedMembers = room.memberUids.filter { it != targetUid }
            val updatedMuted = room.mutedUids.filter { it != targetUid }
            val updatedNames = room.memberNames - targetUid
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "mutedUids" to updatedMuted,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomPasscode(room: CloudRoom, newPasscode: String): Result<Unit> {
        val path = "rooms/${room.id}"
        val clean = newPasscode.trim().take(32)
        if (clean.length < 4) {
            return Result.failure(IllegalArgumentException("رمز الدخول يجب أن يكون 4 أحرف أو أرقام على الأقل"))
        }
        return try {
            val uid = requireUserId()
            if (room.ownerId != uid) {
                return Result.failure(IllegalArgumentException("فقط مالك الغرفة يمكنه تغيير الرمز"))
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "roomPasscode" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(roomId: String): Flow<List<RoomChatMessage>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RoomChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).reversed()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendChatMessage(
        roomId: String,
        memberUids: List<String>,
        text: String,
        senderName: String? = null
    ): Result<Unit> {
        val cleanText = text.trim().take(400)
        if (cleanText.isBlank()) return Result.failure(IllegalArgumentException("الرسالة فارغة"))
        val docRef = db.collection("rooms").document(roomId).collection("messages").document()
        val path = docRef.path
        return try {
            val uid = requireUserId()
            val safeMembers = if (uid in memberUids) memberUids.take(25) else (memberUids + uid).take(25)
            val name = senderName?.trim()?.take(40)?.ifBlank { currentDisplayName() } ?: currentDisplayName()
            val payload = mapOf(
                "senderUid" to uid,
                "senderName" to name,
                "text" to cleanText,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }
}
