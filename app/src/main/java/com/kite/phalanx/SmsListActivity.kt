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
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap

// Preferences DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val DONT_ASK_DEFAULT_SMS = booleanPreferencesKey("dont_ask_default_sms")

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

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)

        setContent {
            PhalanxTheme {
                var smsList by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
                var showDefaultSmsDialog by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                var isSearching by remember { mutableStateOf(false) }
                var selectedThreads by remember { mutableStateOf<Set<String>>(emptySet()) }
                val isSelectionMode = selectedThreads.isNotEmpty()
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
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

                LaunchedEffect(Unit) {
                    val missingPermissions = REQUIRED_PERMISSIONS.filter {
                        ContextCompat.checkSelfPermission(context, it) !=
                                PackageManager.PERMISSION_GRANTED
                    }

                    if (missingPermissions.isEmpty()) {
                        refreshSmsList()

                        // Check if we should show default SMS app prompt
                        // Use multiple methods to check if we're the default SMS app
                        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
                        val isDefaultViaPackage = defaultSmsPackage == context.packageName

                        // Alternative check: see if we have the role
                        val roleManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            context.getSystemService(android.app.role.RoleManager::class.java)
                        } else null

                        val isDefaultViaRole = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false

                        val isDefaultSmsApp = isDefaultViaPackage || isDefaultViaRole

                        Log.d("SmsListActivity", "Default SMS package: $defaultSmsPackage")
                        Log.d("SmsListActivity", "This app package: ${context.packageName}")
                        Log.d("SmsListActivity", "Is default via package: $isDefaultViaPackage")
                        Log.d("SmsListActivity", "Is default via role: $isDefaultViaRole")
                        Log.d("SmsListActivity", "Is default (combined): $isDefaultSmsApp")

                        // Only show dialog if not already default SMS app
                        if (!isDefaultSmsApp) {
                            val preferences = context.dataStore.data.first()
                            val dontAsk = preferences[DONT_ASK_DEFAULT_SMS] ?: false

                            Log.d("SmsListActivity", "Don't ask again: $dontAsk")

                            // Show dialog only if user hasn't explicitly dismissed it
                            if (!dontAsk) {
                                Log.d("SmsListActivity", "Showing default SMS dialog")
                                showDefaultSmsDialog = true
                            } else {
                                Log.d("SmsListActivity", "Not showing dialog (user preference)")
                            }
                        } else {
                            Log.d("SmsListActivity", "Already default SMS app, not showing dialog")
                        }
                    } else {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshSmsList()
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
                                    Text("Messages")
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
                                } else {
                                    IconButton(onClick = {
                                        isSearching = !isSearching
                                        if (!isSearching) searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
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

                if (showDefaultSmsDialog) {
                    DefaultSmsDialog(
                        onDismiss = { showDefaultSmsDialog = false },
                        onSetAsDefault = { dontAskAgain ->
                            showDefaultSmsDialog = false
                            if (dontAskAgain) {
                                coroutineScope.launch {
                                    context.dataStore.edit { prefs ->
                                        prefs[DONT_ASK_DEFAULT_SMS] = true
                                    }
                                }
                            }
                            // Open system settings to set default SMS app
                            try {
                                // Try the general default apps settings page
                                // This is more reliable across different Android versions and manufacturers
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                context.startActivity(intent)
                                Log.d("SmsListActivity", "Launched default apps settings")
                            } catch (e: Exception) {
                                Log.e("SmsListActivity", "Failed to launch default apps settings", e)
                            }
                        },
                        onCancel = { dontAskAgain ->
                            showDefaultSmsDialog = false
                            if (dontAskAgain) {
                                coroutineScope.launch {
                                    context.dataStore.edit { prefs ->
                                        prefs[DONT_ASK_DEFAULT_SMS] = true
                                    }
                                }
                            }
                        }
                    )
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
            }
        }
    }

    private suspend fun readSmsMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
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

                while (it.moveToNext()) {
                    try {
                        val address = it.getString(indexAddress)?.takeIf { addr -> addr.isNotBlank() }
                            ?: continue
                        if (latestByAddress.containsKey(address)) continue

                        val body = it.getString(indexBody).orEmpty()
                        val timestamp = it.getLong(indexDate)
                        val messageType = it.getInt(indexType)
                        val contactPhotoUri =
                            if (hasContactPermission()) lookupContactPhotoUri(address) else null
                        val contactName = lookupContactName(address)
                        val unreadCount = SmsOperations.getUnreadCount(this@SmsListActivity, address)
                        val draftText = try {
                            DraftsManager.getDraftSync(this@SmsListActivity, address)
                        } catch (e: Exception) {
                            Log.e("SmsListActivity", "Error loading draft for $address", e)
                            null
                        }

                        latestByAddress[address] = SmsMessage(
                            sender = address,
                            body = body,
                            timestamp = timestamp,
                            isSentByUser = isUserMessage(messageType),
                            contactPhotoUri = contactPhotoUri,
                            unreadCount = unreadCount,
                            contactName = contactName,
                            draftText = draftText
                        )
                    } catch (e: Exception) {
                        Log.e("SmsListActivity", "Error processing SMS message", e)
                        continue
                    }
                }
            }

            return@withContext latestByAddress.values.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("SmsListActivity", "Error reading SMS messages", e)
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
                    Text(
                        text = sms.contactName ?: sms.sender,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
fun DefaultSmsDialog(
    onDismiss: () -> Unit,
    onSetAsDefault: (Boolean) -> Unit,
    onCancel: (Boolean) -> Unit
) {
    var dontAskAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set as Default SMS App") },
        text = {
            Column {
                Text("Would you like to set Phalanx as your default SMS app? This allows Phalanx to send and receive messages.")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { dontAskAgain = !dontAskAgain }
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Don't ask again")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSetAsDefault(dontAskAgain) }) {
                Text("Set as Default")
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancel(dontAskAgain) }) {
                Text("Not Now")
            }
        }
    )
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
