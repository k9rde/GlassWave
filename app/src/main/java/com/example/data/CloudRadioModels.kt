package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudRoom(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val roomCode: String = "",
    val roomName: String = "",
    val memberUids: List<String> = emptyList(),
    val activeSpeakerUid: String = "",
    val activeSpeakerName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VoicePacket(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val audioBase64: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
