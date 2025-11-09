package com.kite.phalanx.ui

import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.phalanx.domain.repository.SenderPackRepository
import com.kite.phalanx.ui.theme.PhalanxTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity for security settings configuration.
 *
 * Phase 3 - Safety Rails: Security Settings UI
 * - Sensitivity level slider (Low/Medium/High)
 * - Per-SIM security toggles for dual-SIM devices
 * - OTP pass-through toggle (auto-allow OTP messages)
 *
 * Phase 4 - Sender Intelligence:
 * - Region selection for sender pack (IN, US, GB, etc.)
 *
 * TODO: Integrate Proto DataStore for settings persistence
 */
@AndroidEntryPoint
class SecuritySettingsActivity : ComponentActivity() {

    private val viewModel: SecuritySettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhalanxTheme {
                SecuritySettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: SecuritySettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val sensitivityLevel by viewModel.sensitivityLevel.collectAsState()
    val otpPassThrough by viewModel.otpPassThrough.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val simSettings by viewModel.simSettings.collectAsState()
    var showRegionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sensitivity Level Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Threat Detection Sensitivity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Controls how aggressively Phalanx flags suspicious messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sensitivity slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low", style = MaterialTheme.typography.labelSmall)
                            Text("Medium", style = MaterialTheme.typography.labelSmall)
                            Text("High", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = sensitivityLevel.toFloat(),
                            onValueChange = { viewModel.setSensitivityLevel(it.toInt()) },
                            valueRange = 0f..2f,
                            steps = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description based on level
                        val description = when (sensitivityLevel) {
                            0 -> "Low sensitivity: Only flag obvious threats with high confidence"
                            1 -> "Medium sensitivity: Balanced threat detection (recommended)"
                            2 -> "High sensitivity: Flag even slightly suspicious patterns"
                            else -> ""
                        }
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // OTP Pass-Through Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Allow OTP Messages",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Automatically mark one-time password (OTP) messages as safe",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = otpPassThrough,
                        onCheckedChange = { viewModel.setOtpPassThrough(it) }
                    )
                }
            }

            // Region Selection Section (Phase 4)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRegionDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sender Intelligence Region",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verifies sender IDs for carriers, banks, and services in: ${viewModel.getRegionName(selectedRegion)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Region Selection Dialog
            if (showRegionDialog) {
                AlertDialog(
                    onDismissRequest = { showRegionDialog = false },
                    title = { Text("Select Region") },
                    text = {
                        Column {
                            Text(
                                text = "Choose your region to enable sender verification for local carriers, banks, and services.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            viewModel.availableRegions.forEach { (code, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setRegion(code)
                                            showRegionDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (code == selectedRegion) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRegionDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Per-SIM Settings Section
            if (simSettings.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Per-SIM Security Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enable or disable security analysis for each SIM card",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        simSettings.forEach { (simId, enabled) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "SIM $simId",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { viewModel.setSimEnabled(simId, it) }
                                )
                            }
                            if (simId != simSettings.keys.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            // Additional Settings Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "About Security Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• All security analysis happens on-device\n" +
                                "• No message content is sent to external servers\n" +
                                "• Allow/Block lists take precedence over threat detection\n" +
                                "• Settings are saved locally and persist across app restarts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class SimSettings(
    val simId: Int,
    val enabled: Boolean
)

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val senderPackRepository: SenderPackRepository
) : ViewModel() {

    // TODO: Replace with Proto DataStore persistence
    private val _sensitivityLevel = MutableStateFlow(1) // Default: Medium
    val sensitivityLevel: StateFlow<Int> = _sensitivityLevel.asStateFlow()

    private val _otpPassThrough = MutableStateFlow(true) // Default: enabled
    val otpPassThrough: StateFlow<Boolean> = _otpPassThrough.asStateFlow()

    private val _selectedRegion = MutableStateFlow("IN") // Default: India
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _simSettings = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val simSettings: StateFlow<Map<Int, Boolean>> = _simSettings.asStateFlow()

    /**
     * Available sender intelligence regions.
     * Each region has its own verified sender pack.
     */
    val availableRegions = mapOf(
        "IN" to "India",
        "US" to "United States",
        "GB" to "United Kingdom",
        "AU" to "Australia",
        "CA" to "Canada"
    )

    init {
        loadSettings()
        detectSimCards()
    }

    private fun loadSettings() {
        // TODO: Load from Proto DataStore
        // For now, using SharedPreferences as placeholder
        val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
        _sensitivityLevel.value = prefs.getInt("sensitivity_level", 1)
        _otpPassThrough.value = prefs.getBoolean("otp_pass_through", true)
        _selectedRegion.value = prefs.getString("sender_pack_region", "IN") ?: "IN"
    }

    private fun detectSimCards() {
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subscriptionManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                if (activeSubscriptions != null && activeSubscriptions.size > 1) {
                    // Dual SIM device
                    val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
                    val simMap = mutableMapOf<Int, Boolean>()
                    activeSubscriptions.forEachIndexed { index, _ ->
                        val simId = index + 1
                        simMap[simId] = prefs.getBoolean("sim_${simId}_enabled", true)
                    }
                    _simSettings.value = simMap
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted - skip SIM detection
        }
    }

    fun setSensitivityLevel(level: Int) {
        viewModelScope.launch {
            _sensitivityLevel.value = level
            saveSettings()
        }
    }

    fun setOtpPassThrough(enabled: Boolean) {
        viewModelScope.launch {
            _otpPassThrough.value = enabled
            saveSettings()
        }
    }

    fun setSimEnabled(simId: Int, enabled: Boolean) {
        viewModelScope.launch {
            _simSettings.value = _simSettings.value.toMutableMap().apply {
                this[simId] = enabled
            }
            saveSettings()
        }
    }

    /**
     * Set sender pack region (Phase 4).
     * Loads the new sender pack for the selected region.
     */
    fun setRegion(regionCode: String) {
        viewModelScope.launch {
            _selectedRegion.value = regionCode
            saveSettings()

            // Load sender pack for new region
            try {
                val result = senderPackRepository.loadPack(regionCode)
                if (result.isValid) {
                    android.util.Log.i("SecuritySettings", "Loaded sender pack for $regionCode")
                } else {
                    android.util.Log.w("SecuritySettings", "Failed to load pack: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SecuritySettings", "Error loading sender pack", e)
            }
        }
    }

    /**
     * Get human-readable region name from code.
     */
    fun getRegionName(code: String): String {
        return availableRegions[code] ?: code
    }

    private fun saveSettings() {
        // TODO: Save to Proto DataStore
        // For now, using SharedPreferences as placeholder
        val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("sensitivity_level", _sensitivityLevel.value)
            putBoolean("otp_pass_through", _otpPassThrough.value)
            putString("sender_pack_region", _selectedRegion.value)
            _simSettings.value.forEach { (simId, enabled) ->
                putBoolean("sim_${simId}_enabled", enabled)
            }
            apply()
        }
    }
}
