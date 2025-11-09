package com.kite.phalanx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kite.phalanx.ui.theme.PhalanxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactDetailActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val contactName = intent.getStringExtra("contact_name")

        setContent {
            PhalanxTheme {
                var contactPhotoUri by remember { mutableStateOf<Uri?>(null) }
                var displayName by remember { mutableStateOf(contactName ?: phoneNumber) }
                var contactExists by remember { mutableStateOf(false) }

                // Load contact details
                LaunchedEffect(phoneNumber) {
                    val details = loadContactDetails(phoneNumber)
                    contactExists = details != null
                    details?.let {
                        contactPhotoUri = it.photoUri
                        if (contactName == null && it.name != null) {
                            displayName = it.name
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Contact Details") },
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
                    ContactDetailScreen(
                        phoneNumber = phoneNumber,
                        displayName = displayName,
                        contactPhotoUri = contactPhotoUri,
                        contactExists = contactExists,
                        onPhoneCallClick = {
                            makePhoneCall(phoneNumber)
                        },
                        onVideoCallClick = {
                            makeVideoCall(phoneNumber)
                        },
                        onContactInfoClick = {
                            openContactInfo(phoneNumber)
                        },
                        onAddContactClick = {
                            addToContacts(phoneNumber, displayName)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private suspend fun loadContactDetails(phoneNumber: String): ContactDetails? = withContext(Dispatchers.IO) {
        // Check if we have contact permission
        if (ContextCompat.checkSelfPermission(
                this@ContactDetailActivity,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }

        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    val photoUriString = it.getString(1)
                    val photoUri = photoUriString?.let { uri -> Uri.parse(uri) }
                    return@withContext ContactDetails(name, photoUri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext null
    }

    private fun makePhoneCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeVideoCall(phoneNumber: String) {
        try {
            // Try to open video call using various common video calling apps
            // First, try the generic video call action
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("tel:$phoneNumber")
                putExtra("android.phone.extra.VIDEO_CALL", true)
            }

            // Check if there's an app that can handle video calls
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback: Just open regular dialer
                makePhoneCall(phoneNumber)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to regular phone call
            makePhoneCall(phoneNumber)
        }
    }

    private fun openContactInfo(phoneNumber: String) {
        try {
            // First, try to get the contact ID
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            var contactUri: Uri? = null

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val cursor = contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val contactId = it.getLong(0)
                        contactUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_URI,
                            contactId.toString()
                        )
                    }
                }
            }

            // Open the contact card
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (contactUri != null) {
                    // If we found the contact, open their full profile
                    data = contactUri
                } else {
                    // Otherwise, open the contacts app with option to add this number
                    data = Uri.parse("tel:$phoneNumber")
                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                }
            }

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addToContacts(phoneNumber: String, displayName: String) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE

                // Pre-fill phone number
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)

                // If displayName is different from phoneNumber, use it as the name
                if (displayName != phoneNumber) {
                    putExtra(ContactsContract.Intents.Insert.NAME, displayName)
                }
            }

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class ContactDetails(
        val name: String?,
        val photoUri: Uri?
    )
}

@Composable
private fun ContactDetailScreen(
    phoneNumber: String,
    displayName: String,
    contactPhotoUri: Uri?,
    contactExists: Boolean,
    onPhoneCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onContactInfoClick: () -> Unit,
    onAddContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Contact Photo
        ContactDetailPhoto(
            photoUri = contactPhotoUri,
            displayName = displayName,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Contact Name
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Phone Number (if different from display name)
        if (displayName != phoneNumber) {
            Text(
                text = phoneNumber,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Phone Call Button
            ContactActionButton(
                icon = Icons.Default.Call,
                label = "Call",
                onClick = onPhoneCallClick
            )

            // Video Call Button
            ContactActionButton(
                icon = Icons.Default.VideoCall,
                label = "Video",
                onClick = onVideoCallClick
            )

            // Contact Info or Add to Contacts Button
            if (contactExists) {
                ContactActionButton(
                    icon = Icons.Default.Info,
                    label = "Info",
                    onClick = onContactInfoClick
                )
            } else {
                ContactActionButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Add",
                    onClick = onAddContactClick
                )
            }
        }
    }
}

@Composable
private fun ContactDetailPhoto(
    photoUri: Uri?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Load contact photo asynchronously
    val photoBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, photoUri) {
        value = withContext(Dispatchers.IO) {
            photoUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }.value

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap,
                contentDescription = "Contact photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Show default person icon if no photo
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Default contact icon",
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ContactActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
