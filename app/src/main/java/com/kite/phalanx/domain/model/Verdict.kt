package com.kite.phalanx.domain.model

/**
 * Final security verdict for a message.
 *
 * Per PRD: Verdicts are color-coded risk levels with explainable reasons.
 * - GREEN: Safe, no action needed
 * - AMBER: Suspicious, user should be cautious
 * - RED: Dangerous, strong warning needed
 *
 * @property messageId The message this verdict applies to
 * @property level Risk level (GREEN/AMBER/RED)
 * @property score Total risk score (sum of signal weights)
 * @property reasons List of reasons explaining the verdict (top 1-3 for UI)
 * @property timestamp When this verdict was generated
 */
data class Verdict(
    val messageId: String,
    val level: VerdictLevel,
    val score: Int,
    val reasons: List<Reason>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Risk level for a verdict.
 *
 * Per PRD:
 * - GREEN: Safe, no notification
 * - AMBER: Suspicious, notify user
 * - RED: Dangerous, strong warning
 */
enum class VerdictLevel {
    GREEN,
    AMBER,
    RED
}

/**
 * A human-readable reason explaining part of the verdict.
 *
 * Per PRD: Reasons are structured for explainability.
 * Shown in "Explain" bottom sheet (top 1-3 reasons).
 *
 * @property code Signal code that triggered this reason
 * @property label Short user-facing label
 * @property details Longer explanation with specifics
 */
data class Reason(
    val code: SignalCode,
    val label: String,
    val details: String
)
