package com.kite.phalanx

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays a single MMS attachment (image, video, audio, etc.)
 */
@Composable
fun AttachmentView(
    attachment: MessageAttachment,
    modifier: Modifier = Modifier,
    maxWidth: Int = 250
) {
    val context = LocalContext.current

    when {
        attachment.isImage -> ImageAttachmentView(
            attachment = attachment,
            context = context,
            modifier = modifier,
            maxWidth = maxWidth
        )
        attachment.isVideo -> VideoAttachmentView(
            attachment = attachment,
            context = context,
            modifier = modifier
        )
        attachment.isAudio -> AudioAttachmentView(
            attachment = attachment,
            context = context,
            modifier = modifier
        )
        else -> GenericAttachmentView(
            attachment = attachment,
            context = context,
            modifier = modifier
        )
    }
}

/**
 * Displays an image attachment
 */
@Composable
fun ImageAttachmentView(
    attachment: MessageAttachment,
    context: Context,
    modifier: Modifier = Modifier,
    maxWidth: Int = 250
) {
    val imageBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, attachment) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(attachment.uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = maxWidth.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                openAttachment(context, attachment)
            }
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = attachment.fileName ?: "Image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Loading placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Displays a video attachment
 */
@Composable
fun VideoAttachmentView(
    attachment: MessageAttachment,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable {
                openAttachment(context, attachment)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Video",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName ?: "Video",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(attachment.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Displays an audio attachment
 */
@Composable
fun AudioAttachmentView(
    attachment: MessageAttachment,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable {
                openAttachment(context, attachment)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add, // Note: Should be music note icon
                contentDescription = "Audio",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName ?: "Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(attachment.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Displays a generic attachment (unknown type)
 */
@Composable
fun GenericAttachmentView(
    attachment: MessageAttachment,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable {
                openAttachment(context, attachment)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place, // Note: Should be attachment icon
                contentDescription = "Attachment",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName ?: "Attachment",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(attachment.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Opens an attachment in the appropriate app
 */
private fun openAttachment(context: Context, attachment: MessageAttachment) {
    try {
        // For API 24+, we need to use FileProvider
        // First, save attachment to cache
        val file = MmsHelper.saveAttachmentToCache(context, attachment)
        if (file != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.contentType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open with"))
        }
    } catch (e: Exception) {
        // Fallback: try opening directly
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(attachment.uri, attachment.contentType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            // Could show error toast here
        }
    }
}

/**
 * Format file size in human-readable format
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * Displays a list of attachments
 */
@Composable
fun AttachmentList(
    attachments: List<MessageAttachment>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentView(attachment = attachment)
        }
    }
}
