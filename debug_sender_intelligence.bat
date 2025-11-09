@echo off
echo ========================================
echo Phalanx Sender Intelligence Debugger
echo ========================================
echo.
echo This script will:
echo 1. Clear logcat
echo 2. Send a test SMS
echo 3. Show relevant logs
echo.
pause

echo Clearing logcat...
adb logcat -c

echo.
echo Sending test SMS: Fake HDFC message from +919876543210
adb emu sms send +919876543210 "URGENT: Your HDFC Bank account will be locked. Verify now: https://hdfc-verify.tk/login"

echo.
echo Waiting 3 seconds for processing...
timeout /t 3 /nobreak >nul

echo.
echo ========================================
echo DIAGNOSTIC LOGS:
echo ========================================
echo.

echo --- Sender Pack Loading ---
adb logcat -d -s PhalanxApp:I | findstr /C:"sender pack"

echo.
echo --- Sender Mismatch Detection ---
adb logcat -d -s CheckSenderMismatch:D CheckSenderMismatch:I CheckSenderMismatch:W CheckSenderMismatch:E

echo.
echo --- Risk Analysis ---
adb logcat -d -s AnalyzeMessageRisk:D AnalyzeMessageRisk:I AnalyzeMessageRisk:W | findstr /V "Checking brand"

echo.
echo ========================================
echo.
echo If you see "No sender pack loaded!" above, the pack isn't loading.
echo If you see "Claimed brands found: 0", the brand detection is failing.
echo If you see "SENDER_MISMATCH detected!", the feature is working!
echo.
echo Press any key to show FULL detailed logs...
pause >nul

echo.
echo ========================================
echo FULL VERBOSE LOGS:
echo ========================================
adb logcat -d -s CheckSenderMismatch:V CheckSenderMismatch:D CheckSenderMismatch:I CheckSenderMismatch:W CheckSenderMismatch:E

echo.
pause
