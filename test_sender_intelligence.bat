@echo off
REM Test script for Phalanx Sender Intelligence
REM Usage: Run this script to send test SMS messages to the emulator

echo ========================================
echo Phalanx Sender Intelligence Test Suite
echo ========================================
echo.

REM Test 1: Legitimate HDFC Bank message
echo Test 1: Sending legitimate HDFC Bank SMS...
adb emu sms send HDFCBK "Your HDFC Bank account has been credited with Rs.5000. Available balance: Rs.25000. For details visit https://bit.ly/hdfccheck"
timeout /t 3 /nobreak >nul

REM Test 2: FAKE HDFC Bank message (should trigger SENDER_MISMATCH)
echo Test 2: Sending FAKE HDFC Bank SMS (should trigger SENDER_MISMATCH)...
adb emu sms send +919876543210 "URGENT: Your HDFC Bank account will be locked. Verify now: https://hdfc-verify.tk/login"
timeout /t 3 /nobreak >nul

REM Test 3: Legitimate Airtel message
echo Test 3: Sending legitimate Airtel SMS...
adb emu sms send AX-AIRTEL "Your Airtel bill of Rs.599 is due on 15th Jan. Pay now to avoid service disruption."
timeout /t 3 /nobreak >nul

REM Test 4: FAKE Airtel message (should trigger SENDER_MISMATCH)
echo Test 4: Sending FAKE Airtel SMS (should trigger SENDER_MISMATCH)...
adb emu sms send AIRTEL99 "Congratulations! You've won a free Airtel recharge. Claim here: http://airtel-offer.xyz/claim"
timeout /t 3 /nobreak >nul

REM Test 5: Legitimate Paytm message
echo Test 5: Sending legitimate Paytm SMS...
adb emu sms send PAYTM "Rs.1000 added to your Paytm wallet. Transaction ID: TXN123456789"
timeout /t 3 /nobreak >nul

REM Test 6: FAKE Paytm message (should trigger SENDER_MISMATCH)
echo Test 6: Sending FAKE Paytm SMS (should trigger SENDER_MISMATCH)...
adb emu sms send PayTM123 "Your Paytm KYC is pending. Complete now to avoid account suspension: http://paytm-kyc.tk/verify"
timeout /t 3 /nobreak >nul

REM Test 7: Legitimate Government (IRCTC) message
echo Test 7: Sending legitimate IRCTC SMS...
adb emu sms send IRCTC "Your train ticket from Delhi to Mumbai is confirmed. PNR: 1234567890. Journey date: 20th Jan."
timeout /t 3 /nobreak >nul

REM Test 8: FAKE Government message (should trigger SENDER_MISMATCH)
echo Test 8: Sending FAKE IRCTC SMS (should trigger SENDER_MISMATCH)...
adb emu sms send IRCTC-INFO "Your IRCTC ticket is cancelled. Get refund here: http://irctc-refund.ml/claim"
timeout /t 3 /nobreak >nul

echo.
echo ========================================
echo Test suite complete!
echo ========================================
echo.
echo Expected Results:
echo - Tests 1, 3, 5, 7: Should show GREEN verdicts (legitimate senders)
echo - Tests 2, 4, 6, 8: Should show RED/AMBER verdicts with SENDER_MISMATCH signal
echo.
echo Check the Phalanx app to verify the results.
pause
