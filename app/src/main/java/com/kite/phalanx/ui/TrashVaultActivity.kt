package com.kite.phalanx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import com.kite.phalanx.domain.usecase.RestoreMessageUseCase
import com.kite.phalanx.domain.repository.TrashVaultRepository
import com.kite.phalanx.ui.theme.PhalanxTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Activity for viewing and managing trashed messages.
 *
 * Phase 3 - Safety Rails: Trash Vault UI
 * - Shows all trashed messages with 30-day countdown
 * - Restore messages back to SMS inbox
 * - Permanently delete messages
 */
@AndroidEntryPoint
class TrashVaultActivity : ComponentActivity() {

    private val viewModel: TrashVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhalanxTheme {
                TrashVaultScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

// Data class to represent a group of trashed messages (single message or thread)
data class TrashedItemGroup(
    val threadGroupId: String?, // null for single messages
    val sender: String,
    val latestMessage: TrashedMessageEntity,
    val messageCount: Int,
    val trashedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashVaultScreen(
    viewModel: TrashVaultViewModel,
    onNavigateBack: () -> Unit
) {
    val trashedMessages by viewModel.trashedMessages.collectAsState()

    // Group messages by threadGroupId
    val trashedItemGroups = remember(trashedMessages) {
        val groupMap = mutableMapOf<String, MutableList<TrashedMessageEntity>>()

        trashedMessages.forEach { message ->
            val key = message.threadGroupId ?: message.messageId.toString()
            groupMap.getOrPut(key) { mutableListOf() }.add(message)
        }

        groupMap.map { (groupId, messages) ->
            val latest = messages.maxByOrNull { it.timestamp } ?: messages.first()
            TrashedItemGroup(
                threadGroupId = if (messages.first().threadGroupId != null) groupId else null,
                sender = latest.sender,
                latestMessage = latest,
                messageCount = messages.size,
                trashedAt = latest.trashedAt
            )
        }.sortedByDescending { it.trashedAt }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf<TrashedItemGroup?>(null) }
    var showPermanentDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash Vault") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (trashedItemGroups.isNotEmpty()) {
                        TextButton(onClick = { showPermanentDeleteAllDialog = true }) {
                            Text("Empty Trash")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (trashedItemGroups.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Trash is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Deleted messages appear here for 30 days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Item groups list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(trashedItemGroups, key = { it.threadGroupId ?: it.latestMessage.messageId }) { group ->
                    TrashedItemGroupCard(
                        group = group,
                        onRestore = { viewModel.restoreItemGroup(it) },
                        onDelete = { showDeleteConfirmDialog = it }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Permanent delete confirmation dialog
    if (showDeleteConfirmDialog != null) {
        val group = showDeleteConfirmDialog!!
        val itemDescription = if (group.messageCount > 1) {
            "conversation (${group.messageCount} messages)"
        } else {
            "message"
        }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Permanently delete $itemDescription?") },
            text = { Text("This $itemDescription will be permanently deleted and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.permanentlyDeleteItemGroup(group)
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Empty trash confirmation dialog
    if (showPermanentDeleteAllDialog) {
        val totalMessages = trashedMessages.size
        val itemsText = if (trashedItemGroups.size == 1) {
            "1 item ($totalMessages ${if (totalMessages == 1) "message" else "messages"})"
        } else {
            "${trashedItemGroups.size} items ($totalMessages messages)"
        }

        AlertDialog(
            onDismissRequest = { showPermanentDeleteAllDialog = false },
            title = { Text("Empty trash?") },
            text = { Text("All $itemsText will be permanently deleted and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        showPermanentDeleteAllDialog = false
                    }
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TrashedItemGroupCard(
    group: TrashedItemGroup,
    onRestore: (TrashedItemGroup) -> Unit,
    onDelete: (TrashedItemGroup) -> Unit
) {
    val message = group.latestMessage
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    val daysUntilDeletion = remember(group.trashedAt) {
        val now = System.currentTimeMillis()
        val deletionTime = group.trashedAt + (30L * 24 * 60 * 60 * 1000) // 30 days
        val daysLeft = ((deletionTime - now) / (24 * 60 * 60 * 1000)).toInt()
        maxOf(0, daysLeft)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Sender and message count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (group.messageCount > 1) {
                    Text(
                        text = "${group.messageCount} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message preview (latest message)
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp and deletion countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deletes in $daysUntilDeletion days",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysUntilDeletion < 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Restore button
                    FilledTonalButton(onClick = { onRestore(group) }) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore")
                    }

                    // Permanent delete button
                    OutlinedButton(onClick = { onDelete(group) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class TrashVaultViewModel @Inject constructor(
    private val trashVaultRepository: TrashVaultRepository,
    private val restoreMessageUseCase: RestoreMessageUseCase
) : ViewModel() {

    private val _trashedMessages = MutableStateFlow<List<TrashedMessageEntity>>(emptyList())
    val trashedMessages = _trashedMessages.asStateFlow()

    init {
        loadTrashedMessages()
    }

    private fun loadTrashedMessages() {
        viewModelScope.launch {
            trashVaultRepository.getAllTrashedMessages().collect { messages ->
                _trashedMessages.value = messages.sortedByDescending { it.trashedAt }
            }
        }
    }

    fun restoreMessage(message: TrashedMessageEntity) {
        viewModelScope.launch {
            restoreMessageUseCase.execute(message.messageId)
        }
    }

    fun restoreItemGroup(group: TrashedItemGroup) {
        viewModelScope.launch {
            if (group.threadGroupId != null) {
                // Restore entire thread group
                restoreMessageUseCase.executeThreadGroup(group.threadGroupId)
            } else {
                // Restore single message
                restoreMessageUseCase.execute(group.latestMessage.messageId)
            }
        }
    }

    fun permanentlyDeleteMessage(message: TrashedMessageEntity) {
        viewModelScope.launch {
            trashVaultRepository.permanentlyDelete(message.messageId)
        }
    }

    fun permanentlyDeleteItemGroup(group: TrashedItemGroup) {
        viewModelScope.launch {
            if (group.threadGroupId != null) {
                // Delete entire thread group
                trashVaultRepository.permanentlyDeleteThreadGroup(group.threadGroupId)
            } else {
                // Delete single message
                trashVaultRepository.permanentlyDelete(group.latestMessage.messageId)
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _trashedMessages.value.forEach { message ->
                trashVaultRepository.permanentlyDelete(message.messageId)
            }
        }
    }
}
