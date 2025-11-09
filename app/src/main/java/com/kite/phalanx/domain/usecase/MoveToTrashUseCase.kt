package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.repository.TrashVaultRepository
import javax.inject.Inject

/**
 * Use case for moving a message to trash vault (soft delete).
 *
 * Phase 3 - Safety Rails: Trash Vault
 * Provides 30-day retention before permanent deletion.
 */
class MoveToTrashUseCase @Inject constructor(
    private val trashVaultRepository: TrashVaultRepository
) {

    /**
     * Move a message to trash vault.
     *
     * @param messageId Original SMS provider message ID
     * @param sender Phone number or short code
     * @param body Message content
     * @param timestamp Original message timestamp
     * @param threadId Original conversation thread ID
     * @param isMms True if MMS, false if SMS
     * @param subscriptionId SIM subscription ID
     * @param threadGroupId Optional group ID for messages deleted together (e.g., thread deletion)
     */
    suspend fun execute(
        messageId: Long,
        sender: String,
        body: String,
        timestamp: Long,
        threadId: Long = 0,
        isMms: Boolean = false,
        subscriptionId: Int = -1,
        threadGroupId: String? = null
    ) {
        trashVaultRepository.moveToTrash(
            messageId = messageId,
            sender = sender,
            body = body,
            timestamp = timestamp,
            threadId = threadId,
            isMms = isMms,
            subscriptionId = subscriptionId,
            threadGroupId = threadGroupId
        )
    }
}
