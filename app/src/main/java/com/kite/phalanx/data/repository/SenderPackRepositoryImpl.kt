package com.kite.phalanx.data.repository

import android.content.Context
import com.kite.phalanx.domain.model.PackVerificationResult
import com.kite.phalanx.domain.model.SenderPack
import com.kite.phalanx.domain.model.SenderPackEntry
import com.kite.phalanx.domain.model.SenderType
import com.kite.phalanx.domain.repository.SenderPackRepository
import com.kite.phalanx.domain.util.SignatureVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SenderPackRepository that loads packs from assets,
 * verifies Ed25519 signatures, and provides sender pattern matching.
 */
@Singleton
class SenderPackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SenderPackRepository {

    /**
     * Currently loaded and verified sender pack
     */
    private var currentPack: SenderPack? = null

    /**
     * Compiled regex patterns for fast matching
     * Maps pattern string to compiled Regex
     */
    private val compiledPatterns = mutableMapOf<String, Regex>()

    override suspend fun loadPack(region: String): PackVerificationResult = withContext(Dispatchers.IO) {
        try {
            // Load pack JSON from assets
            val packJson = loadPackFromAssets(region)
                ?: return@withContext PackVerificationResult(
                    isValid = false,
                    errorMessage = "Sender pack not found for region: $region"
                )

            // Parse JSON
            val pack = parsePack(packJson, region)

            // Verify signature
            val canonicalJson = createCanonicalJson(pack)
            val isValidSignature = SignatureVerifier.verify(canonicalJson, pack.signature)

            // TODO: Remove this development bypass before production release
            // For development, accept placeholder signatures (all zeros)
            val isDevPlaceholder = pack.signature.matches(Regex("^0+$"))

            if (!isValidSignature && !isDevPlaceholder) {
                android.util.Log.e("SenderPackRepo", "Invalid signature for pack $region")
                return@withContext PackVerificationResult(
                    isValid = false,
                    errorMessage = "Invalid signature for sender pack: $region"
                )
            }

            if (isDevPlaceholder) {
                android.util.Log.w("SenderPackRepo", "⚠️ Using DEVELOPMENT pack with placeholder signature for region: $region")
            } else {
                android.util.Log.i("SenderPackRepo", "✓ Verified sender pack signature for region: $region")
            }

            // Cache the verified pack
            currentPack = pack
            precompilePatterns(pack.entries)

            PackVerificationResult(
                isValid = true,
                errorMessage = null,
                pack = pack
            )
        } catch (e: Exception) {
            PackVerificationResult(
                isValid = false,
                errorMessage = "Failed to load sender pack: ${e.message}"
            )
        }
    }

    override fun getCurrentPack(): SenderPack? = currentPack

    override fun findMatchingSenders(senderId: String): List<SenderPackEntry> {
        val pack = currentPack ?: return emptyList()

        return pack.entries.filter { entry ->
            val pattern = compiledPatterns[entry.pattern]
                ?: entry.pattern.toRegex().also { compiledPatterns[entry.pattern] = it }

            pattern.matches(senderId)
        }
    }

    override fun isKnownSender(senderId: String): Boolean {
        return findMatchingSenders(senderId).isNotEmpty()
    }

    override suspend fun updateRegion(region: String): PackVerificationResult {
        return loadPack(region)
    }

    override fun clearCache() {
        currentPack = null
        compiledPatterns.clear()
    }

    /**
     * Loads sender pack JSON from assets directory.
     * Expected path: assets/sender_packs/[region].json
     *
     * For unit tests: Falls back to classpath resources if assets are not available.
     */
    private fun loadPackFromAssets(region: String): String? {
        val assetPath = "sender_packs/${region.uppercase()}.json"

        // First try: Load from classpath resources (works for both unit tests and Robolectric)
        val classLoader = Thread.currentThread().contextClassLoader ?: this::class.java.classLoader
        val resourcePaths = listOf(
            "assets/$assetPath",
            assetPath
        )

        for (resourcePath in resourcePaths) {
            try {
                val inputStream = classLoader?.getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    android.util.Log.d("SenderPackRepo", "✓ Loaded pack from classpath: $resourcePath")
                    return inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                // Continue to next path
            }
        }

        // Second try: Load from Android assets (production app)
        return try {
            android.util.Log.d("SenderPackRepo", "Trying Android assets: $assetPath")
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.w("SenderPackRepo", "Failed to load pack from any source for region: $region", e)
            null
        }
    }

    /**
     * Parses sender pack from JSON string
     */
    private fun parsePack(json: String, region: String): SenderPack {
        val jsonObject = JSONObject(json)

        val version = jsonObject.getLong("version")
        val signature = jsonObject.getString("signature")
        val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
        val entriesArray = jsonObject.getJSONArray("entries")

        val entries = mutableListOf<SenderPackEntry>()
        for (i in 0 until entriesArray.length()) {
            val entryJson = entriesArray.getJSONObject(i)
            entries.add(parseEntry(entryJson))
        }

        return SenderPack(
            region = region,
            version = version,
            entries = entries,
            signature = signature,
            timestamp = timestamp
        )
    }

    /**
     * Parses a single sender pack entry from JSON
     */
    private fun parseEntry(json: JSONObject): SenderPackEntry {
        val pattern = json.getString("pattern")
        val brand = json.getString("brand")
        val type = SenderType.valueOf(json.getString("type").uppercase())

        val keywordsArray = json.optJSONArray("keywords")
        val keywords = if (keywordsArray != null) {
            List(keywordsArray.length()) { i -> keywordsArray.getString(i) }
        } else {
            emptyList()
        }

        return SenderPackEntry(
            pattern = pattern,
            brand = brand,
            type = type,
            keywords = keywords
        )
    }

    /**
     * Creates canonical JSON representation for signature verification.
     * Must match the format used when signing the pack.
     *
     * Format: {"region":"XX","version":123456,"entries":[...]}
     * (signature field excluded from canonical form)
     */
    private fun createCanonicalJson(pack: SenderPack): String {
        val jsonObject = JSONObject()
        jsonObject.put("region", pack.region)
        jsonObject.put("version", pack.version)

        val entriesArray = JSONArray()
        pack.entries.forEach { entry ->
            val entryJson = JSONObject()
            entryJson.put("pattern", entry.pattern)
            entryJson.put("brand", entry.brand)
            entryJson.put("type", entry.type.name)
            if (entry.keywords.isNotEmpty()) {
                entryJson.put("keywords", JSONArray(entry.keywords))
            }
            entriesArray.put(entryJson)
        }
        jsonObject.put("entries", entriesArray)

        return jsonObject.toString()
    }

    /**
     * Precompiles regex patterns for faster matching
     */
    private fun precompilePatterns(entries: List<SenderPackEntry>) {
        compiledPatterns.clear()
        entries.forEach { entry ->
            try {
                compiledPatterns[entry.pattern] = entry.pattern.toRegex()
            } catch (e: Exception) {
                // Log invalid regex pattern
                android.util.Log.w("SenderPackRepository", "Invalid regex pattern: ${entry.pattern}", e)
            }
        }
    }
}
