package com.kite.phalanx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchivedMessagesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhalanxTheme {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                var smsList by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
                var selectedThreads by remember { mutableStateOf<Set<String>>(emptySet()) }
                val isSelectionMode = selectedThreads.isNotEmpty()
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                // Load archived threads
                val archivedThreads by ArchivedThreadsPreferences.getArchivedThreadsFlow(context)
                    .collectAsState(initial = emptySet())

                // Load all SMS messages
                val refreshSmsList: () -> Unit = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        coroutineScope.launch {
                            val allMessages = readSmsMessages()
                            // Filter to only archived threads
                            smsList = allMessages.filter { it.sender in archivedThreads }
                        }
                    }
                }

                // Refresh on composition
                androidx.compose.runtime.LaunchedEffect(archivedThreads) {
                    refreshSmsList()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isSelectionMode) {
                                    Text("${selectedThreads.size} selected")
                                } else {
                                    Text("Archived")
                                }
                            },
                            navigationIcon = {
                                if (isSelectionMode) {
                                    IconButton(onClick = { selectedThreads = emptySet() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                                    }
                                } else {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (isSelectionMode) {
                                    // Unarchive button
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            selectedThreads.forEach { sender ->
                                                ArchivedThreadsPreferences.unarchiveThread(context, sender)
                                            }
                                            selectedThreads = emptySet()
                                            refreshSmsList()
                                        }
                                    }) {
                                        Icon(Icons.Default.Unarchive, contentDescription = "Unarchive")
                                    }
                                    // Delete button
                                    IconButton(onClick = {
                                        showDeleteConfirmDialog = true
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (smsList.isEmpty()) {
                            // Empty state
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = "No archived conversations",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Conversations you archive will appear here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Archived conversation list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(smsList, key = { it.sender }) { sms ->
                                    ArchivedThreadItem(
                                        sms = sms,
                                        isSelected = sms.sender in selectedThreads,
                                        onClick = { sender ->
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
                                        onLongClick = { sender ->
                                            selectedThreads = if (sender in selectedThreads) {
                                                selectedThreads - sender
                                            } else {
                                                selectedThreads + sender
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Delete confirmation dialog
                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete ${if (selectedThreads.size == 1) "conversation" else "conversations"}?") },
                        text = {
                            Text(
                                if (selectedThreads.size == 1) {
                                    "This will permanently delete this conversation."
                                } else {
                                    "This will permanently delete ${selectedThreads.size} conversations."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirmDialog = false
                                coroutineScope.launch {
                                    selectedThreads.forEach { sender ->
                                        SmsOperations.deleteThread(context, sender)
                                    }
                                    selectedThreads = emptySet()
                                    refreshSmsList()
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
            }
        }
    }

    private suspend fun readSmsMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        try {
            // Load all conversations (both SMS and MMS) using MessageLoader
            val latestByAddress = MessageLoader.loadConversationList(this@ArchivedMessagesActivity)

            // Enrich with contact info, unread count, and drafts
            val enrichedMessages = latestByAddress.map { (address, message) ->
                val contactPhotoUri =
                    if (hasContactPermission()) lookupContactPhotoUri(address) else null
                val contactName = lookupContactName(address)
                val unreadCount = SmsOperations.getUnreadCount(this@ArchivedMessagesActivity, address)
                val draftText = try {
                    DraftsManager.getDraftSync(this@ArchivedMessagesActivity, address)
                } catch (e: Exception) {
                    null
                }

                message.copy(
                    contactPhotoUri = contactPhotoUri,
                    unreadCount = unreadCount,
                    contactName = formatDisplayName(address, contactName),
                    draftText = draftText
                )
            }

            return@withContext enrichedMessages.sortedByDescending { it.timestamp }
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
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (securityException: SecurityException) {
            null
        }
    }

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
            cleanNumber.startsWith("+49") -> "🇩🇪" // Germany flag
            cleanNumber.startsWith("+33") -> "🇫🇷" // France flag
            cleanNumber.startsWith("+39") -> "🇮🇹" // Italy flag
            cleanNumber.startsWith("+34") -> "🇪🇸" // Spain flag
            cleanNumber.startsWith("+7") -> "🇷🇺"  // Russia flag
            cleanNumber.startsWith("+82") -> "🇰🇷" // South Korea flag
            cleanNumber.startsWith("+61") -> "🇦🇺" // Australia flag
            cleanNumber.startsWith("+55") -> "🇧🇷" // Brazil flag
            cleanNumber.startsWith("+52") -> "🇲🇽" // Mexico flag
            cleanNumber.startsWith("+27") -> "🇿🇦" // South Africa flag
            else -> ""
        }

        return if (flag.isNotEmpty()) "$flag $phoneNumber" else phoneNumber
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchivedThreadItem(
    sms: SmsMessage,
    isSelected: Boolean,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = { onClick(sms.sender) },
                onLongClick = { onLongClick(sms.sender) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contact avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Message content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Sender name/number
            Text(
                text = sms.contactName ?: sms.sender,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Message preview
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatTimestamp(sms.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}
