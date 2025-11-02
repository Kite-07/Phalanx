package com.kite.phalanx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SpamListActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhalanxTheme {
                val context = androidx.compose.ui.platform.LocalContext.current

                // Load text size scale from preferences
                val textSizeScale by AppPreferences.getTextSizeScaleFlow(context)
                    .collectAsState(initial = 1.0f)

                var spamList by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
                var selectedThreads by remember { mutableStateOf<Set<String>>(emptySet()) }
                val isSelectionMode = selectedThreads.isNotEmpty()
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                var showUnblockConfirmDialog by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                val refreshSpamList: () -> Unit = {
                    coroutineScope.launch {
                        spamList = loadBlockedConversations()
                    }
                }

                LaunchedEffect(Unit) {
                    spamList = loadBlockedConversations()
                }

                // Refresh spam list when activity resumes (e.g., after returning from SpamDetailActivity)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshSpamList()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isSelectionMode) {
                                    Text("${selectedThreads.size} selected")
                                } else {
                                    Text("Spam and blocked")
                                }
                            },
                            navigationIcon = {
                                if (isSelectionMode) {
                                    IconButton(onClick = { selectedThreads = emptySet() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                                    }
                                } else {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (isSelectionMode) {
                                    IconButton(onClick = {
                                        showDeleteConfirmDialog = true
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                    IconButton(onClick = {
                                        showUnblockConfirmDialog = true
                                    }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Unblock")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    if (spamList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No blocked conversations",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * textSizeScale
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        SpamListScreen(
                            spamList = spamList,
                            selectedThreads = selectedThreads,
                            isSelectionMode = isSelectionMode,
                            textSizeScale = textSizeScale,
                            onThreadClick = { sender ->
                                if (isSelectionMode) {
                                    selectedThreads = if (sender in selectedThreads) {
                                        selectedThreads - sender
                                    } else {
                                        selectedThreads + sender
                                    }
                                } else {
                                    val intent = Intent(this@SpamListActivity, SpamDetailActivity::class.java)
                                    intent.putExtra("sender", sender)
                                    startActivity(intent)
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
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete ${if (selectedThreads.size == 1) "conversation" else "conversations"}?") },
                        text = {
                            Text(
                                if (selectedThreads.size == 1) {
                                    "Are you sure you want to delete this conversation? This action cannot be undone."
                                } else {
                                    "Are you sure you want to delete ${selectedThreads.size} conversations? This action cannot be undone."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirmDialog = false
                                coroutineScope.launch {
                                    selectedThreads.forEach { sender ->
                                        SmsOperations.deleteThread(this@SpamListActivity, sender)
                                    }
                                    selectedThreads = emptySet()
                                    refreshSpamList()
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
                        title = { Text("Unblock ${if (selectedThreads.size == 1) "number" else "numbers"}?") },
                        text = {
                            Text(
                                if (selectedThreads.size == 1) {
                                    "This number will be able to call and message you again."
                                } else {
                                    "${selectedThreads.size} numbers will be able to call and message you again."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUnblockConfirmDialog = false
                                coroutineScope.launch {
                                    selectedThreads.forEach { sender ->
                                        SmsOperations.unblockNumber(this@SpamListActivity, sender)
                                    }
                                    selectedThreads = emptySet()
                                    refreshSpamList()
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

    private suspend fun loadBlockedConversations(): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val blockedNumbers = getBlockedNumbers()
            if (blockedNumbers.isEmpty()) return@withContext emptyList()

            val latestByAddress = LinkedHashMap<String, SmsMessage>()

            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val indexAddress = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = cursor.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = cursor.getColumnIndex(Telephony.Sms.DATE)
                val indexType = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    try {
                        val address = cursor.getString(indexAddress)?.takeIf { it.isNotBlank() }
                            ?: continue

                        // Only include if number is blocked
                        if (!blockedNumbers.contains(address)) continue
                        if (latestByAddress.containsKey(address)) continue

                        val body = cursor.getString(indexBody).orEmpty()
                        val timestamp = cursor.getLong(indexDate)
                        val messageType = cursor.getInt(indexType)
                        val contactPhotoUri = lookupContactPhotoUri(address)
                        val contactName = lookupContactName(address)

                        latestByAddress[address] = SmsMessage(
                            sender = address,
                            body = body,
                            timestamp = timestamp,
                            isSentByUser = isUserMessage(messageType),
                            contactPhotoUri = contactPhotoUri,
                            unreadCount = 0,
                            contactName = contactName ?: address,
                            draftText = null
                        )
                    } catch (e: Exception) {
                        Log.e("SpamListActivity", "Error processing blocked conversation", e)
                        continue
                    }
                }
            }

            latestByAddress.values.toList()
        }
    }

    private fun getBlockedNumbers(): Set<String> {
        val blockedNumbers = mutableSetOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                contentResolver.query(
                    android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    arrayOf(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(
                        android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                    )
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(columnIndex)
                        if (number != null) {
                            blockedNumbers.add(number)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SpamListActivity", "Error loading blocked numbers", e)
            }
        }
        return blockedNumbers
    }

    private fun lookupContactPhotoUri(phoneNumber: String): Uri? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

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
}

@Composable
fun SpamListScreen(
    spamList: List<SmsMessage>,
    selectedThreads: Set<String>,
    isSelectionMode: Boolean,
    textSizeScale: Float,
    onThreadClick: (String) -> Unit,
    onThreadLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = spamList,
            key = { it.sender }
        ) { sms ->
            SpamThreadItem(
                sms = sms,
                isSelected = sms.sender in selectedThreads,
                textSizeScale = textSizeScale,
                onClick = { onThreadClick(sms.sender) },
                onLongClick = { onThreadLongClick(sms.sender) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpamThreadItem(
    sms: SmsMessage,
    isSelected: Boolean,
    textSizeScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                photoUri = sms.contactPhotoUri,
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sms.contactName ?: sms.sender,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * textSizeScale
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeFormatter.format(Date(sms.timestamp)),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * textSizeScale
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = sms.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeScale
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
        // Default avatar icon
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
    context: android.content.Context,
    photoUri: Uri?
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (photoUri == null) return@withContext null

    try {
        context.contentResolver.openInputStream(photoUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}
