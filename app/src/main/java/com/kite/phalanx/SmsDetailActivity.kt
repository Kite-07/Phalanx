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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
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

    // Flag to prevent ContentObserver from refreshing while we mark messages as read
    @Volatile
    private var isMarkingAsRead = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"

        setContent {
            PhalanxTheme {
                // Read messages FIRST to capture unread status before marking as read
                var messages by remember { mutableStateOf(readSmsMessages(sender)) }
                var refreshTrigger by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()

                // Mark as read/seen and cancel notification after initial load
                LaunchedEffect(Unit) {
                    isMarkingAsRead = true
                    SmsOperations.markAsRead(this@SmsDetailActivity, sender)
                    SmsOperations.markAsSeen(this@SmsDetailActivity, sender)
                    NotificationHelper.cancelNotification(this@SmsDetailActivity, sender)
                    // Small delay to ensure ContentObserver processes all changes
                    kotlinx.coroutines.delay(200)
                    isMarkingAsRead = false
                }

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
                            // Skip refresh if we're currently marking messages as read
                            if (!isMarkingAsRead) {
                                scope.launch {
                                    messages = readSmsMessages(sender)
                                }
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
                            null
                        }
                    }
                }
                val displayName = contactName ?: sender

                val contactPhoto by produceState<ImageBitmap?>(initialValue = null, sender) {
                    value = try {
                        loadContactPhoto(sender)
                    } catch (e: Exception) {
                        null
                    }
                }

                // List state for scroll-to-bottom functionality
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                // Show scroll-to-bottom button when user has scrolled up significantly
                val showScrollToBottom by remember {
                    derivedStateOf {
                        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        // Show button if user is not near the bottom (more than 5 messages away)
                        totalItems > 0 && lastVisibleIndex < totalItems - 5
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
                                        contactPhoto = contactPhoto,
                                        sender = sender
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
                        var showSimSelector by remember { mutableStateOf(false) }
                        val activeSims by remember { mutableStateOf(SimHelper.getActiveSims(this@SmsDetailActivity)) }
                        val hasMultipleSims = activeSims.size > 1
                        var currentSimId by remember { mutableStateOf(-1) }

                        // Load current SIM for conversation when dialog is shown
                        LaunchedEffect(showSimSelector) {
                            if (showSimSelector) {
                                currentSimId = SimPreferences.getSimForConversation(this@SmsDetailActivity, sender)
                            }
                        }

                        MessageComposer(
                            message = messageText,
                            onMessageChange = { newText ->
                                messageText = newText
                                // Auto-save draft
                                scope.launch {
                                    try {
                                        DraftsManager.saveDraft(this@SmsDetailActivity, sender, newText)
                                    } catch (e: Exception) {
                                        // Silently handle error
                                    }
                                }
                            },
                            onSendClick = {
                                if (messageText.isNotBlank()) {
                                    scope.launch {
                                        val messageToSend = messageText
                                        // Get SIM to use for this conversation
                                        val subId = SimPreferences.getSimForConversation(this@SmsDetailActivity, sender)
                                        try {
                                            SmsHelper.sendSms(
                                                context = this@SmsDetailActivity,
                                                recipient = sender,
                                                message = messageToSend,
                                                subscriptionId = subId
                                            )
                                        } catch (e: Exception) {
                                            // Silently handle error
                                        }
                                        messageText = ""
                                        // Delete draft after sending
                                        try {
                                            DraftsManager.deleteDraft(this@SmsDetailActivity, sender)
                                        } catch (e: Exception) {
                                            // Silently handle error
                                        }
                                        // Refresh messages after a short delay to allow database write
                                        kotlinx.coroutines.delay(500) // Wait for DB write
                                        refreshTrigger++
                                    }
                                }
                            },
                            onSendLongClick = {
                                // Show SIM selector if there are active SIMs
                                if (activeSims.isNotEmpty() && messageText.isNotBlank()) {
                                    showSimSelector = true
                                }
                            },
                            hasMultipleSims = hasMultipleSims
                        )

                        if (showSimSelector) {
                            SimSelectorDialog(
                                sims = activeSims,
                                selectedSubscriptionId = currentSimId,
                                onSimSelected = { selectedSubId, setAsDefault ->
                                    showSimSelector = false
                                    if (messageText.isNotBlank()) {
                                        scope.launch {
                                            val messageToSend = messageText
                                            // Save as default for this conversation if checkbox was checked
                                            if (setAsDefault) {
                                                SimPreferences.setConversationSim(this@SmsDetailActivity, sender, selectedSubId)
                                            }
                                            try {
                                                SmsHelper.sendSms(
                                                    context = this@SmsDetailActivity,
                                                    recipient = sender,
                                                    message = messageToSend,
                                                    subscriptionId = selectedSubId
                                                )
                                            } catch (e: Exception) {
                                                // Silently handle error
                                            }
                                            messageText = ""
                                            // Delete draft after sending
                                            try {
                                                DraftsManager.deleteDraft(this@SmsDetailActivity, sender)
                                            } catch (e: Exception) {
                                                // Silently handle error
                                            }
                                            // Refresh messages
                                            kotlinx.coroutines.delay(500)
                                            refreshTrigger++
                                        }
                                    }
                                },
                                onDismiss = { showSimSelector = false }
                            )
                        }
                    }
                ) { innerPadding ->
                    val messageUiModels = remember(messages) { buildMessageUiModels(messages) }

                    // Find the index where the "New messages" divider is shown
                    val scrollToIndex = remember(messageUiModels) {
                        val dividerIndex = messageUiModels.indexOfFirst { it.showNewMessagesDivider }
                        if (dividerIndex >= 0) {
                            // Scroll to the divider (which is at the first unread message)
                            dividerIndex
                        } else {
                            // No unread messages, scroll to the last message
                            (messageUiModels.size - 1).coerceAtLeast(0)
                        }
                    }

                    Box(modifier = Modifier.padding(innerPadding)) {
                        SmsDetailScreen(
                            messageUiModels = messageUiModels,
                            selectedMessages = selectedMessages,
                            scrollToIndex = scrollToIndex,
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
                            listState = listState,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Scroll to bottom FAB
                        AnimatedVisibility(
                            visible = showScrollToBottom,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(messageUiModels.size - 1)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom"
                                )
                            }
                        }
                    }
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
                                            // Silently handle error
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
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.SEEN,
            Telephony.Sms.READ
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
            val indexSubId = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            val indexSeen = it.getColumnIndex(Telephony.Sms.SEEN)
            val indexRead = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val body = it.getString(indexBody).orEmpty()
                val timestamp = it.getLong(indexDate)
                val type = it.getInt(indexType)
                val subId = if (indexSubId >= 0) it.getInt(indexSubId) else -1
                val isSeen = if (indexSeen >= 0) it.getInt(indexSeen) == 1 else true
                val isRead = if (indexRead >= 0) it.getInt(indexRead) == 1 else true
                smsList.add(
                    SmsMessage(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        isSentByUser = isUserMessage(type),
                        subscriptionId = subId,
                        isSeen = isSeen,
                        isRead = isRead
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
fun ContactTitle(displayName: String, contactPhoto: ImageBitmap?, sender: String) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Check if conversation is muted
    val isMuted by ConversationMutePreferences.isConversationMutedFlow(context, sender)
        .collectAsState(initial = false)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Open contact detail activity
                val intent = android.content.Intent(context, ContactDetailActivity::class.java).apply {
                    putExtra("phone_number", sender)
                    putExtra("contact_name", displayName)
                }
                context.startActivity(intent)
            }
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
        if (isMuted) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "(Muted)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SmsDetailScreen(
    messageUiModels: List<MessageUiModel>,
    selectedMessages: Set<Long>,
    scrollToIndex: Int,
    onMessageLongClick: (Long) -> Unit,
    onMessageClick: (Long) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    // Scroll to the target index when messages load
    LaunchedEffect(scrollToIndex, messageUiModels.size) {
        if (messageUiModels.isNotEmpty() && scrollToIndex >= 0 && scrollToIndex < messageUiModels.size) {
            listState.scrollToItem(scrollToIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messageUiModels,
            key = { "${it.message.timestamp}-${it.message.isSentByUser}" }
        ) { uiModel ->
            Column {
                if (uiModel.showNewMessagesDivider) {
                    NewMessagesDivider()
                }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val alignment = if (message.isSentByUser) Alignment.End else Alignment.Start

    // Get bubble color based on SIM for user's messages, or default for received messages
    val bubbleColor = if (message.isSentByUser && message.subscriptionId != -1) {
        // For user's messages, use the custom SIM color from preferences
        val simBubbleColor by SimPreferences.getBubbleColorForSimFlow(context, message.subscriptionId)
            .collectAsState(initial = MaterialTheme.colorScheme.primary)
        simBubbleColor
    } else if (message.isSentByUser) {
        // User message but no SIM info, use primary color
        MaterialTheme.colorScheme.primary
    } else {
        // Received message, use grey
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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

@Composable
fun NewMessagesDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF4CAF50) // Green color
        )
        Text(
            text = "Unread",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF4CAF50)
        )
    }
}

internal data class MessageUiModel(
    val message: SmsMessage,
    val showTimestamp: Boolean,
    val showNewMessagesDivider: Boolean = false
)

internal fun buildMessageUiModels(messages: List<SmsMessage>): List<MessageUiModel> {
    if (messages.isEmpty()) return emptyList()

    val sorted = messages.sortedBy { it.timestamp }

    // Find the first unread received message
    val firstUnreadIndex = sorted.indexOfFirst { !it.isRead && !it.isSentByUser }

    return sorted.mapIndexed { index, message ->
        val currentMinute = minuteBucket(message.timestamp)
        val nextMinute = sorted.getOrNull(index + 1)?.timestamp?.let(::minuteBucket)
        val showTimestamp = nextMinute == null || nextMinute != currentMinute

        // Show "New messages" divider at the first unread message
        val showNewMessagesDivider = firstUnreadIndex >= 0 && index == firstUnreadIndex

        MessageUiModel(
            message = message,
            showTimestamp = showTimestamp,
            showNewMessagesDivider = showNewMessagesDivider
        )
    }
}

private fun minuteBucket(timestamp: Long): Long = timestamp / 60_000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendLongClick: () -> Unit = {},
    hasMultipleSims: Boolean = false,
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (message.isNotBlank()) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        onSendClick()
                                    },
                                    onLongClick = {
                                        onSendLongClick()
                                    }
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (message.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
