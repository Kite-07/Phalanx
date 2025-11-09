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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import com.kite.phalanx.data.source.local.entity.RuleAction
import com.kite.phalanx.data.source.local.entity.RuleType
import com.kite.phalanx.domain.repository.AllowBlockListRepository
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
 * Activity for managing allow/block list rules.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists UI
 * - Add/remove domain rules (allow/block)
 * - Add/remove sender rules (allow/block)
 * - Add/remove pattern rules (regex matching)
 * - Set rule priorities
 */
@AndroidEntryPoint
class AllowBlockListActivity : ComponentActivity() {

    private val viewModel: AllowBlockListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhalanxTheme {
                AllowBlockListScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowBlockListScreen(
    viewModel: AllowBlockListViewModel,
    onNavigateBack: () -> Unit
) {
    val rules by viewModel.rules.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<AllowBlockRuleEntity?>(null) }
    var ruleToDelete by remember { mutableStateOf<AllowBlockRuleEntity?>(null) }

    val tabs = listOf("All", "Allowed", "Blocked")

    // Filter rules based on selected tab
    val filteredRules = when (selectedTab) {
        1 -> rules.filter { it.action == "ALLOW" }
        2 -> rules.filter { it.action == "BLOCK" }
        else -> rules
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Allow/Block Lists") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddRuleDialog = true },
                icon = { Icon(Icons.Default.Add, "Add rule") },
                text = { Text("Add Rule") }
            )
        }
    ) { paddingValues ->
        if (filteredRules.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (selectedTab) {
                            1 -> "No allowed items"
                            2 -> "No blocked items"
                            else -> "No rules yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create rules to allow or block specific domains, senders, or patterns",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Rules list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                items(filteredRules, key = { it.id }) { rule ->
                    RuleListItem(
                        rule = rule,
                        onEdit = { ruleToEdit = it },
                        onDelete = { ruleToDelete = it }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Add rule dialog
    if (showAddRuleDialog) {
        AddRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onAdd = { type, value, action, notes ->
                viewModel.addRule(type, value, action, notes)
                showAddRuleDialog = false
            }
        )
    }

    // Edit rule dialog
    if (ruleToEdit != null) {
        AddRuleDialog(
            existingRule = ruleToEdit,
            onDismiss = { ruleToEdit = null },
            onAdd = { type, value, action, notes ->
                viewModel.editRule(ruleToEdit!!.id, type, value, action, notes)
                ruleToEdit = null
            }
        )
    }

    // Delete confirmation dialog
    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete rule?") },
            text = { Text("This will remove the ${ruleToDelete!!.action.lowercase()} rule for \"${ruleToDelete!!.value}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(ruleToDelete!!.id)
                        ruleToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RuleListItem(
    rule: AllowBlockRuleEntity,
    onEdit: (AllowBlockRuleEntity) -> Unit,
    onDelete: (AllowBlockRuleEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ListItem(
        modifier = Modifier.clickable { onEdit(rule) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        supportingContent = {
            Column {
                Text("Type: ${rule.type.lowercase().replaceFirstChar { it.uppercase() }}")
                if (rule.notes.isNotBlank()) {
                    Text("Note: ${rule.notes}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Added: ${dateFormat.format(Date(rule.createdAt))}", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Icon(
                imageVector = when (rule.type) {
                    "DOMAIN" -> Icons.Default.Language
                    "SENDER" -> Icons.Default.Person
                    "PATTERN" -> Icons.Default.Search
                    else -> Icons.Default.Help
                },
                contentDescription = rule.type,
                tint = when (rule.action) {
                    "ALLOW" -> MaterialTheme.colorScheme.primary
                    "BLOCK" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Action badge
                AssistChip(
                    onClick = { },
                    label = { Text(rule.action) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (rule.action) {
                            "ALLOW" -> MaterialTheme.colorScheme.primaryContainer
                            "BLOCK" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        labelColor = when (rule.action) {
                            "ALLOW" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "BLOCK" -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onDelete(rule) }) {
                    Icon(Icons.Default.Delete, "Delete rule")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    existingRule: AllowBlockRuleEntity? = null,
    onDismiss: () -> Unit,
    onAdd: (RuleType, String, RuleAction, String) -> Unit
) {
    val isEditMode = existingRule != null

    var selectedType by remember { mutableStateOf(
        existingRule?.let { RuleType.valueOf(it.type) } ?: RuleType.DOMAIN
    ) }
    var value by remember { mutableStateOf(existingRule?.value ?: "") }
    var selectedAction by remember { mutableStateOf(
        existingRule?.let { RuleAction.valueOf(it.action) } ?: RuleAction.ALLOW
    ) }
    var notes by remember { mutableStateOf(existingRule?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Rule" else "Add Rule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rule type selector
                Text("Rule Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == RuleType.DOMAIN,
                        onClick = { selectedType = RuleType.DOMAIN },
                        label = { Text("Domain") }
                    )
                    FilterChip(
                        selected = selectedType == RuleType.SENDER,
                        onClick = { selectedType = RuleType.SENDER },
                        label = { Text("Sender") }
                    )
                    FilterChip(
                        selected = selectedType == RuleType.PATTERN,
                        onClick = { selectedType = RuleType.PATTERN },
                        label = { Text("Pattern") }
                    )
                }

                // Value input
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            when (selectedType) {
                                RuleType.DOMAIN -> "Domain (e.g., example.com)"
                                RuleType.SENDER -> "Phone number (e.g., +1234567890)"
                                RuleType.PATTERN -> "Regex pattern"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Action selector
                Text("Action", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedAction == RuleAction.ALLOW,
                        onClick = { selectedAction = RuleAction.ALLOW },
                        label = { Text("Allow") },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        }
                    )
                    FilterChip(
                        selected = selectedAction == RuleAction.BLOCK,
                        onClick = { selectedAction = RuleAction.BLOCK },
                        label = { Text("Block") },
                        leadingIcon = {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                        }
                    )
                }

                // Notes (optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(selectedType, value.trim(), selectedAction, notes.trim()) },
                enabled = value.trim().isNotBlank()
            ) {
                Text(if (isEditMode) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@HiltViewModel
class AllowBlockListViewModel @Inject constructor(
    private val allowBlockListRepository: AllowBlockListRepository
) : ViewModel() {

    private val _rules = MutableStateFlow<List<AllowBlockRuleEntity>>(emptyList())
    val rules: StateFlow<List<AllowBlockRuleEntity>> = _rules.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            allowBlockListRepository.getAllRules().collect { rulesList ->
                _rules.value = rulesList.sortedWith(
                    compareByDescending<AllowBlockRuleEntity> { it.priority }
                        .thenByDescending { it.createdAt }
                )
            }
        }
    }

    fun addRule(type: RuleType, value: String, action: RuleAction, notes: String) {
        viewModelScope.launch {
            allowBlockListRepository.addRule(
                type = type,
                value = value,
                action = action,
                priority = 0, // Default priority
                notes = notes
            )
        }
    }

    fun editRule(ruleId: Long, type: RuleType, value: String, action: RuleAction, notes: String) {
        viewModelScope.launch {
            // Find the existing rule to preserve priority and createdAt
            val existingRule = _rules.value.find { it.id == ruleId } ?: return@launch

            // Update the rule
            val updatedRule = existingRule.copy(
                type = type.name,
                value = value,
                action = action.name,
                notes = notes
            )
            allowBlockListRepository.updateRule(updatedRule)
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            allowBlockListRepository.deleteRule(ruleId)
        }
    }
}
