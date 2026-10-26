package com.example.meshchat

data class ChatMessage(

    val text: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis()


)