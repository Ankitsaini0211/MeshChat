package com.example.meshchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meshchat.ChatMessage
import com.example.meshchat.ui.components.EmptyState
import com.example.meshchat.ui.components.MessageBubble
import com.example.meshchat.ui.components.MeshChatTopBar

@Composable
fun ChatScreen(
    peerName: String,
    isConnected: Boolean,
    statusText: String,
    messages: List<ChatMessage>,
    onSendClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var messageText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            MeshChatTopBar(
                title = peerName,
                subtitle = if (isConnected) "● Connected" else statusText,
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyState(
                        icon = "💬",
                        title = "Start the conversation",
                        description = "Say hello! Messages travel over nearby Bluetooth Low Energy connections."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            // Message Composer Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a message…") },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = true,
                    readOnly = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val textToSend = messageText
                        if (textToSend.trim().isNotEmpty()) {
                            onSendClick(textToSend)
                            messageText = ""
                        }
                    },
                    enabled = isConnected && messageText.trim().isNotEmpty(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }
}
