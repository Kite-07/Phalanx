package com.kite.phalanx

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsDetailActivity : ComponentActivity() {

    companion object {
        init {
            Log.e("SmsDetailActivity", "========== COMPANION OBJECT STATIC INIT ==========")
            android.util.Log.wtf("SmsDetailActivity", "CLASS LOADED")
        }
    }

    init {
        Log.e("SmsDetailActivity", "========== INSTANCE INIT BLOCK ==========")
        android.util.Log.wtf("SmsDetailActivity", "INSTANCE CREATED")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("SmsDetailActivity", "========== onCreate START ==========")
        android.util.Log.wtf("SmsDetailActivity", "onCreate called")
        super.onCreate(savedInstanceState)
        Log.e("SmsDetailActivity", "onCreate after super")
        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"

        Log.d("SmsDetailActivity", "onCreate called - sender: $sender")
        Log.d("SmsDetailActivity", "Intent action: ${intent.action}, data: ${intent.data}")

        // Mark messages as read when opening conversation
        SmsOperations.markAsRead(this, sender)
        // Cancel notification for this sender
        NotificationHelper.cancelNotification(this, sender)

        setContent {
            Log.d("SmsDetailActivity", "setContent called")
            PhalanxTheme {
                var messages by remember { mutableStateOf(readSmsMessages(sender)) }
                var refreshTrigger by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()

                // Refresh messages when trigger changes
                LaunchedEffect(refreshTrigger) {
                    if (refreshTrigger > 0) {
                        messages = readSmsMessages(sender)
                    }
                }

                // Observe SMS database changes
                DisposableEffect(Unit) {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            scope.launch {
                                android.util.Log.d("SmsDetailActivity", "SMS database changed, refreshing")
                                messages = readSmsMessages(sender)
                            }
                        }
                    }

                    contentResolver.registerContentObserver(
                        Telephony.Sms.CONTENT_URI,
                        true,
                        observer
                    )

                    onDispose {
                        contentResolver.unregisterContentObserver(observer)
                    }
                }

                // Load saved draft
                val savedDraft by remember {
                    try {
                        DraftsManager.getDraft(this@SmsDetailActivity, sender)
                    } catch (e: Exception) {
                        kotlinx.coroutines.flow.flowOf("")
                    }
                }.collectAsState(initial = "")

                var messageText by remember { mutableStateOf("") }
                var showMenu by remember { mutableStateOf(false) }
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                // Message selection state
                var selectedMessages by remember { mutableStateOf<Set<Long>>(emptySet()) }
                val isSelectionMode = selectedMessages.isNotEmpty()
                var showDeleteSelectedConfirmDialog by remember { mutableStateOf(false) }

                // Block state
                var isBlocked by remember { mutableStateOf(SmsOperations.isNumberBlocked(this@SmsDetailActivity, sender)) }
                var showBlockConfirmDialog by remember { mutableStateOf(false) }

                // Restore draft when screen opens
                LaunchedEffect(savedDraft) {
                    if (savedDraft.isNotBlank() && messageText.isBlank()) {
                        messageText = savedDraft
                    }
                }

                // Load contact info for the sender asynchronously
                val contactName by produceState<String?>(initialValue = null, sender) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            lookupContactName(sender)
                        } catch (e: Exception) {
                            Log.e("SmsDetailActivity", "Error loading contact name", e)
                            null
                        }
                    }
                }
                val displayName = contactName ?: sender

                val contactPhoto by produceState<ImageBitmap?>(initialValue = null, sender) {
                    value = try {
                        loadContactPhoto(sender)
                    } catch (e: Exception) {
                        Log.e("SmsDetailActivity", "Error loading contact photo", e)
                        null
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isSelectionMode) {
                                    Text("${selectedMessages.size} selected")
                                } else {
                                    ContactTitle(
                                        displayName = displayName,
                                        contactPhoto = contactPhoto
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (isSelectionMode) {
                                        selectedMessages = emptySet()
                                    } else {
                                        finish()
                                    }
                                }) {
                                    Icon(
                                        if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = if (isSelectionMode) "Clear selection" else "Back"
                                    )
                                }
                            },
                            actions = {
                                if (isSelectionMode) {
                                    IconButton(onClick = { showDeleteSelectedConfirmDialog = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                                    }
                                } else {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        // Only show these menu items if there are messages in the conversation
                                        if (messages.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Mark as unread") },
                                                onClick = {
                                                    showMenu = false
                                                    SmsOperations.markAsUnread(this@SmsDetailActivity, sender)
                                                    finish()
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.MailOutline, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (isBlocked) "Unblock number" else "Block number") },
                                                onClick = {
                                                    showMenu = false
                                                    if (isBlocked) {
                                                        if (SmsOperations.unblockNumber(this@SmsDetailActivity, sender)) {
                                                            isBlocked = false
                                                        }
                                                    } else {
                                                        showBlockConfirmDialog = true
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Warning, contentDescription = null)
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
                                }
                            }
                        )
                    },
                    bottomBar = {
                        MessageComposer(
                            message = messageText,
                            onMessageChange = { newText ->
                                messageText = newText
                                // Auto-save draft
                                scope.launch {
                                    try {
                                        DraftsManager.saveDraft(this@SmsDetailActivity, sender, newText)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SmsDetailActivity", "Error saving draft", e)
                                    }
                                }
                            },
                            onSendClick = {
                                android.util.Log.d("SmsDetailActivity", "Send button clicked, messageText: '${messageText}'")
                                if (messageText.isNotBlank()) {
                                    val messageToSend = messageText
                                    android.util.Log.d("SmsDetailActivity", "About to call SmsHelper.sendSms - recipient: $sender, message: $messageToSend")
                                    try {
                                        SmsHelper.sendSms(
                                            context = this@SmsDetailActivity,
                                            recipient = sender,
                                            message = messageToSend
                                        )
                                        android.util.Log.d("SmsDetailActivity", "SmsHelper.sendSms returned")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SmsDetailActivity", "Exception calling SmsHelper.sendSms", e)
                                    }
                                    messageText = ""
                                    // Delete draft after sending
                                    scope.launch {
                                        try {
                                            DraftsManager.deleteDraft(this@SmsDetailActivity, sender)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SmsDetailActivity", "Error deleting draft", e)
                                        }
                                    }
                                    // Refresh messages after a short delay to allow database write
                                    scope.launch {
                                        kotlinx.coroutines.delay(500) // Wait for DB write
                                        refreshTrigger++
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    SmsDetailScreen(
                        messages = messages,
                        selectedMessages = selectedMessages,
                        onMessageLongClick = { timestamp ->
                            selectedMessages = if (selectedMessages.contains(timestamp)) {
                                selectedMessages - timestamp
                            } else {
                                selectedMessages + timestamp
                            }
                        },
                        onMessageClick = { timestamp ->
                            if (isSelectionMode) {
                                selectedMessages = if (selectedMessages.contains(timestamp)) {
                                    selectedMessages - timestamp
                                } else {
                                    selectedMessages + timestamp
                                }
                            }
                        },
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
                                if (SmsOperations.deleteThread(this@SmsDetailActivity, sender)) {
                                    // Also delete the draft
                                    scope.launch {
                                        try {
                                            DraftsManager.deleteDraft(this@SmsDetailActivity, sender)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SmsDetailActivity", "Error deleting draft on thread delete", e)
                                        }
                                    }
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

                if (showDeleteSelectedConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteSelectedConfirmDialog = false },
                        title = { Text("Delete ${selectedMessages.size} message${if (selectedMessages.size > 1) "s" else ""}?") },
                        text = { Text("Are you sure you want to delete the selected message${if (selectedMessages.size > 1) "s" else ""}? This action cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteSelectedConfirmDialog = false
                                var deletedCount = 0
                                selectedMessages.forEach { timestamp ->
                                    if (SmsOperations.deleteMessage(this@SmsDetailActivity, sender, timestamp)) {
                                        deletedCount++
                                    }
                                }
                                if (deletedCount > 0) {
                                    selectedMessages = emptySet()
                                    refreshTrigger++
                                }
                            }) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteSelectedConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showBlockConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showBlockConfirmDialog = false },
                        title = { Text("Block this number?") },
                        text = { Text("$sender will be blocked from calling and messaging you. The conversation will be moved to spam.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showBlockConfirmDialog = false
                                if (SmsOperations.blockNumber(this@SmsDetailActivity, sender)) {
                                    isBlocked = true
                                    finish()
                                }
                            }) {
                                Text("Block")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBlockConfirmDialog = false }) {
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

    private fun hasContactPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun lookupContactPhotoUri(phoneNumber: String): Uri? {
        if (!hasContactPermission()) return null

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                } else {
                    null
                }
            }
        } catch (securityException: SecurityException) {
            null
        }
    }

    private fun lookupContactName(phoneNumber: String): String? {
        if (!hasContactPermission()) return null

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (securityException: SecurityException) {
            null
        }
    }

    private suspend fun loadContactPhoto(phoneNumber: String): ImageBitmap? {
        val photoUri = lookupContactPhotoUri(phoneNumber) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(photoUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun ContactTitle(displayName: String, contactPhoto: ImageBitmap?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Contact photo
        if (contactPhoto != null) {
            Image(
                bitmap = contactPhoto,
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Default avatar icon
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Default avatar",
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = displayName)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmsDetailScreen(
    messages: List<SmsMessage>,
    selectedMessages: Set<Long>,
    onMessageLongClick: (Long) -> Unit,
    onMessageClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val messageUiModels = remember(messages) { buildMessageUiModels(messages) }

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
            MessageBubble(
                message = uiModel.message,
                showTimestamp = uiModel.showTimestamp,
                isSelected = selectedMessages.contains(uiModel.message.timestamp),
                onLongClick = { onMessageLongClick(uiModel.message.timestamp) },
                onClick = { onMessageClick(uiModel.message.timestamp) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: SmsMessage,
    showTimestamp: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
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
            tonalElevation = if (message.isSentByUser) 2.dp else 0.dp,
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier
                    }
                )
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

private data class MessageUiModel(
    val message: SmsMessage,
    val showTimestamp: Boolean
)

private fun buildMessageUiModels(messages: List<SmsMessage>): List<MessageUiModel> {
    if (messages.isEmpty()) return emptyList()

    val sorted = messages.sortedBy { it.timestamp }
    return sorted.mapIndexed { index, message ->
        val currentMinute = minuteBucket(message.timestamp)
        val nextMinute = sorted.getOrNull(index + 1)?.timestamp?.let(::minuteBucket)
        val showTimestamp = nextMinute == null || nextMinute != currentMinute
        MessageUiModel(message = message, showTimestamp = showTimestamp)
    }
}

private fun minuteBucket(timestamp: Long): Long = timestamp / 60_000L

@Composable
fun MessageComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val charCount = message.length
    val smsCount = (charCount / 160) + 1

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = onMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message") },
                        maxLines = 4
                    )
                    if (message.isNotEmpty()) {
                        Text(
                            text = "$charCount chars • $smsCount SMS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        android.util.Log.d("MessageComposer", "IconButton clicked, message: '$message', isNotBlank: ${message.isNotBlank()}")
                        onSendClick()
                    },
                    enabled = message.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (message.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}
