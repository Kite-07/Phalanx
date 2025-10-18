package com.example.phalanx

import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.phalanx.ui.theme.PhalanxTheme
import java.text.SimpleDateFormat
import java.util.*

class SmsDetailActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"
        val messages = readSmsMessages(sender)

        setContent {
            PhalanxTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = sender) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) {
                    SmsDetailScreen(messages = messages, modifier = Modifier.padding(it))
                }
            }
        }
    }

    private fun readSmsMessages(sender: String): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        val contentResolver = contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(sender),
            "date DESC"
        )

        cursor?.use {
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)

            if (indexBody != -1 && indexDate != -1) {
                while (it.moveToNext()) {
                    val body = it.getString(indexBody)
                    val timestamp = it.getLong(indexDate)
                    smsList.add(SmsMessage(sender, body, timestamp))
                }
            }
        }
        return smsList
    }
}

@Composable
fun SmsDetailScreen(messages: List<SmsMessage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(16.dp), reverseLayout = true) {
        items(messages) { message ->
            MessageBubble(message = message)
        }
    }
}

@Composable
fun MessageBubble(message: SmsMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Column {
                Text(text = message.body)
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
