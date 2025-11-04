package com.kite.phalanx.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.phalanx.domain.model.Reason
import com.kite.phalanx.domain.model.SignalCode
import com.kite.phalanx.domain.model.Verdict
import com.kite.phalanx.domain.model.VerdictLevel

/**
 * Security chip shown under received messages.
 *
 * Per PRD Phase 2:
 * - Shows registered domain + color indicator
 * - GREEN: Safe (optional display)
 * - AMBER: Suspicious (yellow/orange)
 * - RED: Dangerous (red)
 * - Tappable to show "Explain" bottom sheet
 *
 * @param verdict The security verdict for this message
 * @param registeredDomain The registered domain to display
 * @param onClick Called when chip is tapped to show explanation
 */
@Composable
fun SecurityChip(
    verdict: Verdict,
    registeredDomain: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon, label) = when (verdict.level) {
        VerdictLevel.GREEN -> {
            // Green: Safe (subtle)
            Tuple4(
                Color(0xFF4CAF50).copy(alpha = 0.1f),
                Color(0xFF2E7D32),
                Icons.Default.CheckCircle,
                "Safe"
            )
        }
        VerdictLevel.AMBER -> {
            // Amber: Suspicious (yellow/orange)
            Tuple4(
                Color(0xFFFF9800).copy(alpha = 0.15f),
                Color(0xFFE65100),
                Icons.Default.Warning,
                "Be Careful"
            )
        }
        VerdictLevel.RED -> {
            // Red: Dangerous (strong warning)
            Tuple4(
                Color(0xFFF44336).copy(alpha = 0.15f),
                Color(0xFFC62828),
                Icons.Default.Warning,
                "Dangerous"
            )
        }
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (registeredDomain.isNotBlank()) {
                    Text(
                        text = registeredDomain,
                        color = contentColor.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Show explanation",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Bottom sheet explaining the security verdict.
 *
 * Per PRD Phase 2:
 * - Shows top 1-3 reasons for the verdict
 * - Each reason has: code, label, and detailed explanation
 * - Provides action buttons (Open Safely, Copy URL, etc.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityExplanationSheet(
    verdict: Verdict,
    registeredDomain: String,
    finalUrl: String?,
    senderInfo: String? = null,
    onDismiss: () -> Unit,
    onOpenSafely: (() -> Unit)? = null,
    onCopyUrl: (() -> Unit)? = null,
    onWhitelist: (() -> Unit)? = null,
    onBlockSender: (() -> Unit)? = null,
    onDeleteMessage: (() -> Unit)? = null,
    onReportFalsePositive: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with verdict level
            SecurityVerdictHeader(verdict = verdict, domain = registeredDomain)

            // Divider
            HorizontalDivider()

            // Reasons
            if (verdict.reasons.isNotEmpty()) {
                Text(
                    text = "Why this message was flagged:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                verdict.reasons.take(3).forEach { reason ->
                    SecurityReasonCard(reason = reason)
                }
            }

            // Final URL if available
            if (finalUrl != null && finalUrl != registeredDomain) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Link destination:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = finalUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Actions
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Primary actions row - Dangerous actions first
                if (onBlockSender != null || onDeleteMessage != null) {
                    Text(
                        text = "Protective Actions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Block Sender button
                        if (onBlockSender != null) {
                            OutlinedButton(
                                onClick = {
                                    onBlockSender()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Block Sender")
                            }
                        }

                        // Delete Message button
                        if (onDeleteMessage != null) {
                            OutlinedButton(
                                onClick = {
                                    onDeleteMessage()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }

                // Link actions
                if (onOpenSafely != null || onCopyUrl != null) {
                    Text(
                        text = "Link Actions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Open Safely button
                    if (onOpenSafely != null) {
                        OutlinedButton(
                            onClick = {
                                onOpenSafely()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open in External Browser")
                        }
                    }

                    // Copy URL button
                    if (onCopyUrl != null) {
                        OutlinedButton(
                            onClick = {
                                onCopyUrl()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copy URL")
                        }
                    }
                }

                // Trust/Report actions
                if (onWhitelist != null || onReportFalsePositive != null) {
                    Text(
                        text = "Other Actions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Whitelist button (only for AMBER, not RED)
                    if (onWhitelist != null && verdict.level != VerdictLevel.RED) {
                        TextButton(
                            onClick = {
                                onWhitelist()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Trust This Domain")
                        }
                    }

                    // Report False Positive button
                    if (onReportFalsePositive != null) {
                        TextButton(
                            onClick = {
                                onReportFalsePositive()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Report False Positive")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Header for the security explanation sheet.
 */
@Composable
private fun SecurityVerdictHeader(
    verdict: Verdict,
    domain: String
) {
    val (backgroundColor, contentColor, title, subtitle) = when (verdict.level) {
        VerdictLevel.GREEN -> Tuple4(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF2E7D32),
            "✓ This link appears safe",
            "No significant security concerns detected"
        )
        VerdictLevel.AMBER -> Tuple4(
            Color(0xFFFF9800).copy(alpha = 0.15f),
            Color(0xFFE65100),
            "⚠ Be cautious with this link",
            "Some suspicious characteristics detected"
        )
        VerdictLevel.RED -> Tuple4(
            Color(0xFFF44336).copy(alpha = 0.15f),
            Color(0xFFC62828),
            "⛔ This link is dangerous",
            "Strong indicators of phishing or fraud"
        )
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
            if (domain.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Domain: $domain",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }
            Text(
                text = "Risk Score: ${verdict.score}",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Card displaying a single security reason with icon and expandable details.
 */
@Composable
private fun SecurityReasonCard(reason: Reason) {
    var expanded by remember { mutableStateOf(false) }

    val (icon, severityColor) = getSignalIconAndColor(reason.code)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Signal icon with severity color
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(24.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = reason.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = reason.code.name.replace("_", " ").lowercase()
                            .split(" ")
                            .joinToString(" ") { it.capitalize() },
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor.copy(alpha = 0.8f)
                    )
                }

                // Expand/collapse indicator
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = reason.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }
        }
    }
}

/**
 * Maps signal codes to icons and severity colors.
 */
private fun getSignalIconAndColor(code: SignalCode): Pair<ImageVector, Color> {
    return when (code) {
        // Critical signals - Red
        SignalCode.SAFE_BROWSING_HIT -> Pair(Icons.Default.Dangerous, Color(0xFFC62828))
        SignalCode.URLHAUS_LISTED -> Pair(Icons.Default.Dangerous, Color(0xFFC62828))
        SignalCode.USERINFO_IN_URL -> Pair(Icons.Default.Lock, Color(0xFFC62828))

        // High risk signals - Orange
        SignalCode.HOMOGLYPH_SUSPECT -> Pair(Icons.Default.TextFields, Color(0xFFE65100))
        SignalCode.BRAND_IMPERSONATION -> Pair(Icons.Default.BusinessCenter, Color(0xFFE65100))
        SignalCode.PUNYCODE_DOMAIN -> Pair(Icons.Default.Language, Color(0xFFE65100))

        // Medium risk signals - Amber
        SignalCode.SHORTENER_EXPANDED -> Pair(Icons.Default.Link, Color(0xFFFF9800))
        SignalCode.EXCESSIVE_REDIRECTS -> Pair(Icons.Default.Shuffle, Color(0xFFFF9800))
        SignalCode.SHORTENER_TO_SUSPICIOUS -> Pair(Icons.Default.ArrowForward, Color(0xFFFF9800))
        SignalCode.HIGH_RISK_TLD -> Pair(Icons.Default.Public, Color(0xFFFF9800))

        // Low risk signals - Yellow
        SignalCode.RAW_IP_HOST -> Pair(Icons.Default.Computer, Color(0xFFFFB300))
        SignalCode.HTTP_SCHEME -> Pair(Icons.Default.Http, Color(0xFFFFB300))
        SignalCode.NON_STANDARD_PORT -> Pair(Icons.Default.SettingsEthernet, Color(0xFFFFB300))
        SignalCode.SUSPICIOUS_PATH -> Pair(Icons.Default.Folder, Color(0xFFFFB300))

        // Default
        else -> Pair(Icons.Default.Info, Color(0xFF757575))
    }
}

/**
 * Loading indicator shown while analyzing message.
 */
@Composable
fun SecurityAnalyzingIndicator(
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Analyzing for security threats...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Inline warning banner shown above suspicious messages.
 */
@Composable
fun InlineSecurityWarning(
    verdict: Verdict,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon, warningText) = when (verdict.level) {
        VerdictLevel.AMBER -> Tuple4(
            Color(0xFFFF9800).copy(alpha = 0.15f),
            Color(0xFFE65100),
            Icons.Default.Warning,
            "⚠ This message contains suspicious links"
        )
        VerdictLevel.RED -> Tuple4(
            Color(0xFFF44336).copy(alpha = 0.15f),
            Color(0xFFC62828),
            Icons.Default.Dangerous,
            "⛔ DANGER: This message likely contains phishing links"
        )
        else -> return // Don't show banner for GREEN
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Tap to see details",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Show details",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Helper data class for destructuring
private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
