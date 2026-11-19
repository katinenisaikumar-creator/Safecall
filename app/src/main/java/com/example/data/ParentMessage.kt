package com.example.data

import java.util.UUID

data class ParentMessage(
    val id: String = UUID.randomUUID().toString(),
    val callerDisplay: String,
    val callerNumber: String,
    val messageText: String,
    val studentStatusAtCall: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false
)
