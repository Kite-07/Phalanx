package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kite.phalanx.domain.model.Reason
import com.kite.phalanx.domain.model.Verdict
import com.kite.phalanx.domain.model.VerdictLevel
import com.kite.phalanx.domain.model.SignalCode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room entity for storing verdict results for messages.
 */
@Entity(tableName = "verdicts")
data class VerdictEntity(
    @PrimaryKey
    val messageId: Long,
    val level: String, // "GREEN", "AMBER", or "RED"
    val score: Int,
    val reasons: String, // JSON-encoded list of Reason objects
    val timestamp: Long
)

/**
 * Convert domain model Verdict to Room entity.
 */
fun Verdict.toEntity(messageId: Long): VerdictEntity {
    // Convert reasons list to JSON
    val reasonsJson = JSONArray()
    reasons.forEach { reason ->
        val reasonObj = JSONObject().apply {
            put("code", reason.code.name)
            put("label", reason.label)
            put("details", reason.details)
        }
        reasonsJson.put(reasonObj)
    }

    return VerdictEntity(
        messageId = messageId,
        level = level.name,
        score = score,
        reasons = reasonsJson.toString(),
        timestamp = System.currentTimeMillis()
    )
}

/**
 * Convert Room entity back to domain model Verdict.
 */
fun VerdictEntity.toDomainModel(): Verdict {
    // Parse reasons JSON back to list
    val reasonsList = mutableListOf<Reason>()
    try {
        val reasonsJson = JSONArray(reasons)
        for (i in 0 until reasonsJson.length()) {
            val reasonObj = reasonsJson.getJSONObject(i)
            reasonsList.add(
                Reason(
                    code = SignalCode.valueOf(reasonObj.getString("code")),
                    label = reasonObj.getString("label"),
                    details = reasonObj.getString("details")
                )
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("VerdictEntity", "Failed to parse reasons JSON", e)
    }

    return Verdict(
        messageId = messageId.toString(),
        level = VerdictLevel.valueOf(level),
        score = score,
        reasons = reasonsList,
        timestamp = timestamp
    )
}
