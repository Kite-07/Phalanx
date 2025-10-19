package com.kite.phalanx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SpamDetailActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"

        setContent {
            PhalanxTheme {
                var messages by remember { mutableStateOf(readSmsMessages(sender)) }
                var showMenu by remember { mutableStateOf(false) }
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                var showUnblockConfirmDialog by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(sender) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Unblock number") },
                                        onClick = {
                                            showMenu = false
                                            showUnblockConfirmDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete conversation") },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirmDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    SpamDetailScreen(
                        messages = messages,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete Conversation?") },
                        text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirmDialog = false
                                if (SmsOperations.deleteThread(this@SpamDetailActivity, sender)) {
                                    finish()
                                }
                            }) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showUnblockConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showUnblockConfirmDialog = false },
                        title = { Text("Unblock this number?") },
                        text = { Text("$sender will be able to call and message you again.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showUnblockConfirmDialog = false
                                if (SmsOperations.unblockNumber(this@SpamDetailActivity, sender)) {
                                    finish()
                                }
                            }) {
                                Text("Unblock")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUnblockConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun readSmsMessages(sender: String): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val projection = arrayOf(
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(sender),
            "${Telephony.Sms.DATE} ASC"
        )

        val smsList = mutableListOf<SmsMessage>()
        cursor?.use {
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
            val indexType = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val body = it.getString(indexBody).orEmpty()
                val timestamp = it.getLong(indexDate)
                val type = it.getInt(indexType)
                smsList.add(
                    SmsMessage(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        isSentByUser = isUserMessage(type)
                    )
                )
            }
        }
        return smsList
    }
}

@Composable
fun SpamDetailScreen(messages: List<SmsMessage>, modifier: Modifier = Modifier) {
    val messageUiModels = remember(messages) { buildSpamMessageUiModels(messages) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messageUiModels,
            key = { "${it.message.timestamp}-${it.message.isSentByUser}" }
        ) { uiModel ->
            SpamMessageBubble(
                message = uiModel.message,
                showTimestamp = uiModel.showTimestamp
            )
        }
    }
}

@Composable
fun SpamMessageBubble(message: SmsMessage, showTimestamp: Boolean) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val alignment = if (message.isSentByUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isSentByUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isSentByUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = if (message.isSentByUser) 2.dp else 0.dp
        ) {
            Text(
                text = message.body,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (showTimestamp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = if (message.isSentByUser) {
                    Arrangement.End
                } else {
                    Arrangement.Start
                }
            ) {
                Text(
                    text = timeFormatter.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private data class SpamMessageUiModel(
    val message: SmsMessage,
    val showTimestamp: Boolean
)

private fun buildSpamMessageUiModels(messages: List<SmsMessage>): List<SpamMessageUiModel> {
    if (messages.isEmpty()) return emptyList()

    val sorted = messages.sortedBy { it.timestamp }
    return sorted.mapIndexed { index, message ->
        val currentMinute = minuteBucket(message.timestamp)
        val nextMinute = sorted.getOrNull(index + 1)?.timestamp?.let(::minuteBucket)
        val showTimestamp = nextMinute == null || nextMinute != currentMinute
        SpamMessageUiModel(message = message, showTimestamp = showTimestamp)
    }
}

private fun minuteBucket(timestamp: Long): Long = timestamp / 60_000L
