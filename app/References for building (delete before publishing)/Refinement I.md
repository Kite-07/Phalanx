# Refinement I

**Document Purpose**: This document lists all incomplete implementations, missing tests, and code quality improvements needed to make Phalanx production-ready. Each item includes context (why it matters) and guidance (how to fix it).

**Last Updated**: 2025-01-11
**Codebase Status**: Phases 0-7 functionally complete, but several placeholders and gaps remain

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Critical Issues (Must Fix Before Production)](#critical-issues-must-fix-before-production)
3. [Placeholder Implementations](#placeholder-implementations)
4. [Missing Unit Tests](#missing-unit-tests)
5. [Code Quality Improvements](#code-quality-improvements)
6. [Performance & Monitoring](#performance--monitoring)
7. [Priority Matrix](#priority-matrix)

---

## Executive Summary

### What's Working Well
- **Phases 0-6**: Core messaging, security analysis, UI, safety rails, sender intelligence, and language detection are fully implemented and functional
- **Phase 7 Infrastructure**: Worker scheduling, database cleanup, trash purge, and cold-start optimization are complete
- **Test Coverage**: 46% overall (16/35 components tested), with strong coverage for Phase 1 security pipeline

### What Needs Work
- **1 Critical Security Issue**: Development signature bypass still active in production code
- **2 Placeholder Workers**: Update mechanisms for sender packs and PSL need real implementation
- **19 Missing Test Suites**: Phase 3, 5, and 7 components lack tests
- **2 Minor Features**: Favicon fetching and reply scroll/focus

### What's Been Fixed
- ✅ **Proto DataStore Migration**: Security settings successfully migrated from SharedPreferences (tested and working)
- ✅ **Firebase Crashlytics**: Integrated and configured for production crash monitoring
- ✅ **Timber Logging Framework**: Migrated 132 Log calls across 18 files, debug logs auto-stripped from production

**Estimated Effort**: ~1 week for critical items, ~2-3 weeks for complete refinement

---

## Critical Issues (Must Fix Before Production)

### 1. Remove Development Signature Bypass ⚠️ **SECURITY CRITICAL**

**File**: `SenderPackRepositoryImpl.kt` (lines 54-56)

**Problem**:
```kotlin
// Line 54: TODO: Remove this development bypass before production release
val signature = json.getString("signature")
val isDevPlaceholder = pack.signature.matches(Regex("^0+$"))
if (signature == pack.signature || isDevPlaceholder) {
    // Accepts any signature that's all zeros
}
```

Currently accepts sender packs with placeholder signatures (128 zeros). This was added to allow development/testing without valid Ed25519 signatures. In production, attackers could modify sender packs and sign them with fake signatures.

**Why It Matters**:
Sender packs tell the app which SMS sender IDs are legitimate (banks, carriers, government). If an attacker can inject a fake pack, they could whitelist their own phishing numbers or blacklist legitimate services.

**How to Fix**:
1. Open `SenderPackRepositoryImpl.kt`
2. Find line 56: `val isDevPlaceholder = pack.signature.matches(Regex("^0+$"))`
3. Remove the `|| isDevPlaceholder` condition from line 57
4. Test with real signed packs (you already have production signatures in `assets/sender_packs/`)
5. Verify signature verification fails for tampered packs

**Test Before Releasing**:
- Valid signature → pack loads ✓
- Invalid signature → pack rejected ✓
- Tampered JSON → signature mismatch ✓

**Priority**: 🔴 CRITICAL - Must fix before any production deployment

---

### 2. Migrate Security Settings to Proto DataStore ✅ **COMPLETED**

**Status**: ✅ **Migration completed successfully**

**What Was Done**:
1. ✅ Created Proto schema (`app/src/main/proto/security_settings.proto`)
2. ✅ Created `SecuritySettingsSerializer.kt` for Proto DataStore serialization
3. ✅ Updated `build.gradle.kts` and `libs.versions.toml` with Proto DataStore dependencies
4. ✅ Replaced all SharedPreferences code in `SecuritySettingsViewModel` with Proto DataStore
5. ✅ Implemented automatic migration from SharedPreferences to Proto DataStore
6. ✅ Tested migration - settings preserved correctly after upgrade

**Implementation Details**:
- Proto schema includes all settings: sensitivity, OTP passthrough, sender pack region, TFLite classifier, per-SIM settings
- Migration runs automatically on first launch after upgrade
- All existing user settings are preserved during migration
- DataStore instance created with `DataStoreFactory.create()` instead of extension function for better control
- Settings exposed as reactive `StateFlow<T>` using `Flow.stateIn()`

**Files Modified**:
- `app/src/main/proto/security_settings.proto` (new)
- `app/src/main/java/com/kite/phalanx/SecuritySettingsSerializer.kt` (new)
- `app/src/main/java/com/kite/phalanx/ui/SecuritySettingsActivity.kt` (migrated)
- `app/build.gradle.kts` (added protobuf plugin and dependencies)
- `gradle/libs.versions.toml` (added Proto DataStore versions)

**Migration Behavior**:
- Old SharedPreferences file remains on device (acts as backup, no harm)
- Migration only runs once per user
- All settings types migrated: Int → Enum, Boolean, String, Map<Int, Boolean>

**Priority**: ~~🟡 HIGH~~ → ✅ **DONE**

---

### 3. Add Firebase Crashlytics ✅ **COMPLETED**

**Status**: ✅ **Integration completed successfully**

**What Was Done**:
1. ✅ Added Firebase BOM and Crashlytics dependencies to `libs.versions.toml` and `build.gradle.kts`
2. ✅ Added google-services and firebase-crashlytics plugins
3. ✅ Placed `google-services.json` in `app/` directory
4. ✅ Initialized Crashlytics in `PhalanxApplication.kt`
5. ✅ Added crash reporting to critical catch blocks (migrations, sender pack loading, message analysis)
6. ✅ Configured to disable in debug builds, auto-enable in release builds

**Implementation Details**:
- Firebase BOM 33.7.0 ensures version compatibility
- Crashlytics automatically disabled in debug builds (via `!BuildConfig.DEBUG`)
- Critical error paths now report to Firebase:
  - Migration failures (`PhalanxApplication.kt`)
  - Sender pack loading failures (`PhalanxApplication.kt`)
  - Message analysis pipeline failures (`SmsDetailViewModel.kt`)
- Non-critical failures (URL expansion timeouts) intentionally excluded

**Files Modified**:
- `gradle/libs.versions.toml` (added Firebase versions and libraries)
- `app/build.gradle.kts` (added plugins and dependencies)
- `app/google-services.json` (new - from Firebase Console)
- `app/src/main/java/com/kite/phalanx/PhalanxApplication.kt` (initialization and crash reporting)
- `app/src/main/java/com/kite/phalanx/ui/SmsDetailViewModel.kt` (crash reporting)

**Privacy & Security**:
- Crashlytics disabled in debug builds to prevent developer PII from being reported
- Only exceptions are reported, not message content or user data
- Crash reports automatically include: stack traces, device info, Android version, app version

**Monitoring**:
- View crashes: [Firebase Console](https://console.firebase.google.com/) → Crashlytics
- Real-time crash alerts via Firebase email notifications
- Automatic crash grouping and stack trace deobfuscation

**Priority**: ~~🟡 HIGH~~ → ✅ **DONE**

---

## Placeholder Implementations

These workers are scheduled and will run, but they don't actually do anything yet. They just log "would do X" and return success.

### 4. Implement Sender Pack Update Worker

**File**: `SenderPackUpdateWorker.kt`

**Current Status**: Placeholder that logs "Sender pack update completed (placeholder - no actual update performed)"

**What It Does**:
Scheduled to run weekly (on Wi-Fi only) to download updated sender intelligence packs from a CDN. This keeps the app's knowledge of legitimate senders up-to-date as banks/carriers change their SMS sender IDs.

**What Needs Implementation**:

#### A. Set Up Backend Infrastructure
- Choose CDN provider (Cloudflare, AWS CloudFront, Firebase Storage, etc.)
- Upload sender pack JSONs with versioning: `packs/IN/v2.json`, `packs/US/v2.json`
- Create version manifest: `packs/manifest.json` with latest versions per region
- Set CORS headers to allow app to fetch JSONs

#### B. Implement Version Checking (line 66-71)
```kotlin
// Currently commented out - needs implementation
suspend fun checkForUpdates(region: String): Int? {
    val manifest = downloadManifest() // GET https://cdn.yourapp.com/packs/manifest.json
    return manifest.versions[region] // Returns latest version number
}
```

**Why**: Avoids re-downloading if pack is already up-to-date

#### C. Implement Pack Download (line 111-116)
```kotlin
private suspend fun downloadPack(region: String): String {
    val url = "https://cdn.yourapp.com/packs/$region/${remoteVersion}.json"
    val request = Request.Builder().url(url).build()

    return withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }
}
```

**Why**: Actually fetches the new pack from your CDN

#### D. Implement Signature Verification (line 126-130)
```kotlin
private fun verifySignature(packJson: String, region: String): Boolean {
    val pack = Json.decodeFromString<SenderPack>(packJson)

    // Extract data to verify (everything except signature field)
    val dataToVerify = packJson.substringBefore("\"signature\"")

    // Use your Ed25519 public key (hardcode in app or fetch from secure config)
    val publicKey = "your_ed25519_public_key_hex"

    return SignatureVerifier.verify(
        data = dataToVerify,
        signatureHex = pack.signature,
        publicKeyHex = publicKey
    )
}
```

**Why**: Prevents attackers from serving malicious packs. The signature proves the pack came from you.

#### E. Implement Atomic Pack Saving (line 140-143)
```kotlin
private fun savePack(packJson: String, region: String) {
    val cacheDir = applicationContext.cacheDir
    val tempFile = File(cacheDir, "sender_pack_${region}_temp.json")
    val finalFile = File(cacheDir, "sender_pack_${region}.json")

    // Write to temp file first
    tempFile.writeText(packJson)

    // Atomic rename (if crash happens, old file still valid)
    if (!tempFile.renameTo(finalFile)) {
        throw IOException("Failed to replace pack file")
    }
}
```

**Why**: If app crashes mid-write, you don't corrupt the existing pack

#### F. Update Repository to Load from Cache
In `SenderPackRepositoryImpl.kt`, modify `loadPack()` to check cache first:
```kotlin
fun loadPack(region: String): PackVerificationResult {
    // Try cache first
    val cachedFile = File(context.cacheDir, "sender_pack_${region}.json")
    if (cachedFile.exists()) {
        val cached = loadPackFromFile(cachedFile)
        if (cached.isValid) return cached
    }

    // Fallback to bundled assets
    return loadPackFromAssets(region)
}
```

**Testing Strategy**:
1. Upload test pack to CDN with newer version
2. Trigger worker manually: `WorkManager.getInstance(context).enqueueUniqueWork(...)`
3. Verify download, signature check, and file save
4. Check logs for "Loaded sender pack for region: IN (version: 2)"

**Reference Files**:
- `SenderPackRepositoryImpl.kt` - See existing `loadPackFromAssets()` for JSON parsing
- `SignatureVerifier.kt` - Ed25519 verification already implemented
- `domain/util/` - Crypto utilities

**Priority**: 🟡 MEDIUM - App works fine with bundled packs, but updates improve accuracy over time

---

### 5. Implement PSL Update Worker

**File**: `PSLUpdateWorker.kt`

**Current Status**: Placeholder that logs "PSL update completed (placeholder - no actual update performed)"

**What It Does**:
Scheduled to run monthly (on Wi-Fi only) to download the latest Public Suffix List from Mozilla. PSL is a list of all public domain suffixes (.com, .co.uk, .github.io) used to extract registered domains from URLs.

**What Needs Implementation**:

#### A. Implement PSL Download (line 101-105)
```kotlin
private suspend fun downloadPSL(): String {
    val url = "https://publicsuffix.org/list/public_suffix_list.dat"
    val request = Request.Builder()
        .url(url)
        .build()

    return withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PSL download failed: ${response.code}")

            val body = response.body?.string() ?: throw IOException("Empty PSL response")

            // PSL file is ~200KB, should complete in <5 seconds on any connection
            if (body.length < 50_000) throw IOException("PSL file suspiciously small")

            body
        }
    }
}
```

**Why**: Gets latest domain suffix rules. Mozilla updates PSL when new TLDs are added or removed.

#### B. Implement PSL Validation (line 115-119)
```kotlin
private fun isValidPSL(pslData: String): Boolean {
    // Check for required sections
    val hasIcannSection = pslData.contains("// ===BEGIN ICANN DOMAINS===")
    val hasPrivateSection = pslData.contains("// ===BEGIN PRIVATE DOMAINS===")

    if (!hasIcannSection || !hasPrivateSection) return false

    // Check minimum line count (PSL has ~10,000+ rules)
    val lineCount = pslData.lines().count { it.isNotBlank() && !it.startsWith("//") }
    if (lineCount < 5000) return false

    // Check for common TLDs as sanity test
    val hasCommonTlds = listOf("com", "org", "net", "co.uk").all { pslData.contains(it) }
    if (!hasCommonTlds) return false

    return true
}
```

**Why**: Detects corrupted downloads or server errors before replacing working PSL

#### C. Implement Atomic PSL Saving (line 129-133)
```kotlin
private fun savePSLToCache(pslData: String) {
    val cacheDir = applicationContext.cacheDir
    val tempFile = File(cacheDir, "public_suffix_list_temp.dat")
    val finalFile = File(cacheDir, "public_suffix_list_cached.dat")

    // Write to temp file
    tempFile.writeText(pslData, Charsets.UTF_8)

    // Atomic rename
    if (!tempFile.renameTo(finalFile)) {
        tempFile.delete()
        throw IOException("Failed to replace PSL file")
    }

    Log.d(TAG, "PSL saved to cache: ${finalFile.absolutePath} (${pslData.length} bytes)")
}
```

**Why**: Prevents corruption if app crashes during write

#### D. Update PSL Parser to Load from Cache
In `PublicSuffixListParser.kt` (or wherever PSL is loaded), add cache fallback:
```kotlin
object PublicSuffixList {
    private var rules: List<String>? = null

    fun load(context: Context) {
        if (rules != null) return // Already loaded

        // Try cache first
        val cachedFile = File(context.cacheDir, "public_suffix_list_cached.dat")
        if (cachedFile.exists() && cachedFile.length() > 50_000) {
            try {
                rules = cachedFile.readLines()
                Log.d(TAG, "Loaded PSL from cache (${rules!!.size} rules)")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load cached PSL, falling back to bundled", e)
            }
        }

        // Fallback to bundled assets
        val assetStream = context.assets.open("public_suffix_list.dat")
        rules = assetStream.bufferedReader().readLines()
        Log.d(TAG, "Loaded PSL from assets (${rules!!.size} rules)")
    }
}
```

#### E. Add Last Update Tracking (lines 138-149)
The file already has placeholder methods for tracking update timestamps. Just wire them up:
```kotlin
// In doWork(), before downloading:
val lastUpdate = getLastUpdateTimestamp()
val daysSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (24 * 60 * 60 * 1000)

if (daysSinceUpdate < 30 && cachedPSLExists()) {
    Log.d(TAG, "PSL is recent (updated $daysSinceUpdate days ago), skipping")
    return Result.success()
}

// After successful save:
setLastUpdateTimestamp(System.currentTimeMillis())
```

**Testing Strategy**:
1. Delete cached PSL: `adb shell rm /data/data/com.kite.phalanx/cache/public_suffix_list_cached.dat`
2. Trigger worker manually
3. Verify download (should see ~200KB file in logs)
4. Check PSL parsing still works: extract domain from `test.co.uk` → `test.co.uk` ✓
5. Verify fallback: corrupt cached file, verify app uses bundled PSL

**Reference Files**:
- `PublicSuffixListParser.kt` - PSL parsing logic
- `ProfileDomainUseCase.kt` - Uses PSL for registered domain extraction
- `assets/public_suffix_list.dat` - Bundled PSL (last updated when?)

**Priority**: 🟢 LOW - PSL changes infrequently (~monthly), bundled version works fine for 6+ months

---

## Missing Unit Tests

Currently **19 components lack tests**. This section prioritizes which tests to write first.

### Test Priority Tier 1: Critical Business Logic

#### 6. Test CheckAllowBlockRulesUseCase

**File**: `domain/usecase/CheckAllowBlockRulesUseCase.kt` (no test file exists)

**Why Test This**:
This use case implements rule precedence for allow/block lists. If buggy, it could:
- Block legitimate messages (false positive)
- Allow malicious messages through (false negative)
- Ignore user's whitelist/blacklist settings

**What to Test**:
```kotlin
class CheckAllowBlockRulesUseCaseTest {

    @Test
    fun `ALLOW rule forces GREEN verdict even with suspicious signals`() {
        // Given: domain "example.com" is whitelisted
        // And: message has AMBER verdict (score 50)
        // When: check rules
        // Then: verdict changed to GREEN
    }

    @Test
    fun `BLOCK rule elevates GREEN to AMBER`() {
        // Given: sender "1234" is blacklisted
        // And: message has GREEN verdict
        // When: check rules
        // Then: verdict elevated to AMBER
    }

    @Test
    fun `ALLOW rule does NOT override CRITICAL signals`() {
        // Given: domain whitelisted
        // And: message has USERINFO_IN_URL (critical signal)
        // When: check rules
        // Then: verdict stays RED (critical signals can't be overridden)
    }

    @Test
    fun `domain rule takes precedence over sender rule`() {
        // Given: domain whitelisted but sender blacklisted
        // When: check rules
        // Then: domain rule wins (ALLOW)
    }

    @Test
    fun `pattern matching works with wildcards`() {
        // Given: rule pattern "*.phishing.com"
        // And: message from "subdomain.phishing.com"
        // When: check rules
        // Then: rule matches
    }
}
```

**How to Write Tests**:
1. Create `app/src/test/java/com/kite/phalanx/domain/usecase/CheckAllowBlockRulesUseCaseTest.kt`
2. Mock `AllowBlockListRepository` using MockK
3. Create test verdicts with various scores and levels
4. Create test rules (ALLOW/BLOCK for domain/sender/pattern)
5. Assert final verdict matches expected level

**Reference Tests**: See `AnalyzeMessageRiskUseCaseTest.kt` for verdict testing patterns

---

#### 7. Test TrashVaultRepositoryImpl

**File**: `data/repository/TrashVaultRepositoryImpl.kt` (no test file exists)

**Why Test This**:
Trash vault manages soft-deleted messages. If buggy:
- Messages permanently deleted instead of trashed (data loss)
- Trash not purged after 30 days (storage bloat)
- Restore puts message in wrong conversation (data corruption)

**What to Test**:
```kotlin
class TrashVaultRepositoryImplTest {

    @Test
    fun `moveToTrash preserves message metadata`() {
        // Given: message with sender, body, timestamp
        // When: move to trash
        // Then: all fields preserved in TrashedMessageEntity
    }

    @Test
    fun `getTrashMessages returns only non-expired messages`() {
        // Given: 2 messages trashed 10 days ago, 1 message trashed 40 days ago
        // When: get trash messages
        // Then: returns only 2 recent messages
    }

    @Test
    fun `purgeExpiredMessages deletes messages older than 30 days`() {
        // Given: messages with various deletion timestamps
        // When: purge with current time
        // Then: only old messages deleted, recent messages remain
    }

    @Test
    fun `restoreMessage removes from trash and returns data`() {
        // Given: trashed message
        // When: restore by ID
        // Then: message removed from trash, data returned for re-insertion
    }

    @Test
    fun `thread grouping works correctly`() {
        // Given: 3 messages deleted together (same threadGroupId)
        // When: query trash
        // Then: messages grouped as single entry
    }
}
```

**How to Write Tests**:
1. Use Robolectric for Android database testing: `@RunWith(RobolectricTestRunner::class)`
2. Create in-memory Room database for testing
3. Mock system clock for time-based tests (use `Clock` interface)
4. Test database migrations if schema changes

**Reference Tests**: See `SenderPackRepositoryImplTest.kt` for repository testing patterns

---

#### 8. Test SmsDetailViewModel

**File**: `ui/SmsDetailViewModel.kt` (no test file exists)

**Why Test This**:
ViewModel orchestrates the entire security analysis pipeline. If buggy:
- Analysis doesn't trigger for new messages
- Verdicts cached incorrectly (stale data shown)
- Crashes when URL expansion times out

**What to Test**:
```kotlin
class SmsDetailViewModelTest {

    @Test
    fun `analyzeMessage runs full pipeline and caches verdict`() {
        // Given: message with URL
        // When: analyzeMessage called
        // Then: ExtractLinks → ExpandURL → ProfileDomain → AnalyzeRisk → CheckReputation
        // And: verdict cached in database
    }

    @Test
    fun `cached verdicts returned immediately without re-analysis`() {
        // Given: message analyzed previously (verdict in DB)
        // When: analyzeMessage called again
        // Then: returns cached verdict without running pipeline
    }

    @Test
    fun `URL expansion failure does not crash pipeline`() {
        // Given: URL expansion times out
        // When: analyze message
        // Then: domain profiling continues with original URL
        // And: verdict still generated
    }

    @Test
    fun `verdictsByMessageId flow emits new verdicts`() {
        // Given: ViewModel initialized
        // When: analyze multiple messages
        // Then: verdictsByMessageId flow emits updates for each
    }

    @Test
    fun `expandedUrlsByUrl cache works correctly`() {
        // Given: URL expanded once
        // When: same URL requested again
        // Then: returns cached expansion, no network call
    }
}
```

**How to Write Tests**:
1. Use `TestDispatcher` for coroutines: `StandardTestDispatcher()`
2. Mock all dependencies with MockK: `mockk<ExtractLinksUseCase>()`
3. Use `turbine` library for testing Flows
4. Set `Dispatchers.Main` to test dispatcher: `Dispatchers.setMain(testDispatcher)`

**Reference Tests**: See Android ViewModel testing guides

---

### Test Priority Tier 2: Data Layer

#### 9-12. Test Data Access Objects (DAOs)

**Missing Tests**:
- `SignalDao` - Stores security signals (USERINFO_IN_URL, RAW_IP_HOST, etc.)
- `LinkPreviewDao` - Stores cached link preview data
- `AllowBlockRuleDao` - Stores allow/block rules
- `TrashedMessageDao` - Stores soft-deleted messages

**Why Test DAOs**:
Database bugs cause data loss or corruption. DAO tests catch:
- Incorrect SQL queries (query returns wrong data)
- Missing indices (queries are slow)
- Constraint violations (crash on insert)
- Migration failures (data lost after app update)

**How to Test DAOs** (generic pattern):
```kotlin
@RunWith(AndroidJUnit4::class)
class SignalDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var signalDao: SignalDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        signalDao = database.signalDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveSignal() = runBlocking {
        val signal = SignalEntity(
            messageId = "msg1",
            code = "USERINFO_IN_URL",
            weight = 100,
            metadata = """{"url": "http://user:pass@evil.com"}"""
        )

        signalDao.insert(signal)

        val signals = signalDao.getByMessage("msg1")
        assertThat(signals).hasSize(1)
        assertThat(signals[0].code).isEqualTo("USERINFO_IN_URL")
    }

    @Test
    fun deleteOrphanedSignals() = runBlocking {
        // Insert signal with no corresponding verdict
        signalDao.insert(SignalEntity(messageId = "orphan", code = "HTTP_SCHEME"))

        val deleted = signalDao.deleteOrphaned()
        assertThat(deleted).isEqualTo(1)
    }
}
```

**Tools Needed**:
- AndroidX Test: `androidx.test:runner`, `androidx.test:core`
- Room Testing: `androidx.room:room-testing`
- Truth assertions: `com.google.truth:truth`

**Priority**: 🟡 MEDIUM - DAOs are thin wrappers over Room, less likely to have bugs

---

### Test Priority Tier 3: Workers

#### 13-17. Test Background Workers

**Missing Tests**:
- `DatabaseCleanupWorker` - Removes expired cache entries
- `TrashVaultPurgeWorker` - Purges old trash
- `SenderPackUpdateWorker` - Updates sender packs
- `PSLUpdateWorker` - Updates PSL
- `WorkerScheduler` - Schedules all workers

**Why Test Workers**:
Workers run in background when app is closed. If buggy:
- Data not cleaned up (storage bloat)
- Updates fail silently (stale data)
- Workers crash and retry infinitely (battery drain)

**How to Test Workers**:
```kotlin
@RunWith(AndroidJUnit4::class)
class DatabaseCleanupWorkerTest {

    @Test
    fun workerCleansExpiredCache() = runBlocking {
        // Given: database with expired and valid cache entries
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = mockk<AppDatabase>()

        // When: worker runs
        val worker = TestListenableWorkerBuilder<DatabaseCleanupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return DatabaseCleanupWorker(appContext, workerParameters, database)
                }
            })
            .build()

        val result = worker.doWork()

        // Then: expired entries deleted
        assertThat(result).isEqualTo(Result.success())
        coVerify { database.cachedExpansionDao().deleteExpired(any()) }
    }
}
```

**Tools Needed**:
- WorkManager Testing: `androidx.work:work-testing`
- MockK: `io.mockk:mockk`

**Priority**: 🟢 LOW - Workers are simple and well-isolated

---

## Code Quality Improvements

These aren't bugs, but they make the code more maintainable.

### 18. Implement Favicon Fetching (Optional Feature)

**File**: `FetchLinkPreviewUseCase.kt` (line 236)

**Current Status**: Extracts favicon URL from HTML but doesn't fetch the image

**Why It's Incomplete**:
```kotlin
// Line 236: TODO: Implement safe favicon fetching in future iteration
private fun extractFavicon(document: Document, baseUrl: String): ByteArray? {
    val faviconUrl = document.selectFirst("link[rel~=icon]")?.attr("abs:href")
    // Found URL but doesn't fetch it
    return null
}
```

The security explanation sheet shows link previews with title but no icon. Favicons help users recognize legitimate sites.

**Why This Is Low Priority**:
- Titles already provide context
- Favicons add UI polish but don't improve security detection
- Fetching icons adds complexity (size limits, caching, timeouts)

**How to Implement** (if you want it):

1. **Add Favicon Fetch Logic**:
```kotlin
private suspend fun fetchFavicon(url: String): ByteArray? {
    // Validate URL
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null

    // Prevent data: URLs (security risk)
    if (url.startsWith("data:")) return null

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    return withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                // Check content type
                val contentType = response.body?.contentType()?.toString() ?: ""
                if (!contentType.startsWith("image/")) return@withContext null

                // Check size (favicons are typically <10KB)
                val bytes = response.body?.bytes() ?: return@withContext null
                if (bytes.size > 10_000) return@withContext null

                bytes
            }
        } catch (e: Exception) {
            Log.d(TAG, "Favicon fetch failed: ${e.message}")
            null // Non-fatal failure
        }
    }
}
```

2. **Update Data Model**:
In `LinkPreview.kt` and `LinkPreviewEntity.kt`, the `faviconBytes` field already exists (you're ready!)

3. **Add Caching**:
Favicons don't change often, cache them with the link preview

4. **Display in UI**:
In `SecurityExplanationSheet`, decode `ByteArray` to `ImageBitmap` and show next to title

**Testing**:
- Valid PNG favicon → fetched and cached ✓
- Oversized icon (>10KB) → rejected ✓
- data: URL → blocked ✓
- Fetch timeout → returns null, doesn't crash ✓

**Priority**: 🟢 LOW - Nice-to-have UI polish, not essential

---

### 19. Add Reply Scroll/Focus (UX Polish)

**File**: `SmsDetailActivity.kt` (line 367)

**Current Status**: Reply button sets `replyingToMessage` state but doesn't scroll UI

**Problem**:
```kotlin
// Line 367: TODO: Scroll to bottom/composer and focus
onClick = {
    replyingToMessage = message
    // User expects:
    // 1. Scroll to bottom of message list
    // 2. Focus keyboard on composer
    // But these don't happen automatically
}
```

**Why It Matters** (minor UX issue):
When user taps "Reply" on an old message at top of thread, they expect:
1. Screen scrolls to composer at bottom
2. Keyboard appears automatically
3. They can start typing immediately

Currently, they have to manually scroll down and tap the text field.

**How to Fix**:
```kotlin
// Add LazyListState reference at top of composable:
val listState = rememberLazyListState()
val focusRequester = remember { FocusRequester() }

// In reply button click:
onClick = {
    replyingToMessage = message

    // Scroll to bottom
    scope.launch {
        listState.animateScrollToItem(messages.size - 1)
    }

    // Focus composer after scroll completes
    scope.launch {
        delay(300) // Wait for scroll animation
        focusRequester.requestFocus()
    }
}

// In composer TextField:
TextField(
    value = messageText,
    onValueChange = { messageText = it },
    modifier = Modifier.focusRequester(focusRequester)
)
```

**Priority**: 🟢 LOW - Works fine, just not as smooth as it could be

---

### 20. Add Proper Logging Framework ✅ **COMPLETED**

**Status**: ✅ **Migration completed successfully**

**What Was Done**:
1. ✅ Added Timber dependency (v5.0.1) to `libs.versions.toml` and `build.gradle.kts`
2. ✅ Created `CrashReportingTree.kt` for production builds (logs WARN/ERROR to Crashlytics)
3. ✅ Initialized Timber in `PhalanxApplication.onCreate()`:
   - Debug builds: `Timber.plant(Timber.DebugTree())` - logs everything to logcat
   - Release builds: `Timber.plant(CrashReportingTree())` - logs only errors to Crashlytics
4. ✅ Migrated all 18 files from `android.util.Log` to Timber
5. ✅ Replaced 132 Log calls with Timber equivalents
6. ✅ Removed 17 TAG constant declarations
7. ✅ Build verified successfully

**Files Modified**:
- `gradle/libs.versions.toml` - Added Timber 5.0.1
- `app/build.gradle.kts` - Added Timber dependency
- `app/src/main/java/com/kite/phalanx/CrashReportingTree.kt` (new)
- `app/src/main/java/com/kite/phalanx/PhalanxApplication.kt` - Timber initialization
- 18 source files migrated from Log to Timber:
  - All worker classes (PSLUpdateWorker, SenderPackUpdateWorker, etc.)
  - All receivers (SmsReceiver, MmsReceiver, SmsSentReceiver, etc.)
  - All repositories (SafeBrowsingRepository, URLhausRepository)
  - All use cases (FetchLinkPreviewUseCase, CheckUrlReputationUseCase)
  - Helper classes (SmsHelper, MessageLoader, MmsHelper, MmsSender, CacheManager)
  - Activities (SpamListActivity)

**Implementation Details**:
- All `Log.d(TAG, "message")` → `Timber.d("message")` (TAG auto-generated from class name)
- All `Log.e(TAG, "message", exception)` → `Timber.e(exception, "message")`
- All `Log.i/w/v` similarly converted
- CrashReportingTree silently ignores DEBUG/INFO logs in production
- CrashReportingTree logs WARN/ERROR to Firebase Crashlytics with custom keys
- Privacy: Debug logs never reach production, preventing PII leakage

**Priority**: ~~🟡 MEDIUM~~ → ✅ **DONE**

---

## Performance & Monitoring

### 21. Verify Performance Budgets

**Required by PRD**:
- Per-message analysis: ≤300ms P50
- Cold start: ≤600ms
- URL expansion timeout: ≤1500ms
- Daily background battery: <1-2%

**Current State**: Not measured

**How to Add Performance Tests**:

#### A. Benchmark Analysis Speed
```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class PerformanceBenchmarkTest {

    @Test
    fun analyzeMessageUnder300ms() = runBlocking {
        val useCase = AnalyzeMessageRiskUseCase(...)
        val message = createTestMessage(url = "http://bit.ly/phishing")

        val times = mutableListOf<Long>()

        repeat(100) {
            val start = System.nanoTime()
            useCase.execute(message)
            val duration = (System.nanoTime() - start) / 1_000_000 // ms
            times.add(duration)
        }

        val p50 = times.sorted()[50] // Median
        assertThat(p50).isLessThan(300L)
    }
}
```

#### B. Monitor Cold Start Time
Use Android Studio Profiler or Macrobenchmark library to measure time from app launch to first frame.

#### C. Monitor Battery Usage
1. Install app on test device
2. Let run for 24 hours with workers scheduled
3. Check Settings → Battery → Phalanx → Background usage
4. Should be <1-2% daily

**Priority**: 🟡 MEDIUM - Important for production quality, but app likely meets budgets

---

### 13. Add Crash Reporting

**Current State**: No crash reporting configured

**Problem**:
If app crashes for users, you won't know:
- What caused the crash
- How often it happens
- Which devices are affected

**Solution**: Add Firebase Crashlytics

```kotlin
// In build.gradle.kts:
plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}

// In code:
try {
    analyzeMessage(message)
} catch (e: Exception) {
    FirebaseCrashlytics.getInstance().recordException(e)
    throw e
}
```

**Privacy Note**: Don't log message content, only error types

**Priority**: 🟡 MEDIUM - Essential for production monitoring

---

## Priority Matrix

### Fix Before Production (Week 1)
1. 🔴 Remove development signature bypass (30 min)
2. ~~🟡 Migrate security settings to Proto DataStore (4-6 hours)~~ ✅ **DONE**
3. ~~🟡 Add crash reporting (2 hours)~~ ✅ **DONE**

### Important for Robustness (Week 2)
4. Test `CheckAllowBlockRulesUseCase` (4 hours)
5. Test `TrashVaultRepositoryImpl` (4 hours)
6. Test `SmsDetailViewModel` (6 hours)
7. ~~Add logging framework (2 hours)~~ ✅ **DONE**

### Nice to Have (Week 3-4)
8. Implement sender pack updates (8-12 hours)
9. Implement PSL updates (4-6 hours)
10. Test all 4 DAOs (6 hours total)
11. Test 5 workers (8 hours total)
12. Verify performance benchmarks (4 hours)

### Optional Polish (Backlog)
13. Implement favicon fetching (4 hours)
14. Add reply scroll/focus (1 hour)
15. Test remaining use cases (8 hours)

---

## Validation Checklist

Before marking "production-ready," verify:

### Security
- [ ] Development signature bypass removed
- [ ] All sender packs have valid Ed25519 signatures
- [x] No sensitive data logged in release builds ✅ (Timber auto-strips debug logs)
- [ ] SSL pinning configured for CDN (if using updates)

### Data Integrity
- [x] Security settings migrated to Proto DataStore ✅
- [x] Migration from SharedPreferences tested ✅
- [ ] Database migrations tested (v1→v2→v3→v4)
- [ ] Trash vault auto-purge working correctly

### Testing
- [ ] All critical use cases tested (CheckAllowBlockRules, Trash, Restore)
- [ ] All repositories tested
- [ ] ViewModel tested
- [ ] Performance benchmarks pass (300ms analysis, 600ms cold start)

### Production Monitoring
- [x] Crash reporting configured ✅
- [x] Logging framework configured ✅ (Timber with CrashReportingTree)
- [ ] Analytics events defined
- [x] Logs don't leak PII ✅ (Debug logs stripped from release builds)
- [ ] Battery usage measured (<1-2% daily)

### Workers (Optional)
- [ ] Sender pack update CDN configured
- [ ] PSL update working
- [ ] Workers tested on real device (24-hour run)

---

## Questions to Answer

1. **Sender Pack Updates**: Do you want to implement CDN updates, or just ship with bundled packs and update via app releases?
   - **If CDN**: Need to set up hosting and implement `SenderPackUpdateWorker`
   - **If app releases**: Can remove worker and mark as "not needed"

2. **PSL Updates**: PSL changes ~monthly. Is it worth the complexity to auto-update?
   - **If yes**: Implement `PSLUpdateWorker`
   - **If no**: Update bundled PSL before each app release

3. **Favicon Fetching**: Do you want icons in link previews?
   - **If yes**: Implement fetch logic (4 hours)
   - **If no**: Remove `faviconBytes` field from data model

4. **Test Coverage Target**: How much coverage do you want?
   - **Minimum (46% → 60%)**: Test critical paths only (2-3 days)
   - **Good (60% → 80%)**: Test most components (1 week)
   - **Excellent (80%+)**: Test everything including DAOs and workers (2 weeks)

---

## Glossary

**PSL (Public Suffix List)**: List of all top-level domain suffixes (.com, .co.uk, etc.). Used to extract "registered domains" (example.com) from subdomains (subdomain.example.com).

**Proto DataStore**: Android's modern preference storage using Protocol Buffers. Type-safe (compiler checks) and coroutine-based.

**Sender Pack**: JSON file containing verified SMS sender IDs for banks, carriers, government. Used to detect impersonation (message claims to be from "Chase" but sender ID is wrong).

**Ed25519**: Public-key cryptography algorithm for digital signatures. Faster and more secure than RSA. Used to verify sender packs haven't been tampered with.

**DAO (Data Access Object)**: Kotlin interface that defines database operations (insert, query, delete). Room generates implementation automatically.

**Worker**: Background task scheduled by WorkManager. Runs even when app is closed (e.g., cleanup, updates).

**LRU Cache (Least Recently Used)**: Memory cache that automatically removes old entries when full. Keeps frequently-used items in fast memory.

**CDN (Content Delivery Network)**: Server network that hosts files close to users. Faster downloads than single server. Examples: Cloudflare, AWS CloudFront.

---

**End of Refinement I**

*For questions or clarifications on any item, refer to the corresponding file and line numbers. Most issues include code examples showing how to fix them.*
