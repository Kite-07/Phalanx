# Trust Domain Refactor - Changelog

## Summary of Changes

Three key improvements implemented:

1. **Documentation Update:** Added note that only India (IN) sender pack is implemented
2. **Removed Trusted Domains:** Merged legacy TrustedDomainsPreferences with modern Allow/Block List system
3. **Trust Button Visibility:** "Trust This Domain" now shows for RED verdicts too

---

## 1. Documentation Updates

### Files Modified:
- `Development-Status.md`

### Changes:
Added regional limitation notes to Phase 4 documentation:

```markdown
📝 LIMITATION: Only India (IN) pack implemented. Additional regional packs
(US, GB, AU, CA, etc.) need to be created with verified sender patterns.

⚠️ TODO: Create sender packs for additional regions
⚠️ TODO: Generate production Ed25519 signatures (remove development bypass)
```

**Why:** Users need to know that sender intelligence only works for India region currently. More packs are needed for global coverage.

---

## 2. Removed Trusted Domains, Merged with Allow/Block List

### Problem:
Two separate whitelisting systems existed:
- **Legacy:** `TrustedDomainsPreferences` (DataStore-based, Phase 2)
- **Modern:** `AllowBlockListRepository` (Room-based, Phase 3)

This caused confusion and duplication.

### Solution:
Merged "Trust This Domain" functionality into the modern Allow/Block List system.

### Files Modified:

#### `SmsDetailActivity.kt`
**Before:**
```kotlin
onWhitelist = if (verdict.level != VerdictLevel.RED && registeredDomain.isNotBlank()) {
    {
        scope.launch {
            // OLD: Used legacy TrustedDomainsPreferences
            TrustedDomainsPreferences.trustDomain(context, registeredDomain)
            viewModel.trustDomainAndReanalyze(registeredDomain)
        }
    }
} else null
```

**After:**
```kotlin
onWhitelist = if (registeredDomain.isNotBlank()) {
    {
        scope.launch {
            // NEW: Use modern Allow/Block List system
            allowBlockListRepository.addRule(
                type = RuleType.DOMAIN,
                value = registeredDomain,
                action = RuleAction.ALLOW,
                priority = 80,
                notes = "Trusted by user via Security Sheet"
            )
            viewModel.trustDomainAndReanalyze(registeredDomain)
        }
    }
} else null
```

**Key Changes:**
1. ✅ Removed `verdict.level != VerdictLevel.RED` check (now works for all verdicts)
2. ✅ Added injection: `@Inject lateinit var allowBlockListRepository: AllowBlockListRepository`
3. ✅ Replaced `TrustedDomainsPreferences.trustDomain()` with `allowBlockListRepository.addRule()`
4. ✅ Updated toast message: "Domain added to whitelist" → "Domain added to allow list"

#### `SmsDetailViewModel.kt`
**Before:**
```kotlin
suspend fun trustDomainAndReanalyze(domain: String) {
    // Found messages with domain
    messagesToUpdate.forEach { messageId ->
        // Manually forced GREEN verdict
        val greenVerdict = Verdict(
            messageId = messageId.toString(),
            level = VerdictLevel.GREEN,
            score = 0,
            reasons = emptyList()
        )
        _verdictCache.value = _verdictCache.value + (messageId to greenVerdict)
        database.verdictDao().insert(...)
    }
}
```

**After:**
```kotlin
suspend fun trustDomainAndReanalyze(domain: String) {
    // Find messages with domain
    messagesToUpdate.forEach { messageId ->
        val message = messages.value.find { it.id == messageId }
        if (message != null) {
            // Re-analyze message properly
            // ALLOW rule will automatically force GREEN verdict
            analyzeMessage(message)
        }
    }
}
```

**Key Changes:**
1. ✅ No longer manually forces GREEN verdicts
2. ✅ Properly re-analyzes messages using `analyzeMessage()`
3. ✅ ALLOW rule in AllowBlockListRepository automatically forces GREEN (via `CheckAllowBlockRulesUseCase`)
4. ✅ Updated documentation explaining integration with Allow/Block List system

### How It Works Now:

```
User clicks "Trust This Domain"
    ↓
SmsDetailActivity.onWhitelist callback
    ↓
allowBlockListRepository.addRule(DOMAIN, ALLOW, priority=80)
    ↓
viewModel.trustDomainAndReanalyze(domain)
    ↓
Re-analyze each message with that domain
    ↓
AnalyzeMessageRiskUseCase.execute()
    ↓
checkAllowBlockRulesUseCase.execute() → Returns ALLOW
    ↓
Force GREEN verdict (even if critical signals present*)
    ↓
Update verdict cache + database
    ↓
UI updates to show GREEN chip
```

*Note: Critical signals like `USERINFO_IN_URL` will still show RED even with ALLOW rule (per Phase 3 spec).

---

## 3. Show Trust Button for RED Verdicts

### Problem:
"Trust This Domain" button was hidden for RED verdicts due to condition:
```kotlin
if (onWhitelist != null && verdict.level != VerdictLevel.RED)
```

This prevented users from trusting domains they know are safe, even if flagged as dangerous.

### Solution:
Removed the RED verdict check to allow users to override any verdict level.

### File Modified:
`SecurityComponents.kt`

