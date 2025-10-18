package com.example.phalanx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phalanx.ui.theme.PhalanxTheme
import java.text.SimpleDateFormat
import java.util.*

class SmsListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhalanxTheme {
                var smsList by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
                val context = LocalContext.current

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        // Permission granted, read SMS
                        smsList = readSmsMessages()
                    } else {
                        // Handle permission denial
                    }
                }

                LaunchedEffect(key1 = true) {
                    when (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_SMS
                    )) {
                        PackageManager.PERMISSION_GRANTED -> {
                            smsList = readSmsMessages()
                        }
                        else -> {
                            launcher.launch(Manifest.permission.READ_SMS)
                        }
                    }
                }

                SmsListScreen(smsList = smsList)
            }
        }
    }

    private fun readSmsMessages(): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        val contentResolver = contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)

            if (indexAddress != -1 && indexBody != -1 && indexDate != -1) {
                while (it.moveToNext()) {
                    val sender = it.getString(indexAddress)
                    val body = it.getString(indexBody)
                    val timestamp = it.getLong(indexDate)
                    smsList.add(SmsMessage(sender, body, timestamp))
                }
            }
        }
        return smsList.groupBy { it.sender }.map { it.value.first() }
    }
}

@Composable
fun SmsListScreen(smsList: List<SmsMessage>) {
    val context = LocalContext.current
    LazyColumn {
        items(smsList) { sms ->
            SmsCard(sms = sms, onClick = {
                val intent = Intent(context, SmsDetailActivity::class.java)
                intent.putExtra("sender", sms.sender)
                context.startActivity(intent)
            })
        }
    }
}

@Composable
fun SmsCard(sms: SmsMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Sender Image",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = sms.sender, style = MaterialTheme.typography.bodyLarge)
                    Text(text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sms.timestamp)), style = MaterialTheme.typography.bodySmall)
                }
                Text(text = sms.body, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
