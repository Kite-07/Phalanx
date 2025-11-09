package com.kite.phalanx

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import com.kite.phalanx.ui.SmsDetailViewModel
import com.kite.phalanx.ui.SecurityChip
import com.kite.phalanx.ui.SecurityExplanationSheet
import com.kite.phalanx.domain.model.VerdictLevel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.kite.phalanx.domain.usecase.MoveToTrashUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SmsDetailActivity : ComponentActivity() {

    @Inject
    lateinit var moveToTrashUseCase: MoveToTrashUseCase

    @Inject
    lateinit var allowBlockListRepository: com.kite.phalanx.domain.repository.AllowBlockListRepository

    private val viewModel: SmsDetailViewModel by viewModels()

    // Flag to prevent ContentObserver from refreshing while we mark messages as read
    @Volatile
    private var isMarkingAsRead = false

    // BroadcastReceiver for quota exceeded events
    private val quotaExceededReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == com.kite.phalanx.data.repository.SafeBrowsingRepository.ACTION_QUOTA_EXCEEDED) {
                showQuotaExceededDialog()
            }
        }
    }

    private var quotaExceededDialogShown = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"
        val prefillMessage = intent.getStringExtra("prefill_message")

        setContent {
            PhalanxTheme {
                // Load text size scale from preferences
                val textSizeScale by AppPreferences.getTextSizeScaleFlow(this@SmsDetailActivity)
                    .collectAsState(initial = 1.0f)

                // Read messages FIRST to capture unread status before marking as read
                var messages by remember { mutableStateOf(readSmsMessages(sender)) }
                var refreshTrigger by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()

                // Collect verdict cache from ViewModel
                val verdictCache by viewModel.verdictCache.collectAsState()

                // Build registered domain cache for displaying in SecurityChip
                val registeredDomainCache = remember(verdictCache) {
                    verdictCache.keys.associateWith { messageId ->
                        viewModel.getRegisteredDomain(messageId) ?: ""
                    }
                }

                // State for security explanation sheet
                var showSecuritySheet by remember { mutableStateOf(false) }
                var selectedMessageForSecurity by remember { mutableStateOf<SmsMessage?>(null) }

                // Analyze messages for security threats (only received messages)
                LaunchedEffect(messages) {
                    messages.filter { !it.isSentByUser && it.body.isNotBlank() }.forEach { message ->
                        viewModel.analyzeMessage(message.timestamp, message.body, sender)
                    }
                }

                // Mark as read/seen and cancel notification after initial load
                LaunchedEffect(Unit) {
                    isMarkingAsRead = true
                    SmsOperations.markAsRead(this@SmsDetailActivity, sender)
                    SmsOperations.markAsSeen(this@SmsDetailActivity, sender)

                    // Cancel normal message notification
                    NotificationHelper.cancelNotification(this@SmsDetailActivity, sender)

                    // Cancel all security threat notifications for messages from this sender
                    val notificationManager = androidx.core.app.NotificationManagerCompat.from(this@SmsDetailActivity)
                    messages.forEach { message ->
                        // Cancel security threat notification (if any) for each message
                        notificationManager.cancel((sender + message.timestamp).hashCode())
                    }

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

                // Observe SMS and MMS database changes
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

                    // Watch both SMS and MMS content URIs
                    contentResolver.registerContentObserver(
                        Telephony.Sms.CONTENT_URI,
                        true,
                        observer
                    )
                    contentResolver.registerContentObserver(
                        Telephony.Mms.CONTENT_URI,
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

                var messageText by remember { mutableStateOf(prefillMessage ?: "") }

                // Set draft as message text if no prefill and draft exists
                LaunchedEffect(savedDraft) {
                    if (prefillMessage == null && savedDraft.isNotBlank() && messageText.isBlank()) {
                        messageText = savedDraft
                    }
                }
                var showMenu by remember { mutableStateOf(false) }
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                var attachments by remember { mutableStateOf<List<SelectedAttachment>>(emptyList()) }

                // Message selection state
                var selectedMessages by remember { mutableStateOf<Set<Long>>(emptySet()) }
                val isSelectionMode = selectedMessages.isNotEmpty()
                var showDeleteSelectedConfirmDialog by remember { mutableStateOf(false) }
                var showMessageActionsMenu by remember { mutableStateOf(false) }

                // Reply state
                var replyingToMessage by remember { mutableStateOf<SmsMessage?>(null) }

                // Pin message state
                var showPinDurationDialog by remember { mutableStateOf(false) }
                var messageToPinTimestamp by remember { mutableStateOf<Long?>(null) }
                var showUnpinConfirmDialog by remember { mutableStateOf(false) }
                var messageToUnpinTimestamp by remember { mutableStateOf<Long?>(null) }

                // Load pinned messages for this conversation
                val pinnedMessages by PinnedMessagesPreferences.getPinnedMessagesForSender(this@SmsDetailActivity, sender)
                    .collectAsState(initial = emptyList())

                // Block state
                var isBlocked by remember { mutableStateOf(SmsOperations.isNumberBlocked(this@SmsDetailActivity, sender)) }
                var showBlockConfirmDialog by remember { mutableStateOf(false) }

                // Archive/Pin state
                val isArchived by ArchivedThreadsPreferences.isThreadArchivedFlow(this@SmsDetailActivity, sender)
                    .collectAsState(initial = false)
                val isPinned by PinnedThreadsPreferences.isThreadPinnedFlow(this@SmsDetailActivity, sender)
                    .collectAsState(initial = false)

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
                                        sender = sender,
                                        textSizeScale = textSizeScale
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
                                    // Get selected message(s) for actions
                                    val selectedMessagesList = messages.filter { selectedMessages.contains(it.timestamp) }
                                    val singleMessageSelected = selectedMessagesList.size == 1
                                    val firstSelectedMessage = selectedMessagesList.firstOrNull()

                                    // Reply button (only for single message)
                                    if (singleMessageSelected && firstSelectedMessage != null) {
                                        IconButton(onClick = {
                                            // Set the message being replied to
                                            replyingToMessage = firstSelectedMessage
                                            selectedMessages = emptySet()
                                            // TODO: Scroll to bottom/composer and focus
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                                        }
                                    }

                                    // Copy button (only if all selected have text)
                                    if (selectedMessagesList.all { it.body.isNotBlank() }) {
                                        IconButton(onClick = {
                                            val textToCopy = if (selectedMessagesList.size == 1) {
                                                selectedMessagesList.first().body
                                            } else {
                                                selectedMessagesList.joinToString("\n\n") { it.body }
                                            }
                                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Messages", textToCopy)
                                            clipboard.setPrimaryClip(clip)
                                            selectedMessages = emptySet()
                                            android.widget.Toast.makeText(this@SmsDetailActivity, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                        }
                                    }

                                    // Forward button (only for single message with text)
                                    if (singleMessageSelected && firstSelectedMessage?.body?.isNotBlank() == true) {
                                        IconButton(onClick = {
                                            val intent = Intent(this@SmsDetailActivity, ContactPickerActivity::class.java)
                                            intent.putExtra("forward_message", firstSelectedMessage.body)
                                            startActivity(intent)
                                            selectedMessages = emptySet()
                                        }) {
                                            Icon(Icons.Default.Share, contentDescription = "Forward")
                                        }
                                    }

                                    // Overflow menu for more actions
                                    IconButton(onClick = { showMessageActionsMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showMessageActionsMenu,
                                        onDismissRequest = { showMessageActionsMenu = false }
                                    ) {
                                        // Pin message (only for single message)
                                        if (singleMessageSelected && firstSelectedMessage != null) {
                                            DropdownMenuItem(
                                                text = { Text("Pin message") },
                                                onClick = {
                                                    showMessageActionsMenu = false
                                                    messageToPinTimestamp = firstSelectedMessage.timestamp
                                                    showPinDurationDialog = true
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PushPin, contentDescription = null)
                                                }
                                            )
                                        }

                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                showMessageActionsMenu = false
                                                showDeleteSelectedConfirmDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                            }
                                        )
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
                                                text = { Text(if (isArchived) "Unarchive" else "Archive") },
                                                onClick = {
                                                    showMenu = false
                                                    lifecycleScope.launch {
                                                        if (isArchived) {
                                                            ArchivedThreadsPreferences.unarchiveThread(this@SmsDetailActivity, sender)
                                                        } else {
                                                            ArchivedThreadsPreferences.archiveThread(this@SmsDetailActivity, sender)
                                                            finish() // Close conversation after archiving
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Archive, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (isPinned) "Unpin" else "Pin") },
                                                onClick = {
                                                    showMenu = false
                                                    lifecycleScope.launch {
                                                        if (isPinned) {
                                                            PinnedThreadsPreferences.unpinThread(this@SmsDetailActivity, sender)
                                                        } else {
                                                            PinnedThreadsPreferences.pinThread(this@SmsDetailActivity, sender)
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PushPin, contentDescription = null)
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
                        val canReply = remember(sender) { canSenderReceiveReplies(sender) }

                        if (!canReply) {
                            // Show non-reply message for senders that can't receive replies
                            NonReplyBottomBar(textSizeScale = textSizeScale)
                        } else {
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

                            Column {
                                // Reply preview above composer
                                replyingToMessage?.let { replyMsg ->
                                    ReplyPreview(
                                        message = replyMsg,
                                        textSizeScale = textSizeScale,
                                        onDismiss = { replyingToMessage = null }
                                    )
                                }

                                MessageComposer(
                            message = messageText,
                            textSizeScale = textSizeScale,
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
                                val hasContent = messageText.isNotBlank() || attachments.isNotEmpty()
                                if (hasContent) {
                                    scope.launch {
                                        val messageToSend = messageText
                                        val attachmentsToSend = attachments.toList()
                                        // Get SIM to use for this conversation
                                        val subId = SimPreferences.getSimForConversation(this@SmsDetailActivity, sender)

                                        try {
                                            if (attachmentsToSend.isNotEmpty()) {
                                                // Send as MMS if there are attachments
                                                MmsSender.sendMms(
                                                    context = this@SmsDetailActivity,
                                                    recipient = sender,
                                                    text = if (messageToSend.isBlank()) null else messageToSend,
                                                    attachments = attachmentsToSend,
                                                    subscriptionId = subId
                                                )
                                            } else {
                                                // Send as SMS if no attachments
                                                SmsHelper.sendSms(
                                                    context = this@SmsDetailActivity,
                                                    recipient = sender,
                                                    message = messageToSend,
                                                    subscriptionId = subId
                                                )
                                            }
                                        } catch (e: Exception) {
                                            // Silently handle error
                                        }

                                        messageText = ""
                                        attachments = emptyList()

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
                                val hasContent = messageText.isNotBlank() || attachments.isNotEmpty()
                                if (activeSims.isNotEmpty() && hasContent) {
                                    showSimSelector = true
                                }
                            },
                            hasMultipleSims = hasMultipleSims,
                            attachments = attachments,
                            onAttachmentSelected = { attachment ->
                                attachments = attachments + attachment
                            },
                            onAttachmentRemoved = { attachment ->
                                attachments = attachments.filter { it != attachment }
                            }
                        )

                        if (showSimSelector) {
                            SimSelectorDialog(
                                sims = activeSims,
                                selectedSubscriptionId = currentSimId,
                                onSimSelected = { selectedSubId, setAsDefault ->
                                    showSimSelector = false
                                    val hasContent = messageText.isNotBlank() || attachments.isNotEmpty()
                                    if (hasContent) {
                                        scope.launch {
                                            val messageToSend = messageText
                                            val attachmentsToSend = attachments.toList()

                                            // Save as default for this conversation if checkbox was checked
                                            if (setAsDefault) {
                                                SimPreferences.setConversationSim(this@SmsDetailActivity, sender, selectedSubId)
                                            }

                                            try {
                                                if (attachmentsToSend.isNotEmpty()) {
                                                    // Send as MMS if there are attachments
                                                    MmsSender.sendMms(
                                                        context = this@SmsDetailActivity,
                                                        recipient = sender,
                                                        text = if (messageToSend.isBlank()) null else messageToSend,
                                                        attachments = attachmentsToSend,
                                                        subscriptionId = selectedSubId
                                                    )
                                                } else {
                                                    // Send as SMS if no attachments
                                                    SmsHelper.sendSms(
                                                        context = this@SmsDetailActivity,
                                                        recipient = sender,
                                                        message = messageToSend,
                                                        subscriptionId = selectedSubId
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                // Silently handle error
                                            }

                                            messageText = ""
                                            attachments = emptyList()

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
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Pinned messages block (persistent, doesn't scroll)
                            if (pinnedMessages.isNotEmpty()) {
                                PinnedMessagesBlock(
                                    pinnedMessages = pinnedMessages,
                                    textSizeScale = textSizeScale,
                                    onPinnedMessageClick = { pinnedMessage ->
                                        // Scroll to the message in the conversation
                                        val messageIndex = messageUiModels.indexOfFirst {
                                            it.message.timestamp == pinnedMessage.messageTimestamp
                                        }
                                        if (messageIndex >= 0) {
                                            scope.launch {
                                                listState.animateScrollToItem(messageIndex)
                                            }
                                        }
                                    },
                                    onPinnedMessageLongClick = { pinnedMessage ->
                                        messageToUnpinTimestamp = pinnedMessage.messageTimestamp
                                        showUnpinConfirmDialog = true
                                    }
                                )
                            }

                            // Message list (scrollable)
                            Box(modifier = Modifier.weight(1f)) {
                            SmsDetailScreen(
                                messageUiModels = messageUiModels,
                                selectedMessages = selectedMessages,
                                scrollToIndex = scrollToIndex,
                                textSizeScale = textSizeScale,
                                verdictCache = verdictCache,
                                registeredDomainCache = registeredDomainCache,
                                onSecurityChipClick = { message ->
                                    selectedMessageForSecurity = message
                                    showSecuritySheet = true
                                },
                                onMessageLongClick = { message ->
                                    // Long-press always toggles selection (enters or modifies selection mode)
                                    selectedMessages = if (selectedMessages.contains(message.timestamp)) {
                                        selectedMessages - message.timestamp
                                    } else {
                                        selectedMessages + message.timestamp
                                    }
                                },
                                onMessageClick = { message ->
                                    if (isSelectionMode) {
                                        // In selection mode, click toggles selection
                                        selectedMessages = if (selectedMessages.contains(message.timestamp)) {
                                            selectedMessages - message.timestamp
                                        } else {
                                            selectedMessages + message.timestamp
                                        }
                                    }
                                },
                                listState = listState,
                                modifier = Modifier.fillMaxSize(),
                                onRetry = { failedMessage ->
                                    scope.launch {
                                        // Delete the failed message
                                        SmsOperations.deleteMessage(
                                            context = this@SmsDetailActivity,
                                            sender = failedMessage.sender,
                                            timestamp = failedMessage.timestamp,
                                            moveToTrashUseCase = moveToTrashUseCase
                                        )

                                        // Resend the message
                                        if (failedMessage.isMms && failedMessage.attachments.isNotEmpty()) {
                                            // Retry as MMS
                                            MmsSender.sendMms(
                                                context = this@SmsDetailActivity,
                                                recipient = sender,
                                                text = if (failedMessage.body.isBlank()) null else failedMessage.body,
                                                attachments = failedMessage.attachments.map { attachment ->
                                                    SelectedAttachment(
                                                        uri = attachment.uri,
                                                        mimeType = attachment.contentType,
                                                        fileName = attachment.fileName
                                                    )
                                                },
                                                subscriptionId = failedMessage.subscriptionId
                                            )
                                        } else {
                                            // Retry as SMS
                                            SmsHelper.sendSms(
                                                context = this@SmsDetailActivity,
                                                recipient = sender,
                                                message = failedMessage.body,
                                                subscriptionId = failedMessage.subscriptionId
                                            )
                                        }

                                        // Refresh messages after a short delay
                                        kotlinx.coroutines.delay(500)
                                        refreshTrigger++
                                    }
                                }
                            )

                            // Scroll to bottom FAB
                            androidx.compose.animation.AnimatedVisibility(
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
                }

                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete Conversation?") },
                        text = { Text("Delete this conversation? It will be moved to Trash Vault for 30 days.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirmDialog = false
                                scope.launch {
                                    if (SmsOperations.deleteThread(this@SmsDetailActivity, sender, moveToTrashUseCase)) {
                                        // Also delete the draft
                                        try {
                                            DraftsManager.deleteDraft(this@SmsDetailActivity, sender)
                                        } catch (e: Exception) {
                                            // Silently handle error
                                        }
                                        finish()
                                    }
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
                                scope.launch {
                                    var deletedCount = 0
                                    selectedMessages.forEach { timestamp ->
                                        if (SmsOperations.deleteMessage(
                                            context = this@SmsDetailActivity,
                                            sender = sender,
                                            timestamp = timestamp,
                                            moveToTrashUseCase = moveToTrashUseCase
                                        )) {
                                            deletedCount++
                                        }
                                    }
                                    if (deletedCount > 0) {
                                        selectedMessages = emptySet()
                                        refreshTrigger++
                                    }
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

                // Pin duration selection dialog
                if (showPinDurationDialog && messageToPinTimestamp != null) {
                    val messageToPin = messages.find { it.timestamp == messageToPinTimestamp }
                    if (messageToPin != null) {
                        AlertDialog(
                            onDismissRequest = { showPinDurationDialog = false },
                            title = { Text("Pin message") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("How long should this message remain pinned?")
                                    Spacer(modifier = Modifier.height(8.dp))

                                    PinnedMessagesPreferences.PinDuration.values().forEach { duration ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showPinDurationDialog = false
                                                    lifecycleScope.launch {
                                                        val snippet = if (messageToPin.body.isNotBlank()) {
                                                            if (messageToPin.body.length > 100) {
                                                                messageToPin.body.take(100) + "..."
                                                            } else {
                                                                messageToPin.body
                                                            }
                                                        } else if (messageToPin.attachments.isNotEmpty()) {
                                                            "[Attachment]"
                                                        } else {
                                                            "[Message]"
                                                        }

                                                        PinnedMessagesPreferences.pinMessage(
                                                            context = this@SmsDetailActivity,
                                                            sender = sender,
                                                            messageTimestamp = messageToPin.timestamp,
                                                            snippet = snippet,
                                                            duration = duration,
                                                            hasAttachments = messageToPin.attachments.isNotEmpty()
                                                        )
                                                        selectedMessages = emptySet()
                                                        android.widget.Toast.makeText(
                                                            this@SmsDetailActivity,
                                                            "Message pinned",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.RadioButton(
                                                selected = false,
                                                onClick = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(duration.label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showPinDurationDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                // Unpin confirmation dialog
                if (showUnpinConfirmDialog && messageToUnpinTimestamp != null) {
                    AlertDialog(
                        onDismissRequest = { showUnpinConfirmDialog = false },
                        title = { Text("Unpin message?") },
                        text = { Text("This message will be removed from the pinned messages block.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showUnpinConfirmDialog = false
                                lifecycleScope.launch {
                                    PinnedMessagesPreferences.unpinMessage(
                                        context = this@SmsDetailActivity,
                                        sender = sender,
                                        messageTimestamp = messageToUnpinTimestamp!!
                                    )
                                    android.widget.Toast.makeText(
                                        this@SmsDetailActivity,
                                        "Message unpinned",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }) {
                                Text("Unpin")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUnpinConfirmDialog = false }) {
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

                // Security explanation sheet
                if (showSecuritySheet && selectedMessageForSecurity != null) {
                    val verdict = verdictCache[selectedMessageForSecurity!!.timestamp]
                    if (verdict != null) {
                        // Extract registered domain and final URL from caches
                        val registeredDomain = viewModel.getRegisteredDomain(selectedMessageForSecurity!!.timestamp) ?: ""
                        val finalUrl = viewModel.getFinalUrl(selectedMessageForSecurity!!.timestamp)

                        SecurityExplanationSheet(
                            verdict = verdict,
                            registeredDomain = registeredDomain,
                            finalUrl = finalUrl,
                            senderInfo = sender,
                            onDismiss = {
                                showSecuritySheet = false
                                selectedMessageForSecurity = null
                            },
                            onOpenSafely = null, // Not in scope for this app
                            onCopyUrl = if (finalUrl != null) {
                                {
                                    // Copy final URL to clipboard
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("URL", finalUrl)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(
                                        this@SmsDetailActivity,
                                        "URL copied to clipboard",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else null,
                            onWhitelist = if (registeredDomain.isNotBlank()) {
                                {
                                    // Trust the domain by adding to Allow List
                                    scope.launch {
                                        // Add ALLOW rule for this domain (Phase 3 - Allow/Block List)
                                        allowBlockListRepository.addRule(
                                            type = com.kite.phalanx.data.source.local.entity.RuleType.DOMAIN,
                                            value = registeredDomain,
                                            action = com.kite.phalanx.data.source.local.entity.RuleAction.ALLOW,
                                            priority = 80, // High priority for user-trusted domains
                                            notes = "Trusted by user via Security Sheet"
                                        )

                                        // Re-analyze all messages with this domain (will now force GREEN due to ALLOW rule)
                                        viewModel.trustDomainAndReanalyze(registeredDomain)

                                        android.widget.Toast.makeText(
                                            this@SmsDetailActivity,
                                            "Domain added to allow list",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else null,
                            onBlockSender = {
                                // Block the sender's number
                                SmsOperations.blockNumber(this@SmsDetailActivity, sender)
                                // Note: Sheet dismissal is handled by button's onClick in SecurityComponents
                            },
                            onDeleteMessage = {
                                // Delete this specific message (suspend function - use coroutine)
                                scope.launch {
                                    val deleted = SmsOperations.deleteMessage(
                                        context = this@SmsDetailActivity,
                                        sender = selectedMessageForSecurity!!.sender,
                                        timestamp = selectedMessageForSecurity!!.timestamp,
                                        moveToTrashUseCase = moveToTrashUseCase
                                    )
                                    if (deleted) {
                                        // Refresh message list
                                        messages = readSmsMessages(sender)
                                        refreshTrigger++
                                    }
                                }
                                // Note: Sheet dismissal is handled by button's onClick in SecurityComponents
                            }
                        )
                    }
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        // Register broadcast receiver for quota exceeded events
        val filter = android.content.IntentFilter(
            com.kite.phalanx.data.repository.SafeBrowsingRepository.ACTION_QUOTA_EXCEEDED
        )
        registerReceiver(quotaExceededReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        // Unregister broadcast receiver
        try {
            unregisterReceiver(quotaExceededReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    /**
     * Show dialog when Google Safe Browsing quota is exceeded.
     */
    private fun showQuotaExceededDialog() {
        // Only show once per session
        if (quotaExceededDialogShown) return
        quotaExceededDialogShown = true

        // Show toast notification instead of dialog (simpler in Compose context)
        runOnUiThread {
            android.widget.Toast.makeText(
                this,
                "Google Safe Browsing API quota exceeded. Please add your own API key in Settings.",
                android.widget.Toast.LENGTH_LONG
            ).show()

            // Also launch Settings activity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
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

        // Load all messages (SMS + MMS) for this conversation using MessageLoader
        return MessageLoader.loadThreadMessages(this, sender)
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

    /**
     * Determines if a sender can receive replies.
     * Short codes (5-6 digits) and alphanumeric sender IDs typically cannot receive replies.
     */
    private fun canSenderReceiveReplies(sender: String): Boolean {
        // Remove all non-alphanumeric characters for analysis
        val cleanSender = sender.replace(Regex("[^A-Za-z0-9]"), "")

        // Check if it's a short code (5-6 digits only)
        if (cleanSender.matches(Regex("^\\d{5,6}$"))) {
            return false
        }

        // Check if it's alphanumeric (contains both letters and numbers, or only letters)
        // These are typically service sender IDs that can't receive replies
        if (cleanSender.matches(Regex("^[A-Za-z]+$")) ||
            cleanSender.matches(Regex("^[A-Za-z0-9]+$")) && cleanSender.any { it.isLetter() }) {
            return false
        }

        // If it's a normal phone number (10+ digits), it can receive replies
        return cleanSender.matches(Regex("^\\d{10,}$"))
    }
}

@Composable
fun ContactTitle(displayName: String, contactPhoto: ImageBitmap?, sender: String, textSizeScale: Float) {
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
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * textSizeScale
            )
        )
        if (isMuted) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "(Muted)",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * textSizeScale
                ),
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
    textSizeScale: Float,
    verdictCache: Map<Long, com.kite.phalanx.domain.model.Verdict> = emptyMap(),
    registeredDomainCache: Map<Long, String> = emptyMap(),
    onSecurityChipClick: ((SmsMessage) -> Unit)? = null,
    onMessageLongClick: (SmsMessage) -> Unit,
    onMessageClick: (SmsMessage) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onRetry: ((SmsMessage) -> Unit)? = null
) {
    // Scroll to the target index when messages load
    LaunchedEffect(scrollToIndex, messageUiModels.size) {
        if (messageUiModels.isNotEmpty() && scrollToIndex >= 0 && scrollToIndex < messageUiModels.size) {
            listState.scrollToItem(scrollToIndex)
        }
    }

    if (messageUiModels.isEmpty()) {
        // Empty state - no messages in this conversation yet
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Type a message below to start the conversation",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
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
                        NewMessagesDivider(textSizeScale = textSizeScale)
                    }
                    MessageBubble(
                        message = uiModel.message,
                        showTimestamp = uiModel.showTimestamp,
                        isSelected = selectedMessages.contains(uiModel.message.timestamp),
                        textSizeScale = textSizeScale,
                        verdict = verdictCache[uiModel.message.timestamp],
                        registeredDomain = registeredDomainCache[uiModel.message.timestamp] ?: "",
                        onSecurityChipClick = onSecurityChipClick,
                        onLongClick = { onMessageLongClick(uiModel.message) },
                        onClick = { onMessageClick(uiModel.message) },
                        onRetry = onRetry
                    )
                }
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
    textSizeScale: Float,
    verdict: com.kite.phalanx.domain.model.Verdict? = null,
    registeredDomain: String = "",
    onSecurityChipClick: ((SmsMessage) -> Unit)? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onRetry: ((SmsMessage) -> Unit)? = null
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

    // Check if device has multiple SIMs for visual SIM indicator
    val activeSims = remember { SimHelper.getActiveSims(context) }
    val hasMultipleSims = activeSims.size > 1

    // Determine border color and width
    val (borderColor, borderWidth) = when {
        isSelected -> {
            // Selection border takes priority (bright, thick)
            MaterialTheme.colorScheme.primary to 2.dp
        }
        hasMultipleSims && message.isSentByUser && message.subscriptionId != -1 -> {
            // SIM indicator border for dual SIM (subtle, uses SIM color)
            bubbleColor to 1.5.dp
        }
        else -> {
            // No border
            Color.Transparent to 0.dp
        }
    }

    // State for link confirmation dialog
    var showLinkConfirmDialog by remember { mutableStateOf(false) }
    var urlToOpen by remember { mutableStateOf<String?>(null) }

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
                    if (borderWidth > 0.dp) {
                        Modifier.border(
                            width = borderWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show attachments if present
                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { attachment ->
                        AttachmentView(
                            attachment = attachment,
                            maxWidth = 250
                        )
                    }
                }

                // Show text with status indicator inline
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Show text if present
                    if (message.body.isNotBlank()) {
                        val annotatedString = remember(message.body, textColor) {
                            buildAnnotatedStringWithLinks(message.body, textColor)
                        }

                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale,
                                color = textColor
                            ),
                            modifier = Modifier.weight(1f, fill = false),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(
                                    tag = "URL",
                                    start = offset,
                                    end = offset
                                ).firstOrNull()?.let { annotation ->
                                    // Show confirmation dialog before opening URL
                                    urlToOpen = annotation.item
                                    showLinkConfirmDialog = true
                                }
                            }
                        )
                    }

                    // Show delivery status for sent messages
                    if (message.isSentByUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        when (message.deliveryStatus) {
                            DeliveryStatus.PENDING -> {
                                // Show a small circle for pending
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = textColor.copy(alpha = 0.5f)
                                ) {}
                            }
                            DeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DeliveryStatus.DELIVERED -> {
                                // Double checkmark for delivered
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Delivered",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            DeliveryStatus.FAILED -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = if (onRetry != null) {
                                        Modifier.clickable { onRetry(message) }
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Failed to send",
                                        tint = Color(0xFFEF5350), // Red color for error
                                        modifier = Modifier.size(14.dp)
                                    )
                                    if (onRetry != null) {
                                        Text(
                                            text = "Tap to retry",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize * textSizeScale
                                            ),
                                            color = Color(0xFFEF5350)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Show security chip for received messages with non-GREEN verdicts
        if (!message.isSentByUser && verdict != null && verdict.level != VerdictLevel.GREEN) {
            SecurityChip(
                verdict = verdict,
                registeredDomain = registeredDomain,
                onClick = { onSecurityChipClick?.invoke(message) },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Link confirmation dialog
    if (showLinkConfirmDialog && urlToOpen != null) {
        AlertDialog(
            onDismissRequest = { showLinkConfirmDialog = false },
            title = { Text("Open Link?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to open this link?")
                    Text(
                        text = urlToOpen!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLinkConfirmDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                    context.startActivity(intent)
                    urlToOpen = null
                }) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLinkConfirmDialog = false
                    urlToOpen = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NewMessagesDivider(textSizeScale: Float, modifier: Modifier = Modifier) {
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
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * textSizeScale
            ),
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

/**
 * Bottom bar shown when sender cannot receive replies (short codes, alphanumeric sender IDs).
 */
@Composable
fun NonReplyBottomBar(textSizeScale: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "You can't reply to this sender",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageComposer(
    message: String,
    textSizeScale: Float,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendLongClick: () -> Unit = {},
    hasMultipleSims: Boolean = false,
    attachments: List<SelectedAttachment> = emptyList(),
    onAttachmentSelected: (SelectedAttachment) -> Unit = {},
    onAttachmentRemoved: (SelectedAttachment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Analyze SMS encoding and segment count
    val segmentInfo = remember(message) {
        SmsEncodingHelper.analyzeText(message)
    }
    val hasContent = message.isNotBlank() || attachments.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Show attachment previews if any
            if (attachments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachments.forEach { attachment ->
                        AttachmentPreview(
                            attachment = attachment,
                            onRemove = { onAttachmentRemoved(attachment) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attachment button
                AttachmentButton(
                    onAttachmentSelected = onAttachmentSelected
                )

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = onMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message") },
                        maxLines = 4
                    )
                    if (message.isNotEmpty()) {
                        val messageType = if (attachments.isNotEmpty()) "MMS" else "SMS"
                        val segmentDisplay = SmsEncodingHelper.formatSegmentDisplay(segmentInfo)
                        val encodingName = SmsEncodingHelper.getEncodingName(segmentInfo.encoding)

                        // Use warning color when approaching limit
                        val textColor = if (segmentInfo.isApproachingLimit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = "$segmentDisplay • $encodingName • $messageType",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize * textSizeScale
                            ),
                            color = textColor,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    } else if (attachments.isNotEmpty()) {
                        Text(
                            text = "${attachments.size} attachment${if (attachments.size > 1) "s" else ""} • MMS",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize * textSizeScale
                            ),
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
                            if (hasContent) {
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
                        tint = if (hasContent) {
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

/**
 * Reply preview shown above the message composer
 */
@Composable
fun ReplyPreview(
    message: SmsMessage,
    textSizeScale: Float,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vertical accent line
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp),
                color = MaterialTheme.colorScheme.primary
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            // Reply content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Replying to [Sender]"
                Text(
                    text = "Replying to ${message.contactName ?: message.sender}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Message snippet
                val snippet = remember(message) {
                    when {
                        message.body.isNotBlank() -> {
                            if (message.body.length > 50) {
                                message.body.take(50) + "..."
                            } else {
                                message.body
                            }
                        }
                        message.attachments.isNotEmpty() -> {
                            val firstAttachment = message.attachments.first()
                            when {
                                firstAttachment.isImage -> "📷 Photo"
                                firstAttachment.isVideo -> "🎥 Video"
                                firstAttachment.isAudio -> "🎵 Audio"
                                else -> "📎 Attachment"
                            }
                        }
                        else -> "Message"
                    }
                }

                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Dismiss button
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Pinned messages block shown below top bar
 * Persistent during scroll
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedMessagesBlock(
    pinnedMessages: List<PinnedMessage>,
    textSizeScale: Float,
    onPinnedMessageClick: (PinnedMessage) -> Unit,
    onPinnedMessageLongClick: (PinnedMessage) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pinned",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = MaterialTheme.typography.labelMedium.fontSize * textSizeScale
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${pinnedMessages.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * textSizeScale
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pinned messages list
            pinnedMessages.forEach { pinnedMessage ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPinnedMessageClick(pinnedMessage) },
                            onLongClick = { onPinnedMessageLongClick(pinnedMessage) }
                        )
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Vertical accent line
                        Surface(
                            modifier = Modifier
                                .width(3.dp)
                                .height(40.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp)
                        ) {}

                        Spacer(modifier = Modifier.width(10.dp))

                        // Message content
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = pinnedMessage.snippet,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            // Expiry info
                            pinnedMessage.expiryTimestamp?.let { expiry ->
                                val timeLeft = expiry - System.currentTimeMillis()
                                if (timeLeft > 0) {
                                    val daysLeft = (timeLeft / (24 * 60 * 60 * 1000)).toInt()
                                    val hoursLeft = (timeLeft / (60 * 60 * 1000)).toInt()
                                    val expiryText = when {
                                        daysLeft > 0 -> "Unpins in $daysLeft day${if (daysLeft > 1) "s" else ""}"
                                        hoursLeft > 0 -> "Unpins in $hoursLeft hour${if (hoursLeft > 1) "s" else ""}"
                                        else -> "Unpins soon"
                                    }

                                    Text(
                                        text = expiryText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize * textSizeScale
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            } ?: run {
                                Text(
                                    text = "Pinned permanently",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * textSizeScale
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            if (pinnedMessage.hasAttachments) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Has attachment",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize * textSizeScale
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper function to build an AnnotatedString with clickable links.
 * Detects URLs in text and makes them clickable with blue underlined styling.
 */
private fun buildAnnotatedStringWithLinks(text: String, baseColor: Color): AnnotatedString {
    // Regex to match URLs (http, https, www)
    val urlPattern = Regex(
        """(?:(?:https?://)|(?:www\.))[^\s]+""",
        RegexOption.IGNORE_CASE
    )

    return buildAnnotatedString {
        var lastIndex = 0

        urlPattern.findAll(text).forEach { matchResult ->
            // Add text before the URL
            if (matchResult.range.first > lastIndex) {
                withStyle(style = SpanStyle(color = baseColor)) {
                    append(text.substring(lastIndex, matchResult.range.first))
                }
            }

            // Add the URL with annotation and styling
            pushStringAnnotation(
                tag = "URL",
                annotation = matchResult.value
            )
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF2196F3), // Blue for links
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(matchResult.value)
            }
            pop()

            lastIndex = matchResult.range.last + 1
        }

        // Add remaining text after the last URL
        if (lastIndex < text.length) {
            withStyle(style = SpanStyle(color = baseColor)) {
                append(text.substring(lastIndex))
            }
        }
    }
}