**Before:**
```kotlin
// Whitelist button (only for AMBER, not RED)
if (onWhitelist != null && verdict.level != VerdictLevel.RED) {
    TextButton(
        onClick = { onWhitelist(); onDismiss() }
    ) {
        Icon(Icons.Default.CheckCircle)
        Text("Trust This Domain")
    }
}
```

**After:**
```kotlin
// Trust This Domain button (show for AMBER and RED)
// Users should be able to override even dangerous domains if they trust the source
if (onWhitelist != null) {
    TextButton(
        onClick = { onWhitelist(); onDismiss() }
    ) {
        Icon(Icons.Default.CheckCircle)
        Text("Trust This Domain")
    }
}
```

**Why This Change:**
- Users may receive legitimate messages from known sources that trigger false positives
- Example: Internal company tools might use non-standard ports or HTTP (flagged as RED)
- User should have final authority to trust domains they know are safe
- ALLOW rules still respect CRITICAL signals (USERINFO_IN_URL remains RED)

---

## Migration Path

### Existing Trusted Domains
The `MigrateTrustedDomainsUseCase` (already implemented in Phase 3) automatically migrates old trusted domains:

1. Runs on first app launch after upgrade
2. Reads domains from `TrustedDomainsPreferences` (DataStore)
3. Creates ALLOW rules in `AllowBlockListRepository` (Room)
4. Sets priority=90 for migrated domains
5. Marks migration complete to prevent re-running

### Legacy Code Status
`TrustedDomainsPreferences.kt` still exists but is **no longer used** for new trust operations:
- ✅ Migration code uses it (read-only)
- ❌ New "Trust This Domain" actions do NOT use it
- 📝 Can be deprecated/removed in future after migration period

---

## Benefits

### For Users:
1. ✅ Consistent whitelisting - one system for all trust operations
2. ✅ More control - can override RED verdicts if needed
3. ✅ Better UI - "Trust This Domain" button always available when relevant
4. ✅ Persistent - Allow/Block rules stored in Room database (more reliable)

### For Developers:
1. ✅ Simplified codebase - one system instead of two
2. ✅ Better architecture - uses existing Allow/Block infrastructure
3. ✅ Room database - better query performance, indexing, migrations
4. ✅ Proper re-analysis - leverages existing AnalyzeMessageRiskUseCase logic

---

## Testing

### Test Case 1: Trust a RED Domain
```bash
# Send malicious-looking message
adb emu sms send +919999999999 "URGENT: Your account locked! Login: http://192.168.1.1/verify"

# Expected behavior:
# 1. Message shows RED chip (RAW_IP_HOST + HTTP_SCHEME)
# 2. Tap chip → Security sheet opens
# 3. "Trust This Domain" button IS visible (✅ NEW)
# 4. Click button → Domain added to allow list
# 5. Message re-analyzed → Shows GREEN chip
```

### Test Case 2: Trust an AMBER Domain
```bash
# Send slightly suspicious message
adb emu sms send +919999999999 "Check this out: https://example.tk/offer"

# Expected behavior:
# 1. Message shows AMBER chip (HIGH_RISK_TLD for .tk)
# 2. Tap chip → Security sheet opens
# 3. "Trust This Domain" button visible
# 4. Click button → Domain added to allow list
# 5. Message re-analyzed → Shows GREEN chip
```

### Test Case 3: Verify Allow List Integration
```bash
# Check database
adb shell run-as com.kite.phalanx
cd databases
sqlite3 phalanx_database
SELECT * FROM allow_block_rules WHERE action = 'ALLOW';

# Should show:
# | id | type | value | action | priority | notes |
# | 1 | DOMAIN | 192.168.1.1 | ALLOW | 80 | Trusted by user via Security Sheet |
```

### Test Case 4: Verify Migration
```bash
# If user had old trusted domains, they should be migrated
SELECT * FROM allow_block_rules WHERE notes LIKE '%Migrated%';

# Should show old domains with priority=90
```

---

## Files Changed Summary

| File | Changes | Impact |
|------|---------|--------|
| `Development-Status.md` | Added regional limitation notes | Documentation |
| `SmsDetailActivity.kt` | Inject AllowBlockListRepository, use addRule() instead of TrustedDomainsPreferences | High |
| `SmsDetailViewModel.kt` | Re-analyze properly using analyzeMessage() | Medium |
| `SecurityComponents.kt` | Remove RED verdict check for Trust button | Low |

---

## Breaking Changes

### For Users:
- ⚠️ None - migration handles old trusted domains automatically

### For Developers:
- ⚠️ `TrustedDomainsPreferences` should no longer be used for new features
- ⚠️ Use `AllowBlockListRepository` for all trust/whitelist operations going forward

---

## Future Work

1. **Remove TrustedDomainsPreferences** after migration period (6 months?)
2. **UI for managing Allow/Block rules** - already exists (AllowBlockListActivity)
3. **Export/Import allow lists** for backup/restore
4. **Sync allow lists** across devices (optional, requires backend)

---

## Summary

✅ **Trust This Domain** now uses modern Allow/Block List system
✅ Works for **all verdict levels** (GREEN, AMBER, RED)
✅ **Proper re-analysis** instead of forcing verdicts
✅ **Backward compatible** via automatic migration
✅ **Documentation updated** with regional limitations

The whitelisting system is now unified, consistent, and more powerful! 🎉
