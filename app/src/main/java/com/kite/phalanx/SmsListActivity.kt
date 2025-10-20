package com.kite.phalanx

import android.Manifest
import android.content.Context
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap

// WRITE_SMS and RECEIVE_SMS are automatically granted to default SMS app, don't request them
private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_PHONE_STATE // Required to check default SMS app
)

class SmsListActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhalanxTheme {
                var smsList by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
                var isDefaultSmsApp by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                var isSearching by remember { mutableStateOf(false) }
                var selectedThreads by remember { mutableStateOf<Set<String>>(emptySet()) }
                val isSelectionMode = selectedThreads.isNotEmpty()
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                var showBlockConfirmDialog by remember { mutableStateOf(false) }
                var showMuteDialog by remember { mutableStateOf(false) }
                var showOverflowMenu by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val lifecycleOwner = this@SmsListActivity
                val coroutineScope = rememberCoroutineScope()

                val refreshSmsList = remember(context, coroutineScope) {
                    {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            coroutineScope.launch {
                                smsList = readSmsMessages()
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val hasSmsAccess = permissions[Manifest.permission.READ_SMS] == true ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                    if (hasSmsAccess) {
                        refreshSmsList()
                    }
                }

                // Function to check if app is default SMS app
                val checkDefaultSmsStatus = {
                    val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
                    val isDefaultViaPackage = defaultSmsPackage == context.packageName

                    // Alternative check: see if we have the role
                    val roleManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.getSystemService(android.app.role.RoleManager::class.java)
                    } else null

                    val isDefaultViaRole = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false

                    isDefaultViaPackage || isDefaultViaRole
                }

                LaunchedEffect(Unit) {
                    // Create notification channel
                    NotificationHelper.createNotificationChannel(context)

                    // Check if app is default SMS app
                    isDefaultSmsApp = checkDefaultSmsStatus()

                    val missingPermissions = REQUIRED_PERMISSIONS.filter {
                        ContextCompat.checkSelfPermission(context, it) !=
                                PackageManager.PERMISSION_GRANTED
                    }

                    if (missingPermissions.isEmpty()) {
                        if (isDefaultSmsApp) {
                            refreshSmsList()
                        }
                    } else {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            // Re-check default SMS app status on resume
                            isDefaultSmsApp = checkDefaultSmsStatus()
                            if (isDefaultSmsApp) {
                                refreshSmsList()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Content observer to watch for SMS database changes
                DisposableEffect(context) {
                    val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            super.onChange(selfChange)
                            refreshSmsList()
                        }
                    }

                    context.contentResolver.registerContentObserver(
                        Telephony.Sms.CONTENT_URI,
                        true,
                        smsObserver
                    )

                    onDispose {
                        context.contentResolver.unregisterContentObserver(smsObserver)
                    }
                }

                val filteredSmsList = remember(smsList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        smsList
                    } else {
                        smsList.filter { sms ->
                            sms.sender.contains(searchQuery, ignoreCase = true) ||
                                    sms.body.contains(searchQuery, ignoreCase = true) ||
                                    (sms.contactName?.contains(searchQuery, ignoreCase = true) == true)
                        }
                    }
                }

                // Determine if selected threads should be marked as read or unread
                val selectedThreadsData = remember(smsList, selectedThreads) {
                    smsList.filter { it.sender in selectedThreads }
                }
                val allSelectedAreRead = remember(selectedThreadsData) {
                    selectedThreadsData.isNotEmpty() && selectedThreadsData.all { it.unreadCount == 0 }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isSelectionMode) {
                                    Text("${selectedThreads.size} selected")
                                } else if (isSearching) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search messages...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text("Phalanx")
                                }
                            },
                            navigationIcon = {
                                if (isSelectionMode) {
                                    IconButton(onClick = { selectedThreads = emptySet() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                                    }
                                }
                            },
                            actions = {
                                if (isSelectionMode) {
                                    // Smart button: Mark as unread if all selected are read, otherwise mark as read
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            selectedThreads.forEach { sender ->
                                                if (allSelectedAreRead) {
                                                    SmsOperations.markAsUnread(context, sender)
                                                } else {
                                                    SmsOperations.markAsRead(context, sender)
                                                }
                                            }
                                            selectedThreads = emptySet()
                                            refreshSmsList()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Done,
                                            contentDescription = if (allSelectedAreRead) "Mark as unread" else "Mark as read"
                                        )
                                    }
                                    IconButton(onClick = {
                                        showDeleteConfirmDialog = true
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                    IconButton(onClick = {
                                        showBlockConfirmDialog = true
                                    }) {
                                        Icon(Icons.Default.Warning, contentDescription = "Block")
                                    }
                                    IconButton(onClick = {
                                        showMuteDialog = true
                                    }) {
                                        Icon(Icons.Default.Notifications, contentDescription = "Mute/Unmute")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        isSearching = !isSearching
                                        if (!isSearching) searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                showOverflowMenu = false
                                                startActivity(Intent(this@SmsListActivity, SettingsActivity::class.java))
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Settings, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Spam and blocked") },
                                            onClick = {
                                                showOverflowMenu = false
                                                startActivity(Intent(this@SmsListActivity, SpamListActivity::class.java))
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Warning, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                val intent = Intent(context, ContactPickerActivity::class.java)
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Message")
                        }
                    }
                ) { innerPadding ->
                    if (!isDefaultSmsApp) {
                        // Show "Set as Default" screen
                        SetAsDefaultScreen(
                            onSetAsDefault = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Silently handle error
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        // Show normal SMS list
                        SmsListScreen(
                            smsList = filteredSmsList,
                            selectedThreads = selectedThreads,
                            isSelectionMode = isSelectionMode,
                            onThreadClick = { sender ->
                                if (isSelectionMode) {
                                    selectedThreads = if (sender in selectedThreads) {
                                        selectedThreads - sender
                                    } else {
                                        selectedThreads + sender
                                    }
                                } else {
                                    val intent = Intent(context, SmsDetailActivity::class.java)
                                    intent.putExtra("sender", sender)
                                    context.startActivity(intent)
                                }
                            },
                            onThreadLongClick = { sender ->
                                selectedThreads = if (sender in selectedThreads) {
                                    selectedThreads - sender
                                } else {
                                    selectedThreads + sender
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                if (showDeleteConfirmDialog) {
                    DeleteConfirmDialog(
                        threadCount = selectedThreads.size,
                        onConfirm = {
                            showDeleteConfirmDialog = false
                            coroutineScope.launch {
                                selectedThreads.forEach { sender ->
                                    SmsOperations.deleteThread(context, sender)
                                }
                                selectedThreads = emptySet()
                                refreshSmsList()
                            }
                        },
                        onDismiss = { showDeleteConfirmDialog = false }
                    )
                }

                if (showBlockConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showBlockConfirmDialog = false },
                        title = { Text("Block ${if (selectedThreads.size == 1) "number" else "numbers"}?") },
                        text = {
                            Text(
                                if (selectedThreads.size == 1) {
                                    "Block this number? You can still view the conversation in the Spam folder."
                                } else {
                                    "Block ${selectedThreads.size} numbers? You can still view the conversations in the Spam folder."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBlockConfirmDialog = false
                                coroutineScope.launch {
                                    selectedThreads.forEach { sender ->
                                        SmsOperations.blockNumber(context, sender)
                                    }
                                    selectedThreads = emptySet()
                                    refreshSmsList()
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

                if (showMuteDialog) {
                    MuteConversationsDialog(
                        conversationCount = selectedThreads.size,
                        onMuteFor = { durationMillis ->
                            showMuteDialog = false
                            coroutineScope.launch {
                                selectedThreads.forEach { sender ->
                                    ConversationMutePreferences.muteConversationFor(context, sender, durationMillis)
                                }
                                selectedThreads = emptySet()
                            }
                        },
                        onUnmute = {
                            showMuteDialog = false
                            coroutineScope.launch {
                                selectedThreads.forEach { sender ->
                                    ConversationMutePreferences.unmuteConversation(context, sender)
                                }
                                selectedThreads = emptySet()
                            }
                        },
                        onDismiss = { showMuteDialog = false }
                    )
                }
            }
        }
    }

    private suspend fun readSmsMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID
            )

            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            val latestByAddress = LinkedHashMap<String, SmsMessage>()
            cursor?.use {
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
                val indexType = it.getColumnIndex(Telephony.Sms.TYPE)
                val indexSubId = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    try {
                        val address = it.getString(indexAddress)?.takeIf { addr -> addr.isNotBlank() }
                            ?: continue
                        if (latestByAddress.containsKey(address)) continue

                        // Skip blocked numbers - they should only appear in spam folder
                        if (SmsOperations.isNumberBlocked(this@SmsListActivity, address)) continue

                        val body = it.getString(indexBody).orEmpty()
                        val timestamp = it.getLong(indexDate)
                        val messageType = it.getInt(indexType)
                        val subId = if (indexSubId >= 0) it.getInt(indexSubId) else -1
                        val contactPhotoUri =
                            if (hasContactPermission()) lookupContactPhotoUri(address) else null
                        val contactName = lookupContactName(address)
                        val unreadCount = SmsOperations.getUnreadCount(this@SmsListActivity, address)
                        val draftText = try {
                            DraftsManager.getDraftSync(this@SmsListActivity, address)
                        } catch (e: Exception) {
                            null
                        }

                        latestByAddress[address] = SmsMessage(
                            sender = address,
                            body = body,
                            timestamp = timestamp,
                            isSentByUser = isUserMessage(messageType),
                            contactPhotoUri = contactPhotoUri,
                            unreadCount = unreadCount,
                            contactName = formatDisplayName(address, contactName),
                            draftText = draftText,
                            subscriptionId = subId
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }

            return@withContext latestByAddress.values.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    private fun hasContactPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun lookupContactPhotoUri(phoneNumber: String): Uri? {
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

    /**
     * Formats phone number with country flag emoji for unknown numbers
     * Returns "contactName" if known, or "🇺🇸 phoneNumber" for unknown numbers
     */
    private fun formatDisplayName(phoneNumber: String, contactName: String?): String {
        if (contactName != null) return contactName

        // Extract country code from phone number
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val flag = when {
            cleanNumber.startsWith("+1") -> "🇺🇸"  // US/Canada flag
            cleanNumber.startsWith("+44") -> "🇬🇧" // UK flag
            cleanNumber.startsWith("+91") -> "🇮🇳" // India flag
            cleanNumber.startsWith("+86") -> "🇨🇳" // China flag
            cleanNumber.startsWith("+81") -> "🇯🇵" // Japan flag
            cleanNumber.startsWith("+82") -> "🇰🇷" // South Korea flag
            cleanNumber.startsWith("+33") -> "🇫🇷" // France flag
            cleanNumber.startsWith("+49") -> "🇩🇪" // Germany flag
            cleanNumber.startsWith("+61") -> "🇦🇺" // Australia flag
            cleanNumber.startsWith("+52") -> "🇲🇽" // Mexico flag
            cleanNumber.startsWith("+55") -> "🇧🇷" // Brazil flag
            cleanNumber.startsWith("+39") -> "🇮🇹" // Italy flag
            cleanNumber.startsWith("+34") -> "🇪🇸" // Spain flag
            cleanNumber.startsWith("+7") -> "🇷🇺"  // Russia flag
            cleanNumber.startsWith("+27") -> "🇿🇦" // South Africa flag
            else -> null
        }

        return if (flag != null) {
            "$flag $phoneNumber"
        } else {
            phoneNumber
        }
    }
}

@Composable
fun SmsListScreen(
    smsList: List<SmsMessage>,
    selectedThreads: Set<String>,
    isSelectionMode: Boolean,
    onThreadClick: (String) -> Unit,
    onThreadLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(
            items = smsList,
            key = { it.sender }
        ) { sms ->
            SmsCard(
                sms = sms,
                isSelected = sms.sender in selectedThreads,
                isSelectionMode = isSelectionMode,
                onClick = { onThreadClick(sms.sender) },
                onLongClick = { onThreadLongClick(sms.sender) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsCard(
    sms: SmsMessage,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val previewText = remember(sms.body, sms.isSentByUser, sms.draftText) {
        // Show draft if it exists
        if (!sms.draftText.isNullOrBlank()) {
            "Draft: ${sms.draftText.trim()}"
        } else {
            val trimmed = sms.body.trim()
            if (sms.isSentByUser && trimmed.isNotEmpty()) {
                "You: $trimmed"
            } else {
                trimmed
            }
        }
    }

    // Get SIM info if subscription ID is valid
    val simInfo = remember(sms.subscriptionId) {
        if (sms.subscriptionId != -1) {
            val info = SimHelper.getSimInfo(context, sms.subscriptionId)
            info
        } else {
            null
        }
    }
    val activeSims = remember { SimHelper.getActiveSims(context) }
    val hasMultipleSims = activeSims.size > 1

    // Check if conversation is muted
    val isMuted by ConversationMutePreferences.isConversationMutedFlow(context, sms.sender)
        .collectAsState(initial = false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (sms.unreadCount > 0) {
                        Badge()
                    }
                }
            ) {
                ContactAvatar(
                    photoUri = sms.contactPhotoUri,
                    contentDescription = "Profile photo for ${sms.sender}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sms.contactName ?: sms.sender, // contactName now includes country code prefix
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isMuted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Don't show SIM badges in thread list per user request
                    }
                    Text(
                        text = timeFormatter.format(Date(sms.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = previewText,
                    style = if (sms.unreadCount > 0) {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    } else if (!sms.draftText.isNullOrBlank()) {
                        // Draft text in italic
                        MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (!sms.draftText.isNullOrBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    photoUri: Uri?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = loadContactPhotoBitmap(context, photoUri)
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // Default avatar icon - same style as SmsDetailActivity
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private suspend fun loadContactPhotoBitmap(
    context: Context,
    photoUri: Uri?
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (photoUri == null) return@withContext null

    try {
        context.contentResolver.openInputStream(photoUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { bitmap ->
                bitmap.asImageBitmap()
            }
        }
    } catch (securityException: SecurityException) {
        null
    }
}

@Composable
fun SetAsDefaultScreen(
    onSetAsDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set Phalanx as Default",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To use Phalanx, you need to set it as your default SMS app. This allows Phalanx to send and receive messages.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSetAsDefault,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Open Settings",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    threadCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Conversation${if (threadCount > 1) "s" else ""}?") },
        text = {
            Text(
                if (threadCount > 1) {
                    "Are you sure you want to delete $threadCount conversations? This action cannot be undone."
                } else {
                    "Are you sure you want to delete this conversation? This action cannot be undone."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MuteConversationsDialog(
    conversationCount: Int,
    onMuteFor: (Long) -> Unit,
    onUnmute: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute Conversation${if (conversationCount > 1) "s" else ""}?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Choose how long to mute ${if (conversationCount > 1) "these conversations" else "this conversation"}:",
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    onClick = { onMuteFor(ConversationMutePreferences.MuteDuration.ONE_HOUR) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("For 1 hour", modifier = Modifier.fillMaxWidth())
                }

                TextButton(
                    onClick = { onMuteFor(ConversationMutePreferences.MuteDuration.ONE_DAY) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("For 1 day", modifier = Modifier.fillMaxWidth())
                }

                TextButton(
                    onClick = { onMuteFor(ConversationMutePreferences.MuteDuration.ONE_WEEK) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("For 1 week", modifier = Modifier.fillMaxWidth())
                }

                TextButton(
                    onClick = { onMuteFor(ConversationMutePreferences.MuteDuration.ALWAYS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Always", modifier = Modifier.fillMaxWidth())
                }

                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                TextButton(
                    onClick = onUnmute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unmute", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
