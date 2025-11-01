package com.kite.phalanx

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Data class representing a selected attachment to be sent
 */
data class SelectedAttachment(
    val uri: Uri,
    val mimeType: String,
    val fileName: String? = null
)

/**
 * Attachment button that shows picker options
 */
@Composable
fun AttachmentButton(
    onAttachmentSelected: (SelectedAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    // Gallery picker (uses Photo Picker on Android 13+ or legacy picker on older versions)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = getMimeType(uri) ?: "image/*"
            onAttachmentSelected(
                SelectedAttachment(
                    uri = uri,
                    mimeType = mimeType,
                    fileName = getFileName(uri)
                )
            )
        }
    }

    // Camera launcher
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraCaptureUri != null) {
            onAttachmentSelected(
                SelectedAttachment(
                    uri = cameraCaptureUri!!,
                    mimeType = "image/jpeg",
                    fileName = "camera_${System.currentTimeMillis()}.jpg"
                )
            )
        }
    }

    IconButton(
        onClick = { showPicker = true },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Attach",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (showPicker) {
        AttachmentPickerDialog(
            onDismiss = { showPicker = false },
            onGalleryClick = {
                showPicker = false
                galleryLauncher.launch("*/*") // Accept all file types
            },
            onCameraClick = {
                showPicker = false
                // Create a temporary file URI for camera capture
                // This would need context to create the file
                // For now, just launch gallery as fallback
                galleryLauncher.launch("image/*")
            }
        )
    }
}

/**
 * Dialog showing attachment picker options
 */
@Composable
fun AttachmentPickerDialog(
    onDismiss: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add attachment") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGalleryClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add, // Should be gallery icon
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Choose from gallery",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCameraClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add, // Should be camera icon
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Take photo",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Preview of selected attachment with remove button
 */
@Composable
fun AttachmentPreview(
    attachment: SelectedAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Attachment icon based on type
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            attachment.mimeType.startsWith("image/") -> "📷"
                            attachment.mimeType.startsWith("video/") -> "🎥"
                            attachment.mimeType.startsWith("audio/") -> "🎵"
                            else -> "📎"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Filename
            Text(
                text = attachment.fileName ?: "Attachment",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Helper to get MIME type from URI
 */
private fun getMimeType(uri: Uri): String? {
    return when {
        uri.toString().endsWith(".jpg", ignoreCase = true) ||
        uri.toString().endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        uri.toString().endsWith(".png", ignoreCase = true) -> "image/png"
        uri.toString().endsWith(".gif", ignoreCase = true) -> "image/gif"
        uri.toString().endsWith(".mp4", ignoreCase = true) -> "video/mp4"
        uri.toString().endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        else -> null
    }
}

/**
 * Helper to extract filename from URI
 */
private fun getFileName(uri: Uri): String? {
    return uri.lastPathSegment?.substringAfterLast('/')
}
