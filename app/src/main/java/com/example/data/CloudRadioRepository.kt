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

    suspend fun createRoom(roomName: String): Result<CloudRoom> {
        val code = generateRoomCode()
        val path = "rooms/$code"
        return try {
            val uid = requireUserId()
            val cleanName = roomName.trim().ifBlank { "غرفة الأصدقاء" }.take(60)
            val payload = mapOf(
                "ownerId" to uid,
                "roomCode" to code,
                "roomName" to cleanName,
                "memberUids" to listOf(uid),
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
                    memberUids = listOf(uid)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(rawCode: String): Result<CloudRoom> {
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

            if (uid !in room.memberUids) {
                if (room.memberUids.size >= 25) {
                    return Result.failure(IllegalStateException("الغرفة ممتلئة"))
                }
                val updatedMembers = room.memberUids + uid
                docRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(room.copy(memberUids = updatedMembers))
            } else {
                Result.success(room)
            }
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

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }
}
