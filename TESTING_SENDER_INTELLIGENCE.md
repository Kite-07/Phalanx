# Testing Sender Intelligence in Android Emulator

This guide explains how to test the Phase 4 Sender Intelligence feature in Phalanx using an Android emulator.

## Prerequisites

1. **Android Emulator running** with Phalanx installed
2. **ADB (Android Debug Bridge)** in your PATH
3. Phalanx set as **Default SMS app**

## Quick Start - Automated Test Script

Run the provided test script:

```batch
test_sender_intelligence.bat
```

This sends 8 test messages:
- 4 legitimate messages (HDFCBK, AX-AIRTEL, PAYTM, IRCTC)
- 4 fake messages (should trigger SENDER_MISMATCH)

---

## Method 1: ADB Command Line (Recommended)

### Basic Syntax

```bash
adb emu sms send <sender_id> "<message_text>"
```

### Example Test Cases

#### ✅ PASS - Legitimate HDFC Bank (should be GREEN)
```bash
adb emu sms send HDFCBK "Your HDFC account credited with Rs.5000. Balance: Rs.25000."
```

#### ❌ FAIL - Fake HDFC Bank (should trigger SENDER_MISMATCH)
```bash
adb emu sms send +919876543210 "Your HDFC Bank account will be locked. Verify: https://hdfc-verify.tk"
```

#### ✅ PASS - Legitimate Airtel (should be GREEN)
```bash
adb emu sms send AX-AIRTEL "Your Airtel bill of Rs.599 is due on 15th Jan."
```

#### ❌ FAIL - Fake Airtel (should trigger SENDER_MISMATCH)
```bash
adb emu sms send AIRTEL99 "You've won a free recharge! Click: http://airtel-free.xyz"
```

#### ✅ PASS - Legitimate Paytm (should be GREEN)
```bash
adb emu sms send PAYTM "Rs.1000 added to your Paytm wallet. TXN: 123456"
```

#### ❌ FAIL - Fake Paytm (should trigger SENDER_MISMATCH + other signals)
```bash
adb emu sms send PayTM-KYC "Your Paytm KYC pending. Complete now: http://paytm-verify.tk/login"
```

#### ✅ PASS - Legitimate SBI (should be GREEN)
```bash
adb emu sms send SBIPSG "Your SBI account debited by Rs.2000. Balance: Rs.15000."
```

#### ❌ FAIL - Fake Banking (should trigger SENDER_MISMATCH + USERINFO_IN_URL = RED)
```bash
adb emu sms send BankAlert "URGENT: SBI account locked. Login: http://user:pass@192.168.1.1/verify"
```

---

## Method 2: Emulator Console (Alternative)

### Step 1: Find emulator console port
```bash
adb devices
```
Output example: `emulator-5554`

### Step 2: Connect to telnet console
```bash
telnet localhost 5554
```

### Step 3: Send SMS
```
sms send HDFCBK "Test message from HDFC Bank"
```

### Step 4: Exit console
```
quit
```

---

## Method 3: Android Studio Device File Explorer (Manual)

1. Open **Android Studio** → **View** → **Tool Windows** → **Device File Explorer**
2. Navigate to `/data/data/com.android.providers.telephony/databases/`
3. Manually insert SMS via SQL (advanced, not recommended)

---

## Understanding Sender IDs from IN Pack

### Legitimate Sender IDs (should PASS - GREEN verdict):

| Brand | Sender ID Pattern | Example |
|-------|------------------|---------|
| Airtel | `^(AX-)?AIRTEL$` | AX-AIRTEL, AIRTEL |
| Jio | `^JIONET$` | JIONET |
| Vodafone Idea | `^(VM-)?VODAID$` | VM-VODAID, VODAID |
| HDFC Bank | `^HDFCBK$` | HDFCBK |
| ICICI Bank | `^ICICIB$` | ICICIB |
| SBI | `^SBIPSG$` | SBIPSG |
| Paytm | `^PAYTM$` | PAYTM |
| PhonePe | `^PHNEPE$` | PHNEPE |
| Amazon | `^AMAZON$` | AMAZON |
| Flipkart | `^FLIPKT$` | FLIPKT |
| IRCTC | `^IRCTC$` | IRCTC |
| EPFO | `^EPFOHO$` | EPFOHO |

### Suspicious Sender IDs (should FAIL - trigger SENDER_MISMATCH):

- Phone numbers: `+919876543210`, `1234567890`
- Misspellings: `HDFCBANK`, `AIRTE1`, `PAYTM123`
- Generic: `BANK`, `SMS-INFO`, `ALERT`
- Fake patterns: `AX-HDFC`, `VM-PAYTM`

---

## What to Look For in Testing

### 1. Message Analysis Pipeline

Check logcat for analysis logs:
```bash
adb logcat -s PhalanxApp:I SecuritySettings:I AnalyzeMessageRisk:V
```

Look for:
```
I/PhalanxApp: Loaded sender pack for region: IN (version: 20250109)
V/AnalyzeMessageRisk: Detected SENDER_MISMATCH signal for message...
```

