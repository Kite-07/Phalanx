package com.kite.phalanx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

data class Contact(
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri? = null
)

class ContactPickerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhalanxTheme {
                var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
                var searchQuery by remember { mutableStateOf("") }
                val context = LocalContext.current

                // Load contacts when we have permission
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        contacts = loadContacts()
                    }
                }

                val filteredContacts = remember(contacts, searchQuery) {
                    if (searchQuery.isBlank()) {
                        contacts
                    } else {
                        contacts.filter { contact ->
                            contact.name.contains(searchQuery, ignoreCase = true) ||
                                    contact.phoneNumber.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                // Check if search query is a potential phone number (not matching any contact)
                val isPhoneNumber = searchQuery.isNotBlank() &&
                    searchQuery.any { it.isDigit() } &&
                    filteredContacts.isEmpty()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("New Message") },
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            placeholder = { Text("Search contacts or enter phone number") },
                            singleLine = true
                        )

                        // Show "Send message to" button if query looks like a phone number
                        if (isPhoneNumber) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        openSmsDetail(searchQuery.trim())
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Send message to ${searchQuery.trim()}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        // Contact list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = filteredContacts,
                                key = { it.phoneNumber }
                            ) { contact ->
                                ContactItem(
                                    contact = contact,
                                    onClick = {
                                        openSmsDetail(contact.phoneNumber)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openSmsDetail(phoneNumber: String) {
        val intent = Intent(this, SmsDetailActivity::class.java)
        intent.putExtra("sender", phoneNumber)

        // Forward message if provided
        val forwardMessage = getIntent().getStringExtra("forward_message")
        if (forwardMessage != null) {
            intent.putExtra("prefill_message", forwardMessage)
        }

        startActivity(intent)
        finish()
    }

    private suspend fun loadContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<Contact>()

        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: continue
                    val number = it.getString(numberIndex) ?: continue
                    val photoUriString = it.getString(photoIndex)
                    val photoUri = photoUriString?.let { uri -> Uri.parse(uri) }

                    contactsList.add(
                        Contact(
                            name = name,
                            phoneNumber = number,
                            photoUri = photoUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext contactsList
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactPhoto(
                photoUri = contact.photoUri,
                contentDescription = "Photo of ${contact.name}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactPhoto(
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
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

private suspend fun loadContactPhotoBitmap(
    context: android.content.Context,
    photoUri: Uri?
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (photoUri == null) return@withContext null

    try {
        context.contentResolver.openInputStream(photoUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { bitmap ->
                bitmap.asImageBitmap()
            }
        }
    } catch (e: Exception) {
        null
    }
}
