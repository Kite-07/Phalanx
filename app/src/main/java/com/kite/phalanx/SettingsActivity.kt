package com.kite.phalanx

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private var dndPermissionCheckNeeded = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhalanxTheme {
                val scope = rememberCoroutineScope()
                val activeSims = remember { SimHelper.getActiveSims(this@SettingsActivity) }
                var selectedSimId by remember { mutableStateOf(-1) }

                // App preferences
                var deliveryReportsEnabled by remember { mutableStateOf(false) }
                var mmsAutoDownloadWifi by remember { mutableStateOf(true) }
                var mmsAutoDownloadCellular by remember { mutableStateOf(false) }
                var bypassDnd by remember { mutableStateOf(false) }
                var refreshTrigger by remember { mutableStateOf(0) }

                // Security preferences
                var customApiKey by remember { mutableStateOf("") }
                var hasCustomKey by remember { mutableStateOf(false) }

                // Listen for lifecycle events (specifically onResume)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            // Trigger a refresh when activity resumes (e.g., after returning from settings)
                            refreshTrigger++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Load current default SIM
                LaunchedEffect(refreshTrigger) {
                    selectedSimId = SimPreferences.getDefaultSim(this@SettingsActivity)
                    // If no default set, use system default
                    if (selectedSimId == -1) {
                        selectedSimId = SimHelper.getDefaultSmsSubscriptionId(this@SettingsActivity)
                    }

                    // Load app preferences
                    deliveryReportsEnabled = AppPreferences.getDeliveryReports(this@SettingsActivity)
                    mmsAutoDownloadWifi = AppPreferences.getMmsAutoDownloadWifi(this@SettingsActivity)
                    mmsAutoDownloadCellular = AppPreferences.getMmsAutoDownloadCellular(this@SettingsActivity)
                    bypassDnd = AppPreferences.getBypassDnd(this@SettingsActivity)

                    // Load security preferences
                    hasCustomKey = SafeBrowsingPreferences.hasCustomApiKey(this@SettingsActivity)
                    if (hasCustomKey) {
                        val currentKey = SafeBrowsingPreferences.getApiKey(this@SettingsActivity)
                        if (currentKey != SafeBrowsingPreferences.DEFAULT_API_KEY) {
                            customApiKey = currentKey
                        }
                    }

                    // Check if user granted DND permission after returning from settings
                    if (dndPermissionCheckNeeded) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            notificationManager.isNotificationPolicyAccessGranted) {
                            // Permission granted, enable bypass DND
                            AppPreferences.setBypassDnd(this@SettingsActivity, true)
                            NotificationHelper.createNotificationChannel(this@SettingsActivity)
                            bypassDnd = true
                        }
                        dndPermissionCheckNeeded = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // SIM Settings Section
                        if (activeSims.isNotEmpty()) {
                            item {
                                SettingsSectionHeader(title = "SIM Settings")
                            }

                            item {
                                SettingsDescription(
                                    text = "Choose which SIM to use by default for sending messages. You can override this for individual conversations by long-pressing the send button."
                                )
                            }

                            items(activeSims) { sim ->
                                var bubbleColor by remember { mutableStateOf(Color.Blue) }

                                // Load the bubble color for this SIM
                                LaunchedEffect(sim.subscriptionId) {
                                    bubbleColor = SimPreferences.getBubbleColorForSim(
                                        this@SettingsActivity,
                                        sim.subscriptionId
                                    )
                                }

                                SimSettingsItem(
                                    sim = sim,
                                    isSelected = sim.subscriptionId == selectedSimId,
                                    bubbleColor = bubbleColor,
                                    onDefaultClick = {
                                        selectedSimId = sim.subscriptionId
                                        scope.launch {
                                            SimPreferences.setDefaultSim(
                                                this@SettingsActivity,
                                                sim.subscriptionId
                                            )
                                        }
                                    },
                                    onColorSelected = { color ->
                                        bubbleColor = color
                                        scope.launch {
                                            SimPreferences.setBubbleColorForSim(
                                                this@SettingsActivity,
                                                sim.subscriptionId,
                                                color
                                            )
                                        }
                                    }
                                )
                            }

                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }

                        // Notifications Section
                        item {
                            SettingsSectionHeader(title = "Notifications")
                        }

                        item {
                            SettingsClickableItem(
                                title = "Notification settings",
                                subtitle = "Manage app notifications",
                                onClick = {
                                    // Open app notification settings
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                    startActivity(intent)
                                }
                            )
                        }

                        item {
                            SettingsSwitchItem(
                                title = "Bypass Do Not Disturb",
                                subtitle = "Allow notifications to sound even when Do Not Disturb is on",
                                checked = bypassDnd,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        // Check if we already have DND access permission
                                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                            !notificationManager.isNotificationPolicyAccessGranted) {
                                            // Need to request permission - open settings
                                            dndPermissionCheckNeeded = true
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                            startActivity(intent)
                                        } else {
                                            // Permission already granted, enable the setting
                                            bypassDnd = true
                                            scope.launch {
                                                AppPreferences.setBypassDnd(this@SettingsActivity, true)
                                                // Recreate notification channel with new settings
                                                NotificationHelper.createNotificationChannel(this@SettingsActivity)
                                            }
                                        }
                                    } else {
                                        // Disable bypass DND
                                        bypassDnd = false
                                        scope.launch {
                                            AppPreferences.setBypassDnd(this@SettingsActivity, false)
                                            // Recreate notification channel with new settings
                                            NotificationHelper.createNotificationChannel(this@SettingsActivity)
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Messages Section
                        item {
                            SettingsSectionHeader(title = "Messages")
                        }

                        item {
                            SettingsSwitchItem(
                                title = "Delivery reports",
                                subtitle = "Request notification when message is delivered",
                                checked = deliveryReportsEnabled,
                                onCheckedChange = { enabled ->
                                    deliveryReportsEnabled = enabled
                                    scope.launch {
                                        AppPreferences.setDeliveryReports(this@SettingsActivity, enabled)
                                    }
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // MMS Section
                        item {
                            SettingsSectionHeader(title = "MMS")
                        }

                        item {
                            SettingsSwitchItem(
                                title = "Auto-download MMS on Wi-Fi",
                                subtitle = "Automatically download multimedia messages when connected to Wi-Fi",
                                checked = mmsAutoDownloadWifi,
                                onCheckedChange = { enabled ->
                                    mmsAutoDownloadWifi = enabled
                                    scope.launch {
                                        AppPreferences.setMmsAutoDownloadWifi(this@SettingsActivity, enabled)
                                    }
                                }
                            )
                        }

                        item {
                            SettingsSwitchItem(
                                title = "Auto-download MMS on cellular",
                                subtitle = "Automatically download multimedia messages on mobile data",
                                checked = mmsAutoDownloadCellular,
                                onCheckedChange = { enabled ->
                                    mmsAutoDownloadCellular = enabled
                                    scope.launch {
                                        AppPreferences.setMmsAutoDownloadCellular(this@SettingsActivity, enabled)
                                    }
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Appearance Section
                        item {
                            SettingsSectionHeader(title = "Appearance")
                        }

                        item {
                            var textSizeScale by remember { mutableFloatStateOf(1.0f) }

                            // Load text size from preferences
                            LaunchedEffect(Unit) {
                                textSizeScale = AppPreferences.getTextSizeScale(this@SettingsActivity)
                            }

                            TextSizeSliderItem(
                                title = "Text size",
                                subtitle = getTextSizeLabel(textSizeScale),
                                value = textSizeScale,
                                onValueChange = { newScale ->
                                    textSizeScale = newScale
                                },
                                onValueChangeFinished = {
                                    scope.launch {
                                        AppPreferences.setTextSizeScale(this@SettingsActivity, textSizeScale)
                                    }
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Security Section
                        item {
                            SettingsSectionHeader(title = "Security")
                        }

                        item {
                            SettingsDescription(
                                text = "Phalanx uses Google Safe Browsing to check URLs for phishing and malware. The app provides a default API key, but if you exceed the quota, you can add your own key from Google Cloud Console."
                            )
                        }

                        item {
                            CustomApiKeyInput(
                                currentKey = customApiKey,
                                hasCustomKey = hasCustomKey,
                                onKeyChange = { newKey ->
                                    customApiKey = newKey
                                },
                                onSave = {
                                    scope.launch {
                                        SafeBrowsingPreferences.setCustomApiKey(this@SettingsActivity, customApiKey)
                                        hasCustomKey = SafeBrowsingPreferences.hasCustomApiKey(this@SettingsActivity)
                                    }
                                },
                                onClear = {
                                    scope.launch {
                                        SafeBrowsingPreferences.clearCustomApiKey(this@SettingsActivity)
                                        customApiKey = ""
                                        hasCustomKey = false
                                    }
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Phase 3: Safety Rails Section
                        item {
                            SettingsSectionHeader(title = "Safety Rails")
                        }

                        item {
                            SettingsDescription(
                                text = "Advanced security features to protect against phishing and malicious messages."
                            )
                        }

                        item {
                            SettingsClickableItem(
                                title = "Security Settings",
                                subtitle = "Adjust threat detection sensitivity and security preferences",
                                onClick = {
                                    startActivity(Intent(this@SettingsActivity, com.kite.phalanx.ui.SecuritySettingsActivity::class.java))
                                }
                            )
                        }

                        item {
                            SettingsClickableItem(
                                title = "Allow/Block Lists",
                                subtitle = "Manage trusted domains and blocked senders",
                                onClick = {
                                    startActivity(Intent(this@SettingsActivity, com.kite.phalanx.ui.AllowBlockListActivity::class.java))
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }

    private fun getTextSizeLabel(scale: Float): String {
        return when {
            scale < 0.8f -> "Extra Small (${(scale * 100).toInt()}%)"
            scale < 0.95f -> "Small (${(scale * 100).toInt()}%)"
            scale <= 1.05f -> "Normal (${(scale * 100).toInt()}%)"
            scale <= 1.3f -> "Large (${(scale * 100).toInt()}%)"
            else -> "Extra Large (${(scale * 100).toInt()}%)"
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SimSettingsItem(
    sim: SimInfo,
    isSelected: Boolean,
    bubbleColor: Color,
    onDefaultClick: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Default SIM selection row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDefaultClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onDefaultClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            // SIM color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(sim.color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sim.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (sim.carrierName != null) {
                    Text(
                        text = sim.carrierName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sim.phoneNumber != null) {
                    Text(
                        text = sim.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bubble color picker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 8.dp)
        ) {
            Text(
                text = "Message bubble color",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorPicker(
                selectedColor = bubbleColor,
                onColorSelected = onColorSelected
            )
        }
    }
}

@Composable
fun ColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF3F51B5), // Indigo
        Color(0xFFE91E63), // Pink
        Color(0xFF009688), // Teal
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF795548)  // Brown
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors.size) { index ->
            val color = colors[index]
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selectedColor) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

@Composable
fun TextSizeSliderItem(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0.7f..1.6f,
            steps = 8, // 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun CustomApiKeyInput(
    currentKey: String,
    hasCustomKey: Boolean,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Custom Google Safe Browsing API Key",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        if (hasCustomKey) {
            Text(
                text = "Using custom API key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = "Using default API key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = currentKey,
            onValueChange = onKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Enter your Google Safe Browsing API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasCustomKey) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset to Default")
                }
            }

            Button(
                onClick = onSave,
                enabled = currentKey.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (hasCustomKey) "Update" else "Save")
            }
        }
    }
}
