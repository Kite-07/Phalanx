package com.kite.phalanx.domain.repository

import com.kite.phalanx.domain.model.PackVerificationResult
import com.kite.phalanx.domain.model.SenderPack
import com.kite.phalanx.domain.model.SenderPackEntry

/**
 * Repository interface for managing sender intelligence packs.
 *
 * Sender packs contain verified patterns for known senders (carriers, banks, government)
 * to detect SENDER_MISMATCH when message claims don't match actual sender ID.
 */
interface SenderPackRepository {

    /**
     * Loads the sender pack for a specific region.
     *
     * @param region Region code (e.g., "IN", "US", "GB")
     * @return PackVerificationResult containing the verified pack or error
     */
    suspend fun loadPack(region: String): PackVerificationResult

    /**
     * Gets the currently active sender pack (cached).
     *
     * @return Current sender pack or null if none loaded
     */
    fun getCurrentPack(): SenderPack?

    /**
     * Finds sender entries that match a given sender ID.
     *
     * @param senderId The sender ID from the message (e.g., "AX-AIRTEL", "HDFCBK")
     * @return List of matching sender pack entries
     */
    fun findMatchingSenders(senderId: String): List<SenderPackEntry>

    /**
     * Checks if a sender ID matches any known pattern in the current pack.
     *
     * @param senderId The sender ID to check
     * @return true if sender matches a known pattern
     */
    fun isKnownSender(senderId: String): Boolean

    /**
     * Updates the active pack with a new region.
     *
     * @param region New region code
     * @return PackVerificationResult for the new pack
     */
    suspend fun updateRegion(region: String): PackVerificationResult

    /**
     * Clears the cached pack (useful for testing or forcing reload).
     */
    fun clearCache()
}