### 2. UI Indicators

**GREEN Verdict (Legitimate):**
- ✅ No security chip shown (or green chip)
- Message from known sender ID matches brand mentioned

**AMBER/RED Verdict (Suspicious):**
- ⚠️ Orange/Red security chip shown under message
- Tap chip → see "Suspicious Sender for [Brand]" reason
- Details: "This message claims to be from [Brand] (bank), but the sender ID (+1234567890) doesn't match their verified patterns."

### 3. Notification Behavior

**Legitimate messages:**
- Normal SMS notification
- No security threat alert

**Suspicious messages:**
- Security Threats notification channel
- High priority with red LED
- Actions: Block Sender, Delete Message, Copy URL

---

## Advanced Testing Scenarios

### Scenario 1: Brand Impersonation + Sender Mismatch
```bash
adb emu sms send FAKE-BANK "Your HDFC account locked. Verify: http://hdfc-verify.tk"
```
Expected signals:
- `SENDER_MISMATCH` (weight: 70 for BANK type)
- `HIGH_RISK_TLD` (weight: 30 for .tk)
- Verdict: **RED** (score: 100)

### Scenario 2: Legitimate Sender + Safe Content
```bash
adb emu sms send HDFCBK "Your account credited with Rs.5000"
```
Expected:
- No signals detected
- Verdict: **GREEN** (score: 0)

### Scenario 3: Multiple Red Flags
```bash
adb emu sms send +911234567890 "URGENT: Your HDFC Bank account suspended! Login now: http://user:admin@192.168.1.1/hdfc/verify.php"
```
Expected signals:
- `SENDER_MISMATCH` (weight: 70)
- `USERINFO_IN_URL` (weight: 100, CRITICAL)
- `RAW_IP_HOST` (weight: 40)
- `HTTP_SCHEME` (weight: 25)
- Verdict: **RED** (score: 235, CRITICAL)

---

## Debugging Tips

### Check Sender Pack Loading
```bash
adb logcat -s PhalanxApp:I | grep "sender pack"
```

Expected output:
```
I/PhalanxApp: Loaded sender pack for region: IN (version: 20250109)
```

### Verify Sender Pattern Matching
```bash
adb logcat -s CheckSenderMismatch:V
```

### View All Security Signals
```bash
adb logcat -s AnalyzeMessageRisk:V
```

### Check Database State
```bash
adb shell
run-as com.kite.phalanx
cd databases
sqlite3 phalanx_database
SELECT * FROM verdicts;
.quit
```

---

## Troubleshooting

### Issue: No SMS received in emulator
**Solution:**
1. Check emulator has SMS capability
2. Verify ADB connection: `adb devices`
3. Try `adb kill-server` then `adb start-server`

### Issue: Sender pack not loading
**Solution:**
1. Check assets folder has `sender_packs/IN.json`
2. Verify JSON is valid (no syntax errors)
3. Check signature (currently placeholder, should validate)
4. Review logcat for load errors

### Issue: SENDER_MISMATCH not triggering
**Solution:**
1. Ensure message mentions a brand keyword (e.g., "HDFC", "Airtel")
2. Verify sender ID doesn't match pattern in IN.json
3. Check `CheckSenderMismatchUseCase` is injected properly
4. Review logcat for usecase execution

### Issue: All messages show GREEN
**Solution:**
1. Check sensitivity level (Settings → Security Settings)
2. Verify allow/block rules aren't forcing GREEN
3. Ensure sender pack region matches (IN = India)

---

## Performance Testing

Send bulk messages to test performance budget (≤300ms P50):

```bash
for /L %i in (1,1,20) do (
    adb emu sms send HDFCBK "Test message %i"
    timeout /t 1 /nobreak >nul
)
```

Monitor timing in logcat:
```bash
adb logcat -s AnalyzeMessageRisk:D | grep "Analysis completed"
```

Expected: `Analysis completed in XXms` where XX < 300ms

---

## Next Steps After Testing

1. ✅ Verify all test cases pass/fail as expected
2. ✅ Check notification behavior for RED/AMBER verdicts
3. ✅ Test region switching in Security Settings
4. ✅ Verify sender pack reloads correctly
5. ✅ Test with different sensitivity levels (Low/Medium/High)
6. ⚠️ Create unit tests to automate these scenarios

---

## Sample Test Session

```batch
# Start fresh
adb shell pm clear com.kite.phalanx

# Launch app and set as default SMS
adb shell am start -n com.kite.phalanx/.SmsListActivity

# Wait for sender pack to load (check logcat)
adb logcat -s PhalanxApp:I | grep "sender pack"

# Send test messages
adb emu sms send HDFCBK "Legitimate HDFC message"
adb emu sms send +919999999999 "Fake HDFC Bank alert: verify account now!"

# Check results in app
# Expected: First message GREEN, second message RED with SENDER_MISMATCH
```

---

**Happy Testing!** 🧪
