package com.kite.phalanx

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kite.phalanx.ui.theme.PhalanxTheme

/**
 * First-Run Activity - Phase 4 Deliverable
 *
 * Shows privacy explainer and requests Default SMS app role.
 * Displayed only on first app launch.
 */
class FirstRunActivity : ComponentActivity() {

    private val requestDefaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if user granted default SMS app role
        val isDefaultSms = Telephony.Sms.getDefaultSmsPackage(this) == packageName

        if (isDefaultSms) {
            // Success - mark first run complete and navigate to main app
            markFirstRunComplete()
            navigateToMainApp()
        } else {
            // User declined - show explanation (Assist Mode is deferred)
            // For now, just allow them to continue
            markFirstRunComplete()
            navigateToMainApp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already completed first run, navigate directly to main app
        if (isFirstRunComplete()) {
            navigateToMainApp()
            finish()
            return
        }

        setContent {
            PhalanxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FirstRunScreen(
                        onContinue = { requestDefaultSmsRole() },
                        onSkip = {
                            // Allow skip for now (Assist Mode deferred)
                            markFirstRunComplete()
                            navigateToMainApp()
                        }
                    )
                }
            }
        }
    }

    private fun isFirstRunComplete(): Boolean {
        val prefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("first_run_complete", false)
    }

    private fun markFirstRunComplete() {
        val prefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("first_run_complete", true).apply()
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use RoleManager API
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    requestDefaultSmsLauncher.launch(intent)
                } else {
                    // Already default SMS app
                    markFirstRunComplete()
                    navigateToMainApp()
                }
            }
        } else {
            // Android 9 - Use Telephony API
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            requestDefaultSmsLauncher.launch(intent)
        }
    }

    private fun navigateToMainApp() {
        startActivity(Intent(this, SmsListActivity::class.java))
        finish()
    }
}

@Composable
fun FirstRunScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App icon/title
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Phalanx",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Privacy-First SMS Security",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy explainer features
        FeatureItem(
            icon = Icons.Default.Lock,
            title = "100% On-Device Analysis",
            description = "All security checks happen on your device. No data ever leaves your phone."
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureItem(
            icon = Icons.Default.PhoneAndroid,
            title = "Full Privacy Control",
            description = "Your messages, contacts, and analysis results stay completely private."
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureItem(
            icon = Icons.Default.Shield,
            title = "Real-Time Protection",
            description = "Detects phishing links, sender impersonation, and security threats instantly."
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureItem(
            icon = Icons.Default.Message,
            title = "Full SMS Functionality",
            description = "Send, receive, and manage messages with advanced security features built-in."
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Info card about default SMS app
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Why Default SMS App?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To protect you from threats in real-time, Phalanx needs to be your default SMS app. This allows us to analyze incoming messages instantly and alert you to potential dangers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Set as Default SMS App",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
