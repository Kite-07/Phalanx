package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for allow/block list rules.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists
 * Supports domain/sender/pattern rules with precedence management.
 */
@Dao
interface AllowBlockRuleDao {

    /**
     * Insert a new rule.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AllowBlockRuleEntity): Long

    /**
     * Update an existing rule.
     */
    @Update
    suspend fun update(rule: AllowBlockRuleEntity)

    /**
     * Get all rules ordered by priority (highest first).
     */
    @Query("SELECT * FROM allow_block_rules ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(): Flow<List<AllowBlockRuleEntity>>

    /**
     * Get all rules (suspend version for one-time queries).
     */
    @Query("SELECT * FROM allow_block_rules ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllRulesList(): List<AllowBlockRuleEntity>

    /**
     * Get rules by type.
     */
    @Query("SELECT * FROM allow_block_rules WHERE type = :type ORDER BY priority DESC")
    suspend fun getRulesByType(type: String): List<AllowBlockRuleEntity>

    /**
     * Get rules by type and action.
     */
    @Query("SELECT * FROM allow_block_rules WHERE type = :type AND action = :action ORDER BY priority DESC")
    suspend fun getRulesByTypeAndAction(type: String, action: String): List<AllowBlockRuleEntity>

    /**
     * Find exact matching rule by type and value.
     */
    @Query("SELECT * FROM allow_block_rules WHERE type = :type AND value = :value LIMIT 1")
    suspend fun findRule(type: String, value: String): AllowBlockRuleEntity?

    /**
     * Get all domain ALLOW rules (for whitelisting).
     */
    @Query("SELECT * FROM allow_block_rules WHERE type = 'DOMAIN' AND action = 'ALLOW' ORDER BY priority DESC")
    suspend fun getAllowedDomains(): List<AllowBlockRuleEntity>

    /**
     * Get all sender BLOCK rules (for blocklisting).
     */
    @Query("SELECT * FROM allow_block_rules WHERE type = 'SENDER' AND action = 'BLOCK' ORDER BY priority DESC")
    suspend fun getBlockedSenders(): List<AllowBlockRuleEntity>

    /**
     * Get a specific rule by ID.
     */
    @Query("SELECT * FROM allow_block_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRule(ruleId: Long): AllowBlockRuleEntity?

    /**
     * Delete a specific rule.
     */
    @Query("DELETE FROM allow_block_rules WHERE id = :ruleId")
    suspend fun delete(ruleId: Long)

    /**
     * Delete a rule by type and value.
     */
    @Query("DELETE FROM allow_block_rules WHERE type = :type AND value = :value")
    suspend fun deleteByTypeAndValue(type: String, value: String)

    /**
     * Delete all rules.
     */
    @Query("DELETE FROM allow_block_rules")
    suspend fun deleteAll()

    /**
     * Count total rules.
     */
    @Query("SELECT COUNT(*) FROM allow_block_rules")
    suspend fun getCount(): Int

    /**
     * Count rules by type.
     */
    @Query("SELECT COUNT(*) FROM allow_block_rules WHERE type = :type")
    suspend fun getCountByType(type: String): Int
}
